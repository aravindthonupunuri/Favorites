package com.tgt.shoppinglist.kafka.api

import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.lib.kafka.model.CreateListItemNotifyEvent
import com.tgt.lists.lib.api.service.UpdateListItemService
import com.tgt.lists.lib.api.transport.ListItemMetaDataTO
import com.tgt.lists.lib.api.transport.UserItemMetaDataTO
import com.tgt.lists.lib.api.util.Constants
import com.tgt.lists.lib.api.util.ItemType
import com.tgt.lists.lib.api.util.LIST_CHANNEL
import com.tgt.lists.lib.api.util.LIST_ITEM_STATE
import com.tgt.lists.lib.kafka.model.DeleteListItemNotifyEvent
import com.tgt.lists.lib.kafka.model.PendingItemActionEvent
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

@MicronautTest
@Stepwise
class ItemStateCompletedToPendingFunctionalTest extends BaseKafkaFunctionalTest {

    private static Logger logger = LoggerFactory.getLogger(ItemStateCompletedToPendingFunctionalTest)

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
   // String pendingItemId = UUID.randomUUID().toString()
    Long locationId = 1375L

    def setupSpec() {
        waitForKafkaReadiness()
        testEventListener = new TestEventListener()
        eventNotificationsProvider.registerListener(testEventListener)
    }

    def setup() {
        testEventListener.preDispatchLambda = null
        testEventListener.results.clear()
    }

    def "test update item state from completed to pending"() {
        given:
        def listItemUpdateRequest = cartDataProvider.getListItemUpdateRequest(LIST_ITEM_STATE.PENDING)
        def completedCartId = UUID.randomUUID()

        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        def completedCartResponse = cartDataProvider.getCartResponse(completedCartId, guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "Completed list", "My completed list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])
        def cartLists = [completedCartResponse]

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse = cartDataProvider.getCartItemResponse(completedCartResponse.cartId, UUID.fromString(itemId), "1234",
            "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def createCartItemResponse = cartDataProvider.getCartItemResponse(UUID.fromString(listId), UUID.randomUUID(), "1234",
            "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        def pendingCartResponse = cartDataProvider.getCartResponse(UUID.fromString(listId), guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "Pending list", "My pending list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])
        def pendingCartContentsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [])

        def cartItemDeleteResponse = cartDataProvider.getCartItemDeleteResponse(completedCartResponse.cartId, UUID.fromString(itemId))

        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId+"?cart_id="+listId) }, _) >> [status: 404] // call to get the item in pending list
        1 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=$guestId&cart_state=PENDING")}, _) >> [status: 200, body: cartLists] // get call for finding the completed list
        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId+"?cart_id="+completedCartId) }, _) >> [status: 200, body: completedCartItemResponse] // call to get the item in the completed list
        // *****************************   Async operations  *********************************
        1 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=$guestId&cart_state=PENDING")}, _) >> [status: 200, body: cartLists] // get call for finding the completed list
        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId+"?cart_id="+completedCartId) }, _) >> [status: 200, body: completedCartItemResponse] // call to get the item in the completed list
        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_contents/")}, _) >> [status: 200, body: pendingCartContentsResponse] // dedup call
        1 * mockServer.post({ path -> path.contains("/carts/v4/cart_items")},_, _) >> [status: 200, body: createCartItemResponse] // call to create the item in the pending list
        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId+"?cart_id="+completedCartId) }, _) >> [status: 200, body: completedCartItemResponse] // call to get the item in the completed list
        1 * mockServer.post({ path -> path.contains("/carts/v4/deleted_cart_items")}, _, _) >> [status: 200, body: cartItemDeleteResponse] // call to delete item in the completed list

        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                // order of assertion follows event creation
                if(eventHeaders.eventType == CreateListItemNotifyEvent.eventType) {
                    def createListItem = CreateListItemNotifyEvent.deserialize(data)
                    if (createListItem.listId.toString() == createCartItemResponse.cartId.toString()) {
                        return true
                    }
                } else if (eventHeaders.eventType == PendingItemActionEvent.getEventType()) {
                    def pendingItem = PendingItemActionEvent.deserialize(data)
                    if (pendingItem.listId.toString() == listId) {
                        return true
                    }
                } else if(eventHeaders.eventType == DeleteListItemNotifyEvent.eventType) {
                    def deleteListItem = DeleteListItemNotifyEvent.deserialize(data)
                    if (deleteListItem.listId.toString() == completedCartResponse.cartId.toString()) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        updateListItemService.updateListItem(guestId, locationId,  UUID.fromString(listId), UUID.fromString(itemId), listItemUpdateRequest).block()

        then:
        testEventListener.verifyEvents { events ->
            conditions.eventually {
                assert events.size() == 3 // [one pending, one createlistitem, one deletelistitem]
                // asserting pending item
                assert events.any { it.eventHeaders.eventType == PendingItemActionEvent.eventType && it.success }
            }
        }
    }

    def "test update item state from completed to pending with exception finding the completed listId"() {
        given:
        def listItemUpdateRequest = cartDataProvider.getListItemUpdateRequest(LIST_ITEM_STATE.PENDING)

        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId+"?cart_id="+listId) }, _) >> [status: 404] // call to get the item in pending list
        1 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=$guestId&cart_state=PENDING")}, _) >> [status: 400, body: '{"message": "400 bad request"}'] // get call for finding the completed list

        when:
        updateListItemService.updateListItem(guestId, locationId,  UUID.fromString(listId), UUID.fromString(itemId), listItemUpdateRequest).block()

        then:
        def error = thrown(HttpClientResponseException)
        error.status == HttpStatus.BAD_REQUEST
    }

    def "test update item state from completed to pending with exception getting the item details from the completed list"() {
        given:
        def listItemUpdateRequest = cartDataProvider.getListItemUpdateRequest(LIST_ITEM_STATE.PENDING)
        def completedCartId = UUID.randomUUID()

        def completedCartResponse = cartDataProvider.getCartResponse(completedCartId, guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "Completed list", "My completed list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])
        def cartLists = [completedCartResponse]

        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId+"?cart_id="+listId) }, _) >> [status: 404] // call to get the item in pending list
        1 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=$guestId&cart_state=PENDING")}, _) >> [status: 200, body: cartLists] // get call for finding the completed list
        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId+"?cart_id="+completedCartId) }, _) >> [status: 400, body: '{"message": "400 bad request"}'] // call to get the item in the completed list


        when:
        updateListItemService.updateListItem(guestId, locationId,  UUID.fromString(listId), UUID.fromString(itemId), listItemUpdateRequest).block()

        then:
        def error = thrown(HttpClientResponseException)
        error.status == HttpStatus.BAD_REQUEST
    }

    def "test update item state from completed to pending with exception in pending item count exceeding 3"() {
        given:
        def listItemUpdateRequest = cartDataProvider.getListItemUpdateRequest(LIST_ITEM_STATE.PENDING)
        def completedCartId = UUID.randomUUID()

        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        def completedCartResponse = cartDataProvider.getCartResponse(completedCartId, guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "Completed list", "My completed list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])
        def cartLists = [completedCartResponse]

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse = cartDataProvider.getCartItemResponse(completedCartResponse.cartId, UUID.fromString(itemId), "1234",
            "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        def pendingCartResponse = cartDataProvider.getCartResponse(UUID.fromString(listId), guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "Pending list", "My pending list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])
        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(UUID.fromString(listId), UUID.randomUUID(), "1236",
            "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(UUID.fromString(listId), UUID.randomUUID(), "1237",
            "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        def pendingCartItemResponse3 = cartDataProvider.getCartItemResponse(UUID.fromString(listId), UUID.randomUUID(), "1238",
            "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)
        def pendingCartContentsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2, pendingCartItemResponse3])

        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId+"?cart_id="+listId) }, _) >> [status: 404] // call to get the item in pending list
        1 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=$guestId&cart_state=PENDING")}, _) >> [status: 200, body: cartLists] // get call for finding the completed list
        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId+"?cart_id="+completedCartId) }, _) >> [status: 200, body: completedCartItemResponse] // call to get the item in the completed list
        // *****************************   Async operations  *********************************
        4 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=$guestId&cart_state=PENDING")}, _) >> [status: 200, body: cartLists] // get call for finding the completed list
        4 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId+"?cart_id="+completedCartId) }, _) >> [status: 200, body: completedCartItemResponse] // call to get the item in the completed list
        4 * mockServer.get({ path -> path.contains("/carts/v4/cart_contents/")}, _) >> [status: 200, body: pendingCartContentsResponse] // dedup call
        // ****************************  Retry events *****************************************

        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == PendingItemActionEvent.getEventType()) {
                    def pendingItem = PendingItemActionEvent.deserialize(data)
                    if (pendingItem.listId.toString() == listId) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        updateListItemService.updateListItem(guestId, locationId,  UUID.fromString(listId), UUID.fromString(itemId), listItemUpdateRequest).block()

        then:
        testEventListener.verifyEvents { events ->
            conditions.eventually {
                assert events.size() == 4 // [4 pending]
                //asserting final pending item
                assert events.any { it.eventHeaders.eventType == PendingItemActionEvent.eventType && it.success && it.poisonEvent && it.eventHeaders.retryCount == 3 }
            }
        }
    }

    def "test update item state from completed to pending with exception creating the item in the pending list"() {
        given:
        def listItemUpdateRequest = cartDataProvider.getListItemUpdateRequest(LIST_ITEM_STATE.PENDING)
        def completedCartId = UUID.randomUUID()

        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        def completedCartResponse = cartDataProvider.getCartResponse(completedCartId, guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "Completed list", "My completed list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])
        def cartLists = [completedCartResponse]

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse = cartDataProvider.getCartItemResponse(completedCartResponse.cartId, UUID.fromString(itemId), "1234",
            "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def createCartItemResponse = cartDataProvider.getCartItemResponse(UUID.fromString(listId), UUID.randomUUID(), "1234",
            "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        def pendingCartResponse = cartDataProvider.getCartResponse(UUID.fromString(listId), guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "Pending list", "My pending list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])
        def pendingCartContentsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [])


        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId+"?cart_id="+listId) }, _) >> [status: 404] // call to get the item in pending list
        1 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=$guestId&cart_state=PENDING")}, _) >> [status: 200, body: cartLists] // get call for finding the completed list
        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId+"?cart_id="+completedCartId) }, _) >> [status: 200, body: completedCartItemResponse] // call to get the item in the completed list
        // *****************************   Async operations  *********************************
        4 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=$guestId&cart_state=PENDING")}, _) >> [status: 200, body: cartLists] // get call for finding the completed list
        4 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId+"?cart_id="+completedCartId) }, _) >> [status: 200, body: completedCartItemResponse] // call to get the item in the completed list
        4 * mockServer.get({ path -> path.contains("/carts/v4/cart_contents/")}, _) >> [status: 200, body: pendingCartContentsResponse] // dedup call
        4 * mockServer.post({ path -> path.contains("/carts/v4/cart_items")},_, _) >> [status: 400, body: '{"message": "400 bad request"}']  // call to create the item in the pending list

        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == PendingItemActionEvent.getEventType()) {
                    def pendingItem = PendingItemActionEvent.deserialize(data)
                    if (pendingItem.listId.toString() == listId) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        updateListItemService.updateListItem(guestId, locationId,  UUID.fromString(listId), UUID.fromString(itemId), listItemUpdateRequest).block()

        then:
        testEventListener.verifyEvents { events ->
            conditions.eventually {
                assert events.size() == 4 // [4 pending]
                //asserting final pending item
                assert events.any { it.eventHeaders.eventType == PendingItemActionEvent.eventType && it.success && it.poisonEvent && it.eventHeaders.retryCount == 3 }
            }
        }
    }
//
//
//    def "test update item state from completed to pending with no completed cart present"() {
//        given:
//        def listItemUpdateRequest = cartDataProvider.getListItemUpdateRequest(LIST_ITEM_STATE.PENDING)
//
//        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
//        def completedCartItemResponse = cartDataProvider.getCartItemResponse(UUID.fromString(listId), UUID.fromString(itemId), "1234",
//            "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
//            "READY", "some-url", "some-image",
//            cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)
//
//        def pendingCartItemResponse = cartDataProvider.getCartItemResponse(UUID.fromString(listId), UUID.fromString(pendingItemId), "1234",
//            "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
//            "READY", "some-url", "some-image",
//            cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)
//
//        def pendingCartResponse = cartDataProvider.getCartResponse(UUID.fromString(listId), guestId,
//            LIST_CHANNEL.WEB, CartType.LIST, "Pending list", "My pending list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])
//        def pendingCartContentsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [])
//
//        def cartItemDeleteResponse = cartDataProvider.getCartItemDeleteResponse(UUID.fromString(listId), UUID.fromString(itemId))
//
//        1 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=$guestId&cart_state=PENDING")}, _) >> [status: 404, body: null] // get call for finding the completed listId which has pending listId as its cart number
//        0 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId) }, _) >> [status: 200, body: completedCartItemResponse] // call to get the item details from the completed list which is used to construct the item in the pending list
//        0 * mockServer.get({ path -> path.contains("/carts/v4/cart_contents/")}, _) >> [status: 200, body: pendingCartContentsResponse] // call to check for deduplication and pending item count
//        0 * mockServer.post({ path -> path.contains("/carts/v4/cart_items")},_, _) >> [status: 200, body: pendingCartItemResponse] // call to create the item in the pending list
//        // *****************************   Async operations  *********************************
//        0 * mockServer.post({ path -> path.contains("/carts/v4/deleted_cart_items")}, _, _) >> [status: 200, body: cartItemDeleteResponse] // call to delete item in the completed list
//
//        when:
//        updateListItemService.updateListItem(guestId, UUID.fromString(listId), 1357L, UUID.fromString(itemId), listItemUpdateRequest).block()
//
//        then:
//        thrown(RuntimeException)
//    }
//
//    def "test update item state from completed to pending with no item present in completed list"() {
//        given:
//        def listItemUpdateRequest = cartDataProvider.getListItemUpdateRequest(LIST_ITEM_STATE.PENDING)
//
//        def completedCartResponse = cartDataProvider.getCartResponse(UUID.randomUUID(), guestId,
//            LIST_CHANNEL.WEB, CartType.LIST, "Completed list", "My completed list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])
//        def cartLists = [completedCartResponse]
//
//        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
//        def pendingCartItemResponse = cartDataProvider.getCartItemResponse(UUID.fromString(listId), UUID.fromString(pendingItemId), "1234",
//            "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
//            "READY", "some-url", "some-image",
//            cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)
//
//        def pendingCartResponse = cartDataProvider.getCartResponse(UUID.fromString(listId), guestId,
//            LIST_CHANNEL.WEB, CartType.LIST, "Pending list", "My pending list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])
//        def pendingCartContentsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [])
//
//        def cartItemDeleteResponse = cartDataProvider.getCartItemDeleteResponse(UUID.fromString(listId), UUID.fromString(itemId))
//
//        1 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=$guestId&cart_state=PENDING")}, _) >> [status: 200, body: cartLists] // get call for finding the completed listId which has pending listId as its cart number
//        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId) }, _) >> [status: 404, body: null] // call to get the item details from the completed list which is used to construct the item in the pending list
//        0 * mockServer.get({ path -> path.contains("/carts/v4/cart_contents/")}, _) >> [status: 200, body: pendingCartContentsResponse] // call to check for deduplication and pending item count
//        0 * mockServer.post({ path -> path.contains("/carts/v4/cart_items")},_, _) >> [status: 200, body: pendingCartItemResponse] // call to create the item in the pending list
//        // *****************************   Async operations  *********************************
//        0 * mockServer.post({ path -> path.contains("/carts/v4/deleted_cart_items")}, _, _) >> [status: 200, body: cartItemDeleteResponse] // call to delete item in the completed list
//
//        when:
//        updateListItemService.updateListItem(guestId, UUID.fromString(listId), 1357L, UUID.fromString(itemId), listItemUpdateRequest).block()
//
//        then:
//        thrown(RuntimeException)
//    }
//
//    def "test update item state from completed to pending with exception deleting the item in completed list"() {
//        given:
//        def listItemUpdateRequest = cartDataProvider.getListItemUpdateRequest(LIST_ITEM_STATE.PENDING)
//
//        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)
//
//        def completedCartResponse = cartDataProvider.getCartResponse(UUID.randomUUID(), guestId,
//            LIST_CHANNEL.WEB, CartType.LIST, "Completed list", "My completed list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])
//        def cartLists = [completedCartResponse]
//
//        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
//        def completedCartItemResponse = cartDataProvider.getCartItemResponse(UUID.randomUUID(), UUID.fromString(itemId), "1234",
//            "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
//            "READY", "some-url", "some-image",
//            cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)
//
//        def pendingCartItemResponse = cartDataProvider.getCartItemResponse(UUID.fromString(listId), UUID.fromString(pendingItemId), "1234",
//            "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
//            "READY", "some-url", "some-image",
//            cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)
//
//        def pendingCartResponse = cartDataProvider.getCartResponse(UUID.fromString(listId), guestId,
//            LIST_CHANNEL.WEB, CartType.LIST, "Pending list", "My pending list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])
//        def pendingCartContentsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [])
//
//        def cartItemDeleteResponse = cartDataProvider.getCartItemDeleteResponse(completedCartResponse.cartId, UUID.fromString(itemId))
//
//        1 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=$guestId&cart_state=PENDING")}, _) >> [status: 200, body: cartLists] // get call for finding the completed listId which has pending listId as its cart number
//        3 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId) }, _) >> [status: 200, body: completedCartItemResponse] // call to get the item details from the completed list which is used to construct the item in the pending list
//        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_contents/")}, _) >> [status: 200, body: pendingCartContentsResponse] // call to check for deduplication and pending item count
//        1 * mockServer.post({ path -> path.contains("/carts/v4/cart_items")},_, _) >> [status: 200, body: pendingCartItemResponse] // call to create the item in the pending list
//        // **************************** Async operations  ************************************
//        1 * mockServer.post({ path -> path.contains("/carts/v4/deleted_cart_items")}, _, _) >> [status: 400, body: '{"message": "400 bad request"}'] // call to delete item in the completed list
//        // ****************************  Retry event *****************************************
//        1 * mockServer.post({ path -> path.contains("/carts/v4/deleted_cart_items")}, _, _) >> [status: 200, body: cartItemDeleteResponse] // call to delete item in the completed list
//
//        testEventListener.preDispatchLambda = new PreDispatchLambda() {
//            @Override
//            boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
//                // order of assertion follows event creation
//                if(eventHeaders.eventType == CreateListItemNotifyEvent.eventType) {
//                    def createListItem = CreateListItemNotifyEvent.deserialize(data)
//                    if (createListItem.listId.toString() == pendingCartItemResponse.cartId.toString()) {
//                        return true
//                    }
//                } else if (eventHeaders.eventType == PendingItemActionEvent.getEventType()) {
//                    def pendingItem = PendingItemActionEvent.deserialize(data)
//                    if (pendingItem.listId.toString() == listId) {
//                        return true
//                    }
//                } else if(eventHeaders.eventType == DeleteListItemNotifyEvent.eventType) {
//                    def deleteListItem = DeleteListItemNotifyEvent.deserialize(data)
//                    if (deleteListItem.listId.toString() == completedCartResponse.cartId.toString()) {
//                        return true
//                    }
//                }
//                return false
//            }
//        }
//
//        when:
//        updateListItemService.updateListItem(guestId, UUID.fromString(listId), 1357L, UUID.fromString(itemId), listItemUpdateRequest).block()
//
//        then:
//        testEventListener.verifyEvents { events ->
//            conditions.eventually {
//                assert events.size() == 3 // [1 pending, one createlistitem, one deletelistitem]
//                // asserting pending item
//                assert events.any { it.eventHeaders.eventType == PendingItemActionEvent.eventType && it.success && !it.poisonEvent && it.eventHeaders.retryCount == 1 }
//            }
//        }
//    }
//
//    def "test update item state from completed to pending with exception deleting the item in completed list with poison event"() {
//        given:
//        def listItemUpdateRequest = cartDataProvider.getListItemUpdateRequest(LIST_ITEM_STATE.PENDING)
//
//        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)
//
//        def completedCartResponse = cartDataProvider.getCartResponse(UUID.randomUUID(), guestId,
//            LIST_CHANNEL.WEB, CartType.LIST, "Completed list", "My completed list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])
//        def cartLists = [completedCartResponse]
//
//        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
//        def completedCartItemResponse = cartDataProvider.getCartItemResponse(UUID.fromString(listId), UUID.fromString(itemId), "1234",
//            "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
//            "READY", "some-url", "some-image",
//            cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)
//
//        def pendingCartItemResponse = cartDataProvider.getCartItemResponse(UUID.fromString(listId), UUID.fromString(pendingItemId), "1234",
//            "itemTitle", 1, "itemNote",10, 10, "Stand Alone",
//            "READY", "some-url", "some-image",
//            cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)
//
//        def pendingCartResponse = cartDataProvider.getCartResponse(UUID.fromString(listId), guestId,
//            LIST_CHANNEL.WEB, CartType.LIST, "Pending list", "My pending list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])
//        def pendingCartContentsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [])
//
//        1 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=$guestId&cart_state=PENDING")}, _) >> [status: 200, body: cartLists] // get call for finding the completed listId which has pending listId as its cart number
//        5 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/"+itemId) }, _) >> [status: 200, body: completedCartItemResponse] // call to get the item details from the completed list which is used to construct the item in the pending list
//        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_contents/")}, _) >> [status: 200, body: pendingCartContentsResponse] // call to check for deduplication and pending item count
//        1 * mockServer.post({ path -> path.contains("/carts/v4/cart_items")},_, _) >> [status: 200, body: pendingCartItemResponse] // call to create the item in the pending list
//        // *****************************   Async operations with retry *********************************
//        4 * mockServer.post({ path -> path.contains("/carts/v4/deleted_cart_items")}, _, _) >> [status: 400, body: '{"message": "400 bad request"}'] // call to delete item in the completed list
//
//        testEventListener.preDispatchLambda = new PreDispatchLambda() {
//            @Override
//            boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
//                // order of assertion follows event creation - no delete event generated here
//                if(eventHeaders.eventType == CreateListItemNotifyEvent.eventType) {
//                    def createListItem = CreateListItemNotifyEvent.deserialize(data)
//                    if (createListItem.listId.toString() == pendingCartItemResponse.cartId.toString()) {
//                        return true
//                    }
//                } else if (eventHeaders.eventType == PendingItemActionEvent.getEventType()) {
//                    def pendingItem = PendingItemActionEvent.deserialize(data)
//                    if (pendingItem.listId.toString() == listId) {
//                        return true
//                    }
//                }
//                return false
//            }
//        }
//
//        when:
//        updateListItemService.updateListItem(guestId, locationId, UUID.fromString(listId), UUID.fromString(itemId), listItemUpdateRequest).block()
//
//        then:
//        testEventListener.verifyEvents { events ->
//            conditions.eventually {
//                assert events.size() == 5 // [4 pending, one createlistitem]
//                //asserting final pending item
//                assert events.any { it.eventHeaders.eventType == PendingItemActionEvent.eventType && it.success && it.poisonEvent && it.eventHeaders.retryCount == 3 }
//            }
//        }
//    }
}
