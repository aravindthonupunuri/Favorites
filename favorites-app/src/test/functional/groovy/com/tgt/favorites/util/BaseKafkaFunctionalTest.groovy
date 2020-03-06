package com.tgt.favorites.util


import com.tgt.lists.msgbus.event.EventHeaders
import com.tgt.lists.msgbus.event.EventProcessingLifecycleListener
import io.micronaut.context.annotation.Value
import io.micronaut.test.support.TestPropertyProvider
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

        class Result {
            public boolean success
            public boolean poisonEvent
            public EventHeaders eventHeaders
            public Object data

            Result(boolean success, boolean poisonEvent, EventHeaders eventHeaders, Object result) {
                this.success = success
                this.poisonEvent = poisonEvent
                this.eventHeaders = eventHeaders
                this.data = result
            }
        }

        private ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<>()

        void verifyEvents(Closure closure) {
            try {
                closure(results)
            }
            catch(Throwable t) {
                int idx = 1
                String events = ""
                results.eventHeaders.forEach {
                    events += "\nevent[${idx++}]: ${it}"
                }
                LOG.info("Test Events: $events")
                throw t
            }
        }

        @Override
        boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
            logger.info("Received onPreDispatch: "+eventHeaders)
            if (preDispatchLambda)
                return preDispatchLambda.onPreDispatchConsumerEvent(eventHeaders, data, isPoisonEvent)
            return true
        }

        @Override
        void onPostCompletionConsumerEvent(boolean success, @NotNull EventHeaders eventHeaders, @Nullable Object result, boolean isPoisonEvent, @Nullable Throwable error) {
            logger.info("Received onPostCompletion: "+eventHeaders)
            results.add(new Result(success, isPoisonEvent, eventHeaders, result))
        }

        @Override
        void onConsumerDeadEventPreCompletion(@NotNull EventHeaders eventHeaders, @NotNull byte[] data) {
            logger.info("Received onConsumerDeadEventPreCompletion: "+eventHeaders)
        }

        @Override
        void onConsumerDeadEventPostCompletion(boolean success, @NotNull EventHeaders eventHeaders, @Nullable Throwable error) {
            logger.info("Received onConsumerDeadEventPostCompletion: "+eventHeaders)
        }

        @Override
        void onSuccessfulProducerSendEvent(@NotNull EventHeaders eventHeaders, @NotNull Object message, @NotNull Object partitionKey) {
            logger.info("Received onSuccessfulProducerSendEvent: "+eventHeaders)
        }

        @Override
        void onFailedProducerSendEvent(@NotNull EventHeaders eventHeaders, @NotNull Object message, @NotNull Object partitionKey) {
            logger.info("Received onFailedProducerSendEvent: "+eventHeaders)
        }
    }
}
