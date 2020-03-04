package com.tgt.shoppinglist.kafka

import com.tgt.lists.lib.api.async.*
import com.tgt.lists.lib.api.util.Constants.LISTS_CCPA_EVENT_SOURCE
import com.tgt.lists.lib.kafka.model.*
import com.tgt.lists.msgbus.ApplicationDataObject
import com.tgt.lists.msgbus.EventDispatcher
import com.tgt.lists.msgbus.ExecutionId
import com.tgt.lists.msgbus.event.EventHeaders
import com.tgt.lists.msgbus.execution.ExecutionSerialization
import io.micronaut.context.annotation.Value
import mu.KotlinLogging
import reactor.core.publisher.Mono
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class ListsEventDispatcher(
    @Inject val updateCompletionItemService: UpdateCompletionItemService,
    @Inject val updatePendingItemService: UpdatePendingItemService,
    @Inject val deleteItemService: DeleteItemService,
    @Inject val listSortOrderService: ListSortOrderService,
    @Inject val listItemSortOrderService: ListItemSortOrderService,
    @Inject val deleteGuestsListsService: DeleteGuestsListsService,
    @Value("\${msgbus.source}") val source: String
) : EventDispatcher {

    private val logger = KotlinLogging.logger {}

    override fun dispatchEvent(eventHeaders: EventHeaders, data: Any, isPoisonEvent: Boolean): Mono<Triple<Boolean, EventHeaders, Any>> {
        if (eventHeaders.source == source) {
            // handle following events only from configured source
            when (eventHeaders.eventType) {
                CompletionItemActionEvent.getEventType() -> {
                    // always use transformValue to convert raw data to concrete type
                    val completionItemActionEvent = data as CompletionItemActionEvent
                    logger.info { "Got CompletionItem Event: $completionItemActionEvent" }
                    return updateCompletionItemService.processCompletionItemEvent(completionItemActionEvent, isPoisonEvent, eventHeaders)
                }
                PendingItemActionEvent.getEventType() -> {
                    // always use transformValue to convert raw data to concrete type
                    val pendingItemActionEvent = data as PendingItemActionEvent
                    logger.info { "Got PendingItem Event: $pendingItemActionEvent" }
                    return updatePendingItemService.processPendingItemEvent(pendingItemActionEvent, isPoisonEvent, eventHeaders)
                }
                DeleteListItemActionEvent.getEventType() -> {
                    // always use transformValue to convert raw data to concrete type
                    val deleteListItemActionEvent = data as DeleteListItemActionEvent
                    logger.info { "Got DeleteItem Event: $deleteListItemActionEvent" }
                    return deleteItemService.processDeleteItemEvent(deleteListItemActionEvent, isPoisonEvent, eventHeaders)
                }
                CreateListNotifyEvent.getEventType() -> {
                    // always use transformValue to convert raw data to concrete type
                    val createListNotifyEvent = data as CreateListNotifyEvent
                    logger.info { "Got CreateList Event: $createListNotifyEvent" }
                    return listSortOrderService.saveListSortOrder(createListNotifyEvent, eventHeaders).map { it }
                }
                DeleteListNotifyEvent.getEventType() -> {
                    // always use transformValue to convert raw data to concrete type
                    val deleteListNotifyEvent = data as DeleteListNotifyEvent
                    logger.info { "Got DeleteList Event: $deleteListNotifyEvent" }
                    return listSortOrderService.deleteListSortOrder(deleteListNotifyEvent, eventHeaders).map { it }
                }
                EditListSortOrderActionEvent.getEventType() -> {
                    // always use transformValue to convert raw data to concrete type
                    val editListSortOrderActionEvent = data as EditListSortOrderActionEvent
                    logger.info { "Got EditListSortOrder Event: $editListSortOrderActionEvent" }
                    return listSortOrderService.editListSortOrder(editListSortOrderActionEvent, eventHeaders).map { it }
                }
                CreateListItemNotifyEvent.getEventType() -> {
                    // always use transformValue to convert raw data to concrete type
                    val createListItemNotifyEvent = data as CreateListItemNotifyEvent
                    logger.info { "Got CreateListItem Event: $createListItemNotifyEvent" }
                    return listItemSortOrderService.saveListItemSortOrder(createListItemNotifyEvent, eventHeaders).map { it }
                }
                DeleteListItemNotifyEvent.getEventType() -> {
                    // always use transformValue to convert raw data to concrete type
                    val deleteListItemNotifyEvent = data as DeleteListItemNotifyEvent
                    logger.info { "Got DeleteListItem Event: $deleteListItemNotifyEvent" }
                    return listItemSortOrderService.deleteListItemSortOrder(deleteListItemNotifyEvent, eventHeaders).map { it }
                }
                EditListItemSortOrderActionEvent.getEventType() -> {
                    // always use transformValue to convert raw data to concrete type
                    val editListItemSortOrderActionEvent = data as EditListItemSortOrderActionEvent
                    logger.info { "Got EditListItemSortOrder Event: $editListItemSortOrderActionEvent" }
                    return listItemSortOrderService.editListItemSortOrder(editListItemSortOrderActionEvent, eventHeaders).map { it }
                }
            }
        }

        if (eventHeaders.source == LISTS_CCPA_EVENT_SOURCE) {
            when (eventHeaders.eventType) {
                DeleteGuestsListsActionEvent.getEventType() -> {
                    // always use transformValue to convert raw data to concrete type
                    val deleteGuestsListsActionEvent = data as DeleteGuestsListsActionEvent
                    logger.info { "CCPA delete guests lists event: $deleteGuestsListsActionEvent" }
                    return deleteGuestsListsService.deleteGuestsLists(deleteGuestsListsActionEvent, isPoisonEvent, eventHeaders).map { it }
                }
            }
        }

        logger.debug { "Unhandled eventType: ${eventHeaders.eventType}" }
        return Mono.just(Triple(true, eventHeaders, data))
    }

    /**
     * Transform ByteArray data to a concrete type based on event type header
     * It is also used by msgbus framework during dql publish exception handling
     */
    override fun transformValue(eventHeaders: EventHeaders, data: ByteArray): Triple<ExecutionId?, ExecutionSerialization, ApplicationDataObject>? {
        when (eventHeaders.source) {
            source -> {
                return when (eventHeaders.eventType) {
                    CompletionItemActionEvent.getEventType() -> {
                        val completionItemActionEvent = CompletionItemActionEvent.deserialize(data)
                        Triple("lists_${completionItemActionEvent.listId}", ExecutionSerialization.ID_SERIALIZATION, completionItemActionEvent)
                    }
                    PendingItemActionEvent.getEventType() -> {
                        val pendingItemActionEvent = PendingItemActionEvent.deserialize(data)
                        Triple("lists_${pendingItemActionEvent.listId}", ExecutionSerialization.ID_SERIALIZATION, pendingItemActionEvent)
                    }
                    DeleteListItemActionEvent.getEventType() -> {
                        val deleteListItemActionEvent = DeleteListItemActionEvent.deserialize(data)
                        Triple("lists_${deleteListItemActionEvent.listId}", ExecutionSerialization.ID_SERIALIZATION, deleteListItemActionEvent)
                    }
                    CreateListNotifyEvent.getEventType() -> {
                        val createListNotifyEvent = CreateListNotifyEvent.deserialize(data)
                        Triple("guest_${createListNotifyEvent.guestId}", ExecutionSerialization.ID_SERIALIZATION, createListNotifyEvent)
                    }
                    DeleteListNotifyEvent.getEventType() -> {
                        val deleteListNotifyEvent = DeleteListNotifyEvent.deserialize(data)
                        Triple("guest_${deleteListNotifyEvent.guestId}", ExecutionSerialization.ID_SERIALIZATION, deleteListNotifyEvent)
                    }
                    EditListSortOrderActionEvent.getEventType() -> {
                        val editListSortOrderActionEvent = EditListSortOrderActionEvent.deserialize(data)
                        Triple(null, ExecutionSerialization.NO_SERIALIZATION, editListSortOrderActionEvent)
                    }
                    CreateListItemNotifyEvent.getEventType() -> {
                        val createListItemNotifyEvent = CreateListItemNotifyEvent.deserialize(data)
                        Triple("lists_${createListItemNotifyEvent.listId}", ExecutionSerialization.ID_SERIALIZATION, createListItemNotifyEvent)
                    }
                    DeleteListItemNotifyEvent.getEventType() -> {
                        val deleteListItemNotifyEvent = DeleteListItemNotifyEvent.deserialize(data)
                        Triple("lists_${deleteListItemNotifyEvent.listId}", ExecutionSerialization.ID_SERIALIZATION, deleteListItemNotifyEvent)
                    }
                    EditListItemSortOrderActionEvent.getEventType() -> {
                        val editListItemSortOrderActionEvent = EditListItemSortOrderActionEvent.deserialize(data)
                        Triple(null, ExecutionSerialization.NO_SERIALIZATION, editListItemSortOrderActionEvent)
                    }
                    else -> null
                }
            }

            LISTS_CCPA_EVENT_SOURCE -> {
                return when (eventHeaders.eventType) {
                    DeleteGuestsListsActionEvent.getEventType() -> {
                        val deleteGuestsListsActionEvent = DeleteGuestsListsActionEvent.deserialize(data)
                        Triple(null, ExecutionSerialization.NO_SERIALIZATION, deleteGuestsListsActionEvent)
                    }
                    else -> null
                }
            }
        }

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
