package com.tgt.favorites.util

import com.tgt.lists.micronaut.test.MockServer
import com.tgt.lists.micronaut.test.MockServerDelegate
import com.tgt.lists.msgbus.ListsMessageBusProducer
import com.tgt.lists.msgbus.event.EventHeaderFactory
import com.tgt.lists.msgbus.event.EventLifecycleNotificationProvider
import com.tgt.lists.msgbus.metrics.MetricsPublisher
import com.tgt.lists.msgbus.producer.MsgbusKafkaProducerClient
import com.tgt.lists.msgbus.tracing.EventTracer
import com.tgt.lists.msgbus.tracing.ZipkinTracingMapper
import com.tgt.favorites.api.util.CartDataProvider
import com.tgt.favorites.api.util.ListV2DataProvider
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.MockClock
import io.micrometer.core.instrument.simple.SimpleConfig
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.support.TestPropertyProvider
import io.opentracing.mock.MockTracer
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import spock.lang.Specification

import javax.inject.Inject

class BaseFunctionalTest extends Specification implements TestPropertyProvider {

    static Logger logger = LoggerFactory.getLogger(BaseFunctionalTest)

    @Inject
    @Client("/")
    RxHttpClient client
    @Inject
    MockServerDelegate mockServerDelegate
    MockServer mockServer
    CartDataProvider cartDataProvider
    ListV2DataProvider listV2DataProvider

    def setup() {
        mockServer = Mock(MockServer)
        mockServer.post({ path -> path.contains("/auth/oauth/v2/token") }, _, _) >> [status: 200, body: DataProvider.getTokenResponse()]
        mockServerDelegate.delegate = mockServer
        cartDataProvider = new CartDataProvider()
        listV2DataProvider = new ListV2DataProvider()
    }

    @Override
    Map<String, String> getProperties() {
        int httpServerPort = SocketUtils.findAvailableTcpPort()
        logger.info("Using HTTP server-port: $httpServerPort")
        Map<String, String> properties = [
            "serverPort" : httpServerPort,
            "tracing.zipkin.enabled": true,
            "tracing.zipkin.sample-rate-percent": 100
        ]
        return properties
    }

    ListsMessageBusProducer newMockMsgbusKafkaProducerClient(EventLifecycleNotificationProvider eventNotificationsProvider) {
        MeterRegistry meterRegistry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
        EventHeaderFactory eventHeaderFactory = new EventHeaderFactory(2, 5)
        return new ListsMessageBusProducer("dummySrc", "dummyTopic", eventHeaderFactory, new MsgbusKafkaProducerClient() {
            @Override
            Mono<RecordMetadata> sendEvent(Object partitionKey, @NotNull Mono data, @NotNull byte[] eventId, @NotNull byte[] eventType,
                                           @Nullable byte[] correlationId, @Nullable byte[] timestamp,
                                           @Nullable byte[] errorCode, @Nullable byte[] errorMessage,
                                           @Nullable byte[] retryCount, @Nullable byte[] retryTimestamp, @Nullable byte[] maxRetryCount,
                                           @Nullable byte[] source, @Nullable byte[] traceHeader, @Nullable byte[] mdcHeader) {
                def metadata = new RecordMetadata(new TopicPartition("dummy", 1), 1, 1, System.currentTimeMillis(),
                    1, 1, 1)
                return Mono.just(metadata)
            }
        }, eventNotificationsProvider, new EventTracer(new MockTracer(), new ZipkinTracingMapper(), null, false), new MetricsPublisher(meterRegistry), null)
    }
}
