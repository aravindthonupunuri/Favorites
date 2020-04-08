package com.tgt.favorites.util

import com.tgt.lists.msgbus.event.EventHeaders
import com.tgt.lists.msgbus.event.EventProcessingLifecycleListener
import io.micronaut.context.annotation.Value
import io.micronaut.test.support.TestPropertyProvider
import io.opentracing.Span
import io.opentracing.Tracer
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.KafkaAdminClient
import org.apache.kafka.common.ConsumerGroupState
import org.apache.kafka.common.errors.CoordinatorNotAvailableException
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared

import javax.inject.Inject
import java.util.concurrent.ConcurrentLinkedQueue

class BaseKafkaFunctionalTest extends BasePersistenceFunctionalTest implements TestPropertyProvider {

    static Logger LOG = LoggerFactory.getLogger(BaseKafkaFunctionalTest)

    @Shared
    @Inject
    AdminClient adminClient

    @Value("\${msgbus.kafka.consumer-group}")
    String msgbusConsumerGroup

    @Value("\${msgbus.kafka.dlq-consumer-group}")
    String dlqConsumerGroup

    private static String MSGBUS_TOPIC = "lists-msg-bus"
    private static String DLQ_TOPIC = "lists-dlq"

    private static long kafkaCheckRetryMillis = 200
    private static long maxkafkaCheckCount = 300

    @Override
    Map<String, String> getProperties() {
        def map = super.getProperties()
        String kafkaBootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS")

        if (kafkaBootstrapServers == null) {
            // use local embedded kafka
            LOG.info("using embedded kafka")
            map.put("kafka.bootstrap.servers", 'localhost:30001')
            map.put("kafka.embedded.enabled", "true")
            map.put("kafka.embedded.topics", "$MSGBUS_TOPIC,$DLQ_TOPIC")
            // log cleaner thread uses high memory by default (134217728 i.e. 128MB)
            // which causes Heap OutOfMemory issues. Setting to somethig smaller 20MB.
            map.put("kafka.embedded.properties.log.cleaner.dedupe.buffer.size", 20000000)
        }
        else {
            // use drone's kafka service
            LOG.info("using drone kafka service $kafkaBootstrapServers")
            map.put("kafka.bootstrap.servers", kafkaBootstrapServers)
            map.put("kafka.embedded.enabled", "false")
        }
        return map
    }

    void waitForKafkaReadiness() {

        boolean kafkaReady = false

        LOG.info("Kafka readiness check")
        KafkaAdminClient kafkaAdminClient = (KafkaAdminClient)adminClient

        int kafkaCheckout = 0
        while (!kafkaReady && kafkaCheckout < maxkafkaCheckCount) {

            LOG.info("Checking Kafka lists-msgbus [$kafkaCheckout]")
            kafkaCheckout++
            boolean msgbusNotReady = true
            try {
                def msgbusConsumerGroupDescriptionMap = kafkaAdminClient.describeConsumerGroups(Collections.singletonList(msgbusConsumerGroup)).all().get()
                msgbusNotReady = msgbusConsumerGroupDescriptionMap.values().find {
                    it.state() != ConsumerGroupState.STABLE
                }
            }
            catch (CoordinatorNotAvailableException ex) {
                LOG.error("Kafka coordinator for lists-msgbus not available", ex)
            }

            if (msgbusNotReady) {
                LOG.info("Kafka msgbus not ready yet, retry after ${kafkaCheckRetryMillis}ms")
                sleep(kafkaCheckRetryMillis)
                continue
            }

            LOG.info("Checking Kafka lists-dlq")
            boolean dlqNotReady = true
            try {
                def dlqConsumerGroupDescriptionMap = kafkaAdminClient.describeConsumerGroups(Collections.singletonList(dlqConsumerGroup)).all().get()
                dlqNotReady = dlqConsumerGroupDescriptionMap.values().find {
                    it.state() != ConsumerGroupState.STABLE
                }
            }
            catch (CoordinatorNotAvailableException ex) {
                LOG.error("Kafka coordinator for lists-dlq not available", ex)
            }

            kafkaReady = !msgbusNotReady && !dlqNotReady

            if (!kafkaReady) {
                LOG.info("Kafka lists-dlq is not ready yet...will retry after ${kafkaCheckRetryMillis}ms")
                sleep(kafkaCheckRetryMillis)
            }
        }
        LOG.info("Kafka is ready")
    }

    static class TestEventListener implements EventProcessingLifecycleListener {
        private PreDispatchLambda preDispatchLambda = null
        PostCompletionLambda postCompletionLambda = null
        Tracer tracer = null

        class Result {
            public String topic
            public boolean success
            public EventHeaders eventHeaders
            public Object data
            public Span activeSpan
            public boolean preDispatch
            public boolean isPoisonEvent

            Result(String topic, boolean success, EventHeaders eventHeaders, Object result, Span activeSpan, boolean preDispatch, boolean isPoisonEvent) {
                this.topic = topic
                this.success = success
                this.eventHeaders = eventHeaders
                this.data = result
                this.activeSpan = activeSpan
                this.preDispatch = preDispatch
                this.isPoisonEvent = isPoisonEvent
            }
        }

        class ConsumerStatus {
            String consumerName
            boolean paused

            ConsumerStatus(String consumerName, boolean paused) {
                this.consumerName = consumerName
                this.paused = paused
            }
        }

        private ConcurrentLinkedQueue<Result> consumerEvents = new ConcurrentLinkedQueue<>()
        private ConcurrentLinkedQueue<Result> producerEvents = new ConcurrentLinkedQueue<>()
        private ConcurrentLinkedQueue<ConsumerStatus> consumerStatusEvents = new ConcurrentLinkedQueue<>()

        void reset() {
            consumerEvents.clear()
            producerEvents.clear()
            consumerStatusEvents.clear()
            preDispatchLambda = null
            postCompletionLambda = null
        }

        void verifyEvents(Closure closure) {
            try {
                closure(consumerEvents, producerEvents, consumerStatusEvents)
            }
            catch(Throwable t) {
                int idx = 1
                String events = ""
                consumerEvents.forEach {
                    EventHeaders headers = it.eventHeaders
                    events += "\nevent[${idx++}]: ${headers} [ success: $it.success, preDispatch: $it.preDispatch], activeSpan: ${it.activeSpan != null}"
                }
                LOG.error("Test ConsumerEvents: $events")

                events = ""
                producerEvents.eventHeaders.forEach {
                    events += "\nevent[${idx++}]: ${it}"
                }
                LOG.error("Test ProducerEvents: $events")
                throw t
            }
        }

        @Override
        boolean onPreDispatchConsumerEvent(@NotNull String topic, @NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
            LOG.info("Received onPreDispatch(topic: $topic): "+eventHeaders)
            consumerEvents.add(new Result(topic, false, eventHeaders, data, tracer.activeSpan(), true, isPoisonEvent))
            if (preDispatchLambda)
                return preDispatchLambda.onPreDispatchConsumerEvent(topic, eventHeaders, data, isPoisonEvent)
            return true
        }

        @Override
        void onPostCompletionConsumerEvent(@NotNull String topic, boolean success, @NotNull EventHeaders eventHeaders, @Nullable Object result, boolean isPoisonEvent, @Nullable Throwable error) {
            LOG.info("Received onPostCompletion(topic: $topic): "+eventHeaders)
            consumerEvents.add(new Result(topic, success, eventHeaders, result, tracer.activeSpan(), false, isPoisonEvent))
            if (postCompletionLambda)
                postCompletionLambda.onPostCompletionConsumerEvent(topic, success, eventHeaders, result, isPoisonEvent, error)
        }

        @Override
        void onConsumerDeadEventPreCompletion(@NotNull String topic, @NotNull EventHeaders eventHeaders, @NotNull byte[] data) {
            LOG.info("Received onConsumerDeadEventPreCompletion(topic: $topic): "+eventHeaders)
            consumerEvents.add(new Result(topic, false, eventHeaders, null, tracer.activeSpan(), true, false))
        }

        @Override
        void onConsumerDeadEventPostCompletion(@NotNull String topic, boolean success, @NotNull EventHeaders eventHeaders, @Nullable Throwable error) {
            LOG.info("Received onConsumerDeadEventPostCompletion(topic: $topic): "+eventHeaders)
            consumerEvents.add(new Result(topic, success, eventHeaders, null, tracer.activeSpan(), false, false))
        }

        @Override
        void onSuccessfulProducerSendEvent(@NotNull String topic, @NotNull EventHeaders eventHeaders, @NotNull Object message, @NotNull Object partitionKey) {
            LOG.info("Received onSuccessfulProducerSendEvent(topic: $topic): "+eventHeaders)
            producerEvents.add(new Result(topic, true, eventHeaders, message, tracer.activeSpan(), false, false))
        }

        @Override
        void onFailedProducerSendEvent(@NotNull String topic, @NotNull EventHeaders eventHeaders, @NotNull Object message, @NotNull Object partitionKey) {
            LOG.info("Received onFailedProducerSendEvent(topic: $topic): "+eventHeaders)
            producerEvents.add(new Result(topic, false, eventHeaders, message, tracer.activeSpan(), false, false))
        }

        @Override
        void onConsumerPause(@NotNull String consumerName) {
            LOG.info("Received onConsumerPause for consumer "+consumerName)
            consumerStatusEvents.add(new ConsumerStatus(consumerName, true))
        }

        @Override
        void onConsumerResume(@NotNull String consumerName) {
            LOG.info("Received onConsumerResume for consumer "+consumerName)
            consumerStatusEvents.add(new ConsumerStatus(consumerName, false))
        }
    }
}
