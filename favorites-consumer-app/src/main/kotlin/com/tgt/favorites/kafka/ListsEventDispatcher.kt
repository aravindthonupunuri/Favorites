package com.tgt.favorites.kafka

import com.tgt.lists.msgbus.ApplicationDataObject
import com.tgt.lists.msgbus.EventDispatcher
import com.tgt.lists.msgbus.ExecutionId
import com.tgt.lists.msgbus.event.EventHeaders
import com.tgt.lists.msgbus.execution.ExecutionSerialization
import io.micronaut.context.annotation.Value
import mu.KotlinLogging
import reactor.core.publisher.Mono
import javax.inject.Singleton

@Singleton
open class ListsEventDispatcher(
    @Value("\${msgbus.source}") val source: String
) : EventDispatcher {

    private val logger = KotlinLogging.logger {}

    override fun dispatchEvent(eventHeaders: EventHeaders, data: Any, isPoisonEvent: Boolean): Mono<Triple<Boolean, EventHeaders, Any>> {
        logger.debug { "Unhandled eventType: ${eventHeaders.eventType}" }
        return Mono.just(Triple(true, eventHeaders, data))
    }

    /**
     * Transform ByteArray data to a concrete type based on event type header
     * It is also used by msgbus framework during dql publish exception handling
     */
    override fun transformValue(eventHeaders: EventHeaders, data: ByteArray): Triple<ExecutionId?, ExecutionSerialization, ApplicationDataObject>? {
        return null
    }

    /**
     * Handle DLQ dead events here
     * @return Triple<ExecutionId?, ExecutionSerialization, Mono<Void>>
     *                          Possible values:
     *                          null - to discard this event as we don't want to handle this dead event
     *                          OR
     *                          Triple:
     *                          =======s
     *                          ExecutionId - used only for ID_SERIALIZATION to denote a unique string identifying the processing of this event (usually some kind of business id)
     *                          ExecutionSerialization - type of serialization processing required for this event
     *                          Mono<Void> - dead event processing lambda to be run
     */
    override fun handleDlqDeadEvent(eventHeaders: EventHeaders, data: ByteArray): Triple<ExecutionId?, ExecutionSerialization, Mono<Void>>? {
        return null
    }
}
