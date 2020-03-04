package com.tgt.shoppinglist.kafka.api

import com.tgt.lists.lib.api.persistence.ListRepository
import com.tgt.lists.lib.api.service.CreateListItemService
import com.tgt.lists.lib.api.service.DeleteListItemService
import com.tgt.lists.lib.api.service.DeleteMultipleListItemService
import com.tgt.lists.lib.api.service.EditItemSortOrderService
import com.tgt.lists.lib.api.transport.EditItemSortOrderRequestTO
import com.tgt.lists.lib.api.transport.ListItemMetaDataTO
import com.tgt.lists.lib.api.transport.UserItemMetaDataTO
import com.tgt.lists.lib.api.util.*
import com.tgt.lists.lib.kafka.model.CreateListItemNotifyEvent
import com.tgt.lists.lib.kafka.model.DeleteListItemNotifyEvent
import com.tgt.lists.lib.kafka.model.EditListItemSortOrderActionEvent
import com.tgt.lists.msgbus.event.EventHeaders
import com.tgt.lists.msgbus.event.EventLifecycleNotificationProvider
import com.tgt.shoppinglist.api.util.CartDataProvider
import com.tgt.shoppinglist.util.BaseKafkaFunctionalTest
import com.tgt.shoppinglist.util.PreDispatchLambda
import io.micronaut.test.annotation.MicronautTest
import org.jetbrains.annotations.NotNull
import spock.lang.Shared
import spock.lang.Stepwise
import spock.util.concurrent.PollingConditions

import javax.inject.Inject
import java.time.LocalDateTime

import static com.tgt.shoppinglist.util.DataProvider.getCartContentURI

@MicronautTest
@Stepwise
class GuestEditingListItemSortOrderFunctionalTest extends BaseKafkaFunctionalTest {

    CartDataProvider cartDataProvider = new CartDataProvider()
    PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

    @Shared
    @Inject
    EventLifecycleNotificationProvider eventNotificationsProvider
    @Shared
    TestEventListener testEventListener
    @Inject
    ListRepository listRepository
    @Inject
    CreateListItemService createListItemService
    @Inject
    DeleteListItemService deleteListItemService
    @Inject
    DeleteMultipleListItemService deleteMultipleListItemService
    @Inject
    EditItemSortOrderService editItemSortOrderService
    @Shared
    String guestId = UUID.randomUUID().toString()
    @Shared
    UUID listId = UUID.randomUUID()
    @Shared
    UUID listItemId1
    @Shared
    UUID listItemId2
    @Shared
    UUID listItemId3

    def setupSpec() {
        waitForKafkaReadiness()
        testEventListener = new TestEventListener()
        eventNotificationsProvider.registerListener(testEventListener)
    }

    def setup() {
        testEventListener.preDispatchLambda = null
        testEventListener.results.clear()
    }

    def "Guest creates list item 1"() {
        given:
        def listRequestTO = cartDataProvider.getListItemRequestTO(ItemType.TCIN, "1234567")
        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)
        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), listRequestTO.tcin,
            "itemTitle", "itemNote", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))
        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == CreateListItemNotifyEvent.getEventType()) {
                    def createListItem = CreateListItemNotifyEvent.deserialize(data)
                    if (createListItem.itemId == cartItemResponse.cartItemId) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        def actual = createListItemService.createListItem(listId, 1357L, listRequestTO).block()
        listItemId1 = actual.listItemId

        then:
        actual.listItemId == cartItemResponse.cartItemId
        actual.tcin == cartItemResponse.tcin
        actual.itemTitle == cartItemResponse.tenantItemName
        actual.itemNote == cartItemResponse.notes
        actual.price == cartItemResponse.price
        actual.listPrice == cartItemResponse.listPrice
        actual.images == cartItemResponse.images
        actual.itemType == itemMetaData1.itemType
        actual.itemExpiration == itemMetaData1.itemExpiration

        1 * mockServer.get({ path -> path.contains(getCartContentURI(listId))}, _) >> [status: 200, body: cartContentsResponse]
        1 * mockServer.post({ path -> path.contains("/carts/v4/cart_items")},_,_) >> [status: 200, body: cartItemResponse]

        then:
        testEventListener.verifyEvents { events ->
            conditions.eventually {
                assert events.size() == 1
                assert events[0].success
            }
        }

        and:
        def list = listRepository.find(listId).block()
        list.listItemSortOrder == listItemId1.toString()
    }

    def "Guest creates list item 2"() {
        given:
        def listRequestTO = cartDataProvider.getListItemRequestTO(ItemType.TCIN, "1234569")
        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)
        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), listRequestTO.tcin,
            "itemTitle", "itemNote", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))
        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == CreateListItemNotifyEvent.getEventType()) {
                    def createListItem = CreateListItemNotifyEvent.deserialize(data)
                    if (createListItem.itemId == cartItemResponse.cartItemId) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        def actual = createListItemService.createListItem(listId, 1357L, listRequestTO).block()
        listItemId2 = actual.listItemId

        then:
        actual.listItemId == cartItemResponse.cartItemId
        actual.tcin == cartItemResponse.tcin
        actual.itemTitle == cartItemResponse.tenantItemName
        actual.itemNote == cartItemResponse.notes
        actual.price == cartItemResponse.price
        actual.listPrice == cartItemResponse.listPrice
        actual.images == cartItemResponse.images
        actual.itemType == itemMetaData1.itemType
        actual.itemExpiration == itemMetaData1.itemExpiration

        1 * mockServer.get({ path -> path.contains(getCartContentURI(listId))}, _) >> [status: 200, body: cartContentsResponse]
        1 * mockServer.post({ path -> path.contains("/carts/v4/cart_items")},_,_) >> [status: 200, body: cartItemResponse]

        then:
        testEventListener.verifyEvents { events ->
            conditions.eventually {
                assert events.size() == 1
                assert events[0].success
            }
        }

        and:
        def list = listRepository.find(listId).block()
        list.listItemSortOrder == listItemId2.toString() + "," + listItemId1.toString()
    }

    def "Guest creates list item 3"() {
        given:
        def listRequestTO = cartDataProvider.getListItemRequestTO(ItemType.TCIN, "1234571")
        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)
        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), listRequestTO.tcin,
            "itemTitle", "itemNote", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))
        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == CreateListItemNotifyEvent.getEventType()) {
                    def createListItem = CreateListItemNotifyEvent.deserialize(data)
                    if (createListItem.itemId == cartItemResponse.cartItemId) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        def actual = createListItemService.createListItem(listId, 1357L, listRequestTO).block()
        listItemId3 = actual.listItemId

        then:
        actual.listItemId == cartItemResponse.cartItemId
        actual.tcin == cartItemResponse.tcin
        actual.itemTitle == cartItemResponse.tenantItemName
        actual.itemNote == cartItemResponse.notes
        actual.price == cartItemResponse.price
        actual.listPrice == cartItemResponse.listPrice
        actual.images == cartItemResponse.images
        actual.itemType == itemMetaData1.itemType
        actual.itemExpiration == itemMetaData1.itemExpiration

        1 * mockServer.get({ path -> path.contains(getCartContentURI(listId))}, _) >> [status: 200, body: cartContentsResponse]
        1 * mockServer.post({ path -> path.contains("/carts/v4/cart_items")},_,_) >> [status: 200, body: cartItemResponse]

        then:
        testEventListener.verifyEvents { events ->
            conditions.eventually {
                assert events.size() == 1
                assert events[0].success
            }
        }

        and:
        def list = listRepository.find(listId).block()
        list.listItemSortOrder == listItemId3.toString() + "," + listItemId2.toString() + "," + listItemId1.toString()
    }

    def "Guest moves item3 below item2"() {
        given:
        def editItemSortOrderRequestTO = new EditItemSortOrderRequestTO(listId, listItemId3, listItemId2, Direction.BELOW)
        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == EditListItemSortOrderActionEvent.getEventType()) {
                    def editListItemSortOrder = EditListItemSortOrderActionEvent.deserialize(data)
                    if (editListItemSortOrder.editItemSortOrderRequestTO.listId == editItemSortOrderRequestTO.listId) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        def actual = editItemSortOrderService.editItemPosition(editItemSortOrderRequestTO).block()

        then:
        actual

        then:
        testEventListener.verifyEvents { events ->
            conditions.eventually {
                assert events.size() == 1
                assert events[0].success
            }
        }

        and:
        def listItemSortDetails = listRepository.find(listId).block()
        listItemSortDetails.listItemSortOrder == listItemId2.toString() + "," + listItemId3.toString() + "," + listItemId1.toString()
    }

    def "Guest moves item1 above item2"() {
        given:
        def editItemSortOrderRequestTO = new EditItemSortOrderRequestTO(listId, listItemId1, listItemId2, Direction.ABOVE)
        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == EditListItemSortOrderActionEvent.getEventType()) {
                    def editListItemSortOrder = EditListItemSortOrderActionEvent.deserialize(data)
                    if (editListItemSortOrder.editItemSortOrderRequestTO.listId == editItemSortOrderRequestTO.listId) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        def actual = editItemSortOrderService.editItemPosition(editItemSortOrderRequestTO).block()

        then:
        actual

        then:
        testEventListener.verifyEvents { events ->
            conditions.eventually {
                assert events.size() == 1
                assert events[0].success
            }
        }

        and:
        def listItemSortDetails = listRepository.find(listId).block()
        listItemSortDetails.listItemSortOrder == listItemId1.toString() + "," + listItemId2.toString() + "," + listItemId3.toString()
    }

    def "Guest deletes list item 3"() {
        given:
        def cartItemUri = "/carts/v4/cart_items/" + listItemId3.toString() + "?cart_id=" + listId.toString()
        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN,
            LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, listItemId3, "1234571",
            "some title", "some note", 1, 10, 10, "Stand Alone", "READY_FOR_LAUNCH",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))
        def cartItemDeleteResponse = cartDataProvider.getCartItemDeleteResponse(listId, listItemId3)
        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == DeleteListItemNotifyEvent.getEventType()) {
                    def deleteListItem = DeleteListItemNotifyEvent.deserialize(data)
                    if (deleteListItem.deleteListItems[0].itemId == cartItemDeleteResponse.cartItemId) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        def actual = deleteListItemService.deleteListItem(guestId, listId, listItemId3).block()

        then:
        actual.listItemId == listItemId3

        1 * mockServer.get({ path -> path.contains(cartItemUri) }, _) >> [status: 200, body: cartItemResponse]
        1 * mockServer.post({ path -> path.contains("/carts/v4/deleted_cart_items")},_,_) >> [status: 200, body: cartItemDeleteResponse]

        then:
        testEventListener.verifyEvents { events ->
            conditions.eventually {
                assert events.size() == 1
                assert events[0].success
            }
        }

        and:
        def list = listRepository.find(listId).block()
        list.listItemSortOrder == listItemId1.toString() + "," + listItemId2.toString()
    }

    def "Guest deletes all pending items"() {
        given:
        ListItemMetaDataTO pendingItemMetaData = new ListItemMetaDataTO(null, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, listItemId1, "1234567",
            "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
            null, LocalDateTime.now(), null)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, listItemId2, "1234569",
            "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
            null, LocalDateTime.now(), null)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def pendingCartContentsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])
        def pendingCartItemDeleteResponse = cartDataProvider.getDeleteMultiCartItemsResponse(listId, [listItemId1,listItemId2], [])
        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == DeleteListItemNotifyEvent.getEventType()) {
                    def deleteListItem = DeleteListItemNotifyEvent.deserialize(data)
                    def items = [listItemId1, listItemId2]
                    if (items.contains(deleteListItem.deleteListItems[0].itemId)
                        && items.contains(deleteListItem.deleteListItems[1].itemId)) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        def actual = deleteMultipleListItemService.deleteMultipleListItem(guestId, listId, null, ItemIncludeFields.PENDING).block()

        then:
        [listItemId1, listItemId2].containsAll(actual.successListItemIds)

        1 * mockServer.get({ path -> path.contains(getCartContentURI(listId)) }, _) >> [status: 200, body: pendingCartContentsResponse]
        1 * mockServer.post({ path -> path.contains("/carts/v4/delete_multi_cart_items") }, _, _) >> [status: 200, body: pendingCartItemDeleteResponse]

        then:
        testEventListener.verifyEvents { events ->
            conditions.eventually {
                assert events.size() == 1
                assert events[0].success
            }
        }

        and:
        def list = listRepository.find(listId).block()
        list.listItemSortOrder == ""
    }

}
