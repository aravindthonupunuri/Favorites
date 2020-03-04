package com.tgt.shoppinglist.kafka.api

import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.lib.api.service.UpdateListItemService
import com.tgt.lists.lib.api.transport.ListItemMetaDataTO
import com.tgt.lists.lib.api.transport.UserItemMetaDataTO
import com.tgt.lists.lib.api.util.Constants
import com.tgt.lists.lib.api.util.ItemType
import com.tgt.lists.lib.api.util.LIST_CHANNEL
import com.tgt.lists.lib.api.util.LIST_ITEM_STATE
import com.tgt.lists.lib.kafka.model.CompletionItemActionEvent
import com.tgt.lists.lib.kafka.model.CreateListItemNotifyEvent
import com.tgt.lists.lib.kafka.model.DeleteListItemActionEvent
import com.tgt.lists.lib.kafka.model.DeleteListItemNotifyEvent
import com.tgt.lists.msgbus.event.EventHeaders
import com.tgt.lists.msgbus.event.EventLifecycleNotificationProvider
import com.tgt.shoppinglist.api.util.TestUtilConstants
import com.tgt.shoppinglist.util.BaseKafkaFunctionalTest
import com.tgt.shoppinglist.util.PreDispatchLambda
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MicronautTest
import org.jetbrains.annotations.NotNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Stepwise
import spock.util.concurrent.PollingConditions

import javax.inject.Inject
import java.util.stream.Collectors

@MicronautTest
@Stepwise
class ItemStatePendingToCompletedFunctionalTest extends BaseKafkaFunctionalTest {

    private static Logger logger = LoggerFactory.getLogger(ItemStatePendingToCompletedFunctionalTest)

    @Inject
    UpdateListItemService updateListItemService
    @Shared
    @Inject
    EventLifecycleNotificationProvider eventNotificationsProvider
    @Shared
    TestEventListener testEventListener

    String guestId = UUID.randomUUID().toString()
    String listId = UUID.randomUUID().toString()
    String itemId = UUID.randomUUID().toString()
    Long locationId = 1375L
    String completedListId = UUID.randomUUID().toString()
    String completedItemId1 = UUID.randomUUID().toString()
    String completedItemId2 = UUID.randomUUID().toString()
    String completedItemId3 = UUID.randomUUID().toString()

    def setupSpec() {
        waitForKafkaReadiness()
        testEventListener = new TestEventListener()
        eventNotificationsProvider.registerListener(testEventListener)
    }

    def setup() {
        testEventListener.preDispatchLambda = null
        testEventListener.results.clear()
    }

    def "test update item state from pending to completed"() {
        given:
        def listItemUpdateRequest = cartDataProvider.getListItemUpdateRequest(LIST_ITEM_STATE.COMPLETED)

        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(UUID.fromString(listId), UUID.fromString(itemId), "1234",
            "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def createCartItemResponse = cartDataProvider.getCartItemResponse(UUID.fromString(listId), UUID.fromString(itemId), "1234",
            "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        def cartResponse = cartDataProvider.getCartResponse(UUID.fromString(listId), guestId,
                    LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])
        def cartLists = [cartResponse]
        def cartItemDeleteResponse = cartDataProvider.getCartItemDeleteResponse(UUID.fromString(listId), UUID.fromString(itemId))
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId) }, _) >> [status: 200, body: cartItemResponse] // call to get the item from the pending list
        // *****************************   Async operations  *********************************
        1 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=$guestId&cart_state=PENDING")}, _) >> [status: 200, body: cartLists] // get call for finding the completed cart
        2 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId) }, _) >> [status: 200, body: cartItemResponse] // call to get the item from the pending list
        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_contents/")}, _) >> [status: 200, body: cartContentsResponse] // dedup call
        1 * mockServer.post({ path -> path.contains("/carts/v4/cart_items")},_, _) >> [status: 200, body: createCartItemResponse] // call to create item in the completed list
        1 * mockServer.post({ path -> path.contains("/carts/v4/deleted_cart_items")}, _, _) >> [status: 200, body: cartItemDeleteResponse] // call to delete item in the pending list

        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == CompletionItemActionEvent.getEventType()) {
                    def completionItem = CompletionItemActionEvent.deserialize(data)
                    if (completionItem.listId.toString() == listId) {
                        return true
                    }
                } else if(eventHeaders.eventType == CreateListItemNotifyEvent.eventType) {
                    def createListItem = CreateListItemNotifyEvent.deserialize(data)
                    if (createListItem.listId.toString() == cartItemResponse.cartId.toString()) {
                        return true
                    }
                } else if(eventHeaders.eventType == DeleteListItemNotifyEvent.eventType) {
                    def deleteListItem = DeleteListItemNotifyEvent.deserialize(data)
                    if (deleteListItem.listId.toString() == cartItemDeleteResponse.cartId.toString()) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        updateListItemService.updateListItem(guestId, locationId, UUID.fromString(listId), UUID.fromString(itemId), listItemUpdateRequest).block()

        then:
        testEventListener.verifyEvents { events ->
            conditions.eventually {
                //asserting completion event
                assert events.size() == 3 // [one completion, one createlistitem, one deletelistitem]
                assert events.any { it.eventHeaders.eventType == CompletionItemActionEvent.eventType && it.success }
            }
        }
    }

    def "test update item state from pending to completed - validating max completed items count"() {
        given:
        def listItemUpdateRequest = cartDataProvider.getListItemUpdateRequest(LIST_ITEM_STATE.COMPLETED)

        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        ListItemMetaDataTO pendingListItemMetaData = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse = cartDataProvider.getCartItemResponse(UUID.fromString(listId), UUID.fromString(itemId), "1234",
            "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(pendingListItemMetaData, new UserItemMetaDataTO()), null, null, null)

        ListItemMetaDataTO createListItemMetaData = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def createCartItemResponse = cartDataProvider.getCartItemResponse(UUID.fromString(listId), UUID.fromString(itemId), "1234",
            "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(createListItemMetaData, new UserItemMetaDataTO()), null, null, null)

        ListItemMetaDataTO completedListItemMetaData = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)    // Completed List Item Metadata
        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(UUID.fromString(completedListId), UUID.fromString(completedItemId1), "123",
            "itemTitle1", 1, "itemNote1",10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(completedListItemMetaData, new UserItemMetaDataTO()), null, null, null)
        def completedCartItemResponse2 = cartDataProvider.getCartItemResponse(UUID.fromString(completedListId), UUID.fromString(completedItemId2), "124",
            "itemTitle2", 1, "itemNote2",10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(completedListItemMetaData, new UserItemMetaDataTO()), null, null, null)
        def completedCartItemResponse3 = cartDataProvider.getCartItemResponse(UUID.fromString(completedListId), UUID.fromString(completedItemId3), "125",
            "itemTitle3", 1, "itemNote3",10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(completedListItemMetaData, new UserItemMetaDataTO()), null, null, null)

        def completedCartResponse = cartDataProvider.getCartResponse(UUID.fromString(completedListId), guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])
        def cartLists = [completedCartResponse]
        def cartItemDeleteResponse = cartDataProvider.getCartItemDeleteResponse(UUID.fromString(listId), UUID.fromString(itemId))
        def completedCartItemDeleteResponse = cartDataProvider.getCartItemDeleteResponse(UUID.fromString(completedListId), UUID.fromString(completedItemId1))
        def completedCartContentsResponse = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1, completedCartItemResponse2, completedCartItemResponse3])

        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId) }, _) >> [status: 200, body: pendingCartItemResponse] //call to get the item from the pending list
        // *****************************   Async operations  *********************************
        1 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=$guestId&cart_state=PENDING")}, _) >> [status: 200, body: cartLists] // get call for finding the completed cart
        2 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId) }, _) >> [status: 200, body: pendingCartItemResponse] // call to get the item from the pending list
        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_contents/")}, _) >> [status: 200, body: completedCartContentsResponse] // dedup call
        1 * mockServer.post({ path -> path.contains("/carts/v4/deleted_cart_items")}, _, _) >> [status: 200, body: completedCartItemDeleteResponse] // call to delete the first item in completed list which is older one
        1 * mockServer.post({ path -> path.contains("/carts/v4/cart_items")},_, _) >> [status: 200, body: createCartItemResponse] // call to create item in the completed list
        1 * mockServer.post({ path -> path.contains("/carts/v4/deleted_cart_items")}, _, _) >> [status: 200, body: cartItemDeleteResponse] // call to delete item in the pending list

        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == CompletionItemActionEvent.getEventType()) {
                    def completionItem = CompletionItemActionEvent.deserialize(data)
                    if (completionItem.listId.toString() == listId) {
                        return true
                    }
                } else if(eventHeaders.eventType == CreateListItemNotifyEvent.eventType) {
                    def createListItem = CreateListItemNotifyEvent.deserialize(data)
                    if (createListItem.listId.toString() == pendingCartItemResponse.cartId.toString()) {
                        return true
                    }
                } else if(eventHeaders.eventType == DeleteListItemNotifyEvent.eventType) {
                    def deleteListItem = DeleteListItemNotifyEvent.deserialize(data)
                    if (deleteListItem.listId.toString() == cartItemDeleteResponse.cartId.toString()) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        updateListItemService.updateListItem(guestId, locationId, UUID.fromString(listId), UUID.fromString(itemId), listItemUpdateRequest).block()

        then:
        testEventListener.verifyEvents { events ->
            conditions.eventually {
                assert events.size() == 3 // [one completion, one createlistitem, one deletelistitem]
                //asserting completion event
                assert events.any { it.eventHeaders.eventType == CompletionItemActionEvent.eventType && it.success }

            }
        }
    }

    def "test update item state from pending to completed with failure in updating the item status to completed in the pending list"() {
        given:
        def listItemUpdateRequest = cartDataProvider.getListItemUpdateRequest(LIST_ITEM_STATE.COMPLETED)

        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId) }, _) >> [status: 400, body: '{"message": "400 bad request"}'] //call to get the item from the pending list

        when:
        updateListItemService.updateListItem(guestId, locationId, UUID.fromString(listId), UUID.fromString(itemId), listItemUpdateRequest).block()

        then:
        def error = thrown(HttpClientResponseException)
        error.status == HttpStatus.BAD_REQUEST
    }

    def "test update item state from pending to completed with exception while getting completed listId"() {
        given:
        def listItemUpdateRequest = cartDataProvider.getListItemUpdateRequest(LIST_ITEM_STATE.COMPLETED)

        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(UUID.fromString(listId), UUID.fromString(itemId), "1234",
            "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def createCartItemResponse = cartDataProvider.getCartItemResponse(UUID.fromString(listId), UUID.fromString(itemId), "1234",
            "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        def cartResponse = cartDataProvider.getCartResponse(UUID.randomUUID(), guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])
        def cartLists = [cartResponse]
        def cartItemDeleteResponse = cartDataProvider.getCartItemDeleteResponse(UUID.fromString(listId), UUID.fromString(itemId))
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId) }, _) >> [status: 200, body: cartItemResponse] //call to get the item from the pending list
        // *****************************   Async operations  *********************************
        1 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=$guestId&cart_state=PENDING")}, _) >> [status: 400, body: '{"message": "400 bad request"}']  // get call for finding the completed cart
        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId) }, _) >> [status: 200, body: cartItemResponse] // call to get the item from the pending list
        // ****************************  Retry events *****************************************
        1 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=$guestId&cart_state=PENDING")}, _) >> [status: 200, body: cartLists] // get call for finding the completed cart
        2 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId) }, _) >> [status: 200, body: cartItemResponse] // call to get the item from the pending list
        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_contents/")}, _) >> [status: 200, body: cartContentsResponse] // dedup call
        1 * mockServer.post({ path -> path.contains("/carts/v4/cart_items")},_, _) >> [status: 200, body: createCartItemResponse] // call to create item in the completed list
        1 * mockServer.post({ path -> path.contains("/carts/v4/deleted_cart_items")}, _, _) >> [status: 200, body: cartItemDeleteResponse] // call to delete item in the pending list

        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == CompletionItemActionEvent.getEventType()) {
                    def completionItem = CompletionItemActionEvent.deserialize(data)
                    if (completionItem.listId.toString() == listId) {
                        return true
                    }
                } else if(eventHeaders.eventType == CreateListItemNotifyEvent.eventType) {
                    def createListItem = CreateListItemNotifyEvent.deserialize(data)
                    if (createListItem.listId.toString() == cartItemResponse.cartId.toString()) {
                        return true
                    }
                } else if(eventHeaders.eventType == DeleteListItemNotifyEvent.eventType) {
                    def deleteListItem = DeleteListItemNotifyEvent.deserialize(data)
                    if (deleteListItem.listId.toString() == cartItemDeleteResponse.cartId.toString()) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        updateListItemService.updateListItem(guestId, locationId, UUID.fromString(listId), UUID.fromString(itemId), listItemUpdateRequest).block()

        then:
        testEventListener.verifyEvents { events ->
            conditions.eventually {
                assert events.size() == 4 // [two completion, one createlistitem, one deletelistitem]
                def completionEvents = events.stream().filter {
                    return it.data instanceof CompletionItemActionEvent
                }.collect(Collectors.toList())

                assert completionEvents.size() == 2
                //asserting final completion event
                assert completionEvents.any { it.eventHeaders.eventType == CompletionItemActionEvent.eventType && it.success && !it.poisonEvent && it.eventHeaders.retryCount == 1 }
            }
        }
    }

    def "test update item state from pending to completed with poison event"() {
        given:
        def listItemUpdateRequest = cartDataProvider.getListItemUpdateRequest(LIST_ITEM_STATE.COMPLETED)

        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(UUID.fromString(listId), UUID.fromString(itemId), "1234",
            "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        2 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId) }, _) >> [status: 200, body: cartItemResponse] // call to get the item from the pending list
        // *****************************   Async operations wit retry *********************************
        4 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=$guestId&cart_state=PENDING")}, _) >> [status: 400, body: '{"message": "400 bad request"}']  // get call for finding the completed cart
        3 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId) }, _) >> [status: 200, body: cartItemResponse] // call to get the item from the pending list

        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == CompletionItemActionEvent.getEventType()) {
                    def completionItem = CompletionItemActionEvent.deserialize(data)
                    if (completionItem.listId.toString() == listId) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        updateListItemService.updateListItem(guestId, locationId, UUID.fromString(listId), UUID.fromString(itemId), listItemUpdateRequest).block()

        then:
        testEventListener.verifyEvents { events ->
            conditions.eventually {
                assert events.size() == 4 // [1 original completion + 3 retries]
                //asserting final completion event
                assert events.any { it.eventHeaders.eventType == CompletionItemActionEvent.eventType && it.success && it.poisonEvent && it.eventHeaders.retryCount == 3 }
            }
        }
    }

    def "test update item state from pending to completed with exception while deleting the item in pending list"() {
        given:
        def listItemUpdateRequest = cartDataProvider.getListItemUpdateRequest(LIST_ITEM_STATE.COMPLETED)

        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(UUID.fromString(listId), UUID.fromString(itemId), "1234",
            "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def createCartItemResponse = cartDataProvider.getCartItemResponse(UUID.fromString(listId), UUID.fromString(itemId), "1234",
            "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)


        def cartResponse = cartDataProvider.getCartResponse(UUID.randomUUID(), guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])
        def cartLists = [cartResponse]
        def cartItemDeleteResponse = cartDataProvider.getCartItemDeleteResponse(UUID.fromString(listId), UUID.fromString(itemId))
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId) }, _) >> [status: 200, body: cartItemResponse] // call to get the item from the pending list
        // *****************************   Async operations  *********************************
        1 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=$guestId&cart_state=PENDING")}, _) >> [status: 200, body: cartLists]  // get call for finding the completed cart
        2 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId) }, _) >> [status: 200, body: cartItemResponse] // call to get the item from the pending list
        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_contents/")}, _) >> [status: 200, body: cartContentsResponse] // dedup call
        1 * mockServer.post({ path -> path.contains("/carts/v4/cart_items")},_, _) >> [status: 200, body: createCartItemResponse] // call to create item in the completed list
        1 * mockServer.post({ path -> path.contains("/carts/v4/deleted_cart_items")}, _, _) >> [status: 400, body: '{"message": "400 bad request"}']// call to delete item in the pending list
        // ****************************  Retry events *****************************************
        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId) }, _) >> [status: 200, body: cartItemResponse] // call to get the item from the pending list
        1 * mockServer.post({ path -> path.contains("/carts/v4/deleted_cart_items")}, _, _) >> [status: 200, body: cartItemDeleteResponse] // call to delete item in the pending list

        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == DeleteListItemActionEvent.getEventType()) {
                    def deleteItem = DeleteListItemActionEvent.deserialize(data)
                    if (deleteItem.listId.toString() == listId) {
                        return true
                    }
                } else if (eventHeaders.eventType == CompletionItemActionEvent.getEventType()) {
                    def completionItem = CompletionItemActionEvent.deserialize(data)
                    if (completionItem.listId.toString() == listId) {
                        return true
                    }
                } else if(eventHeaders.eventType == CreateListItemNotifyEvent.eventType) {
                    def createListItem = CreateListItemNotifyEvent.deserialize(data)
                    if (createListItem.listId.toString() == cartItemResponse.cartId.toString()) {
                        return true
                    }
                } else if(eventHeaders.eventType == DeleteListItemNotifyEvent.eventType) {
                    def deleteListItem = DeleteListItemNotifyEvent.deserialize(data)
                    if (deleteListItem.listId.toString() == cartItemDeleteResponse.cartId.toString()) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        updateListItemService.updateListItem(guestId, locationId, UUID.fromString(listId), UUID.fromString(itemId), listItemUpdateRequest).block()

        then:
        testEventListener.verifyEvents { events ->
            conditions.eventually {
                assert events.size() == 4 // [one completion, one createlistitem, one deletelistitem, one deleteitem]
                //asserting delete item event
                assert events.any { it.eventHeaders.eventType == DeleteListItemActionEvent.eventType && it.success && !it.poisonEvent && it.eventHeaders.retryCount == 1 }
            }
        }
    }
}
