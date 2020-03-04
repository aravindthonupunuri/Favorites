package com.tgt.shoppinglist.kafka.api

import com.tgt.lists.cart.transport.CartResponse
import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.lib.api.persistence.GuestPreferenceRepository
import com.tgt.lists.lib.api.service.CreateListService
import com.tgt.lists.lib.api.service.DeleteListService
import com.tgt.lists.lib.api.service.EditListSortOrderService
import com.tgt.lists.lib.api.service.GetAllListService
import com.tgt.lists.lib.api.transport.*
import com.tgt.lists.lib.api.util.Direction
import com.tgt.lists.lib.api.util.LIST_CHANNEL
import com.tgt.lists.lib.api.util.LIST_STATUS
import com.tgt.lists.lib.api.util.ListSortFieldGroup
import com.tgt.lists.lib.kafka.model.CreateListNotifyEvent
import com.tgt.lists.lib.kafka.model.DeleteListNotifyEvent
import com.tgt.lists.lib.kafka.model.EditListSortOrderActionEvent
import com.tgt.lists.msgbus.event.EventHeaders
import com.tgt.lists.msgbus.event.EventLifecycleNotificationProvider
import com.tgt.shoppinglist.api.util.CartDataProvider
import com.tgt.shoppinglist.util.BaseKafkaFunctionalTest
import com.tgt.shoppinglist.util.DataProvider
import com.tgt.shoppinglist.util.PreDispatchLambda
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.test.annotation.MicronautTest
import org.jetbrains.annotations.NotNull
import spock.lang.Shared
import spock.lang.Stepwise
import spock.util.concurrent.PollingConditions

import javax.inject.Inject

@MicronautTest
@Stepwise
class GuestEditingListSortOrderFunctionalTest extends BaseKafkaFunctionalTest {

    CartDataProvider cartDataProvider = new CartDataProvider()
    PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

    @Shared
    @Inject
    EventLifecycleNotificationProvider eventNotificationsProvider
    @Shared
    TestEventListener testEventListener
    @Inject
    GuestPreferenceRepository guestListRepository
    @Inject
    CreateListService createListService
    @Inject
    DeleteListService deleteListService
    @Inject
    EditListSortOrderService editListSortOrderService
    @Inject
    GetAllListService getAllListService
    @Shared
    String guestId = UUID.randomUUID().toString()
    @Shared
    UUID listId1
    @Shared
    UUID listId2
    @Shared
    UUID listId3
    @Shared
    UUID listId4

    def setupSpec() {
        waitForKafkaReadiness()
        testEventListener = new TestEventListener()
        eventNotificationsProvider.registerListener(testEventListener)
    }

    def setup() {
        testEventListener.preDispatchLambda = null
        testEventListener.results.clear()
    }

    def "Guest creates list 1"() {
        given:

        def listRequestTO = new ListRequestTO(LIST_CHANNEL.WEB, "list1", 1375L, "short", true, null, null)

        ListMetaDataTO metadata = new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.PENDING)
        def pendingCartResponse = cartDataProvider.getCartResponse(UUID.randomUUID(), guestId,
            listRequestTO.channel, CartType.LIST,
            listRequestTO.listTitle, listRequestTO.shortDescription,
            null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))

        ListMetaDataTO completedCartMetadata = new ListMetaDataTO(false, "SHOPPING", LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse = cartDataProvider.getCartResponse(UUID.randomUUID(), guestId, pendingCartResponse.cartId.toString(),
            cartDataProvider.getMetaData(completedCartMetadata, new UserMetaDataTO()))

        def listMetaData = cartDataProvider.getListMetaDataFromCart(pendingCartResponse.metadata)

        mockServer.get({ path -> path.contains("/carts/v4")},_) >> [status: 200, body: []]
        mockServer.post({ path -> path.contains("/carts/v4")},_,_) >>> [[status: 200, body: pendingCartResponse],
                                                                        [status: 200, body: completedCartResponse]]

        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == CreateListNotifyEvent.getEventType()) {
                    def createList = CreateListNotifyEvent.deserialize(data)
                    if ([pendingCartResponse.cartId, completedCartResponse.cartId].contains(createList.listId)) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        // using HTTP based list creation to do distributed tracing starting with HTTP call.
        HttpResponse<ListResponseTO> createListResponse = client.toBlocking()
            .exchange(HttpRequest.POST("/testserver/list_create", listRequestTO)
            .headers(DataProvider.getHeaders(guestId, true)), ListResponseTO)
        def actual = createListResponse.body()
        listId1 = actual.listId

        then:
        actual.listId == pendingCartResponse.cartId
        actual.channel == LIST_CHANNEL.valueOf(pendingCartResponse.cartChannel)
        actual.listTitle == pendingCartResponse.tenantCartName
        actual.shortDescription == pendingCartResponse.tenantCartDescription
        actual.listType == listMetaData.listType
        actual.defaultList == listMetaData.defaultList

        testEventListener.verifyEvents { events ->
            conditions.eventually {
                assert events.size() == 2
                assert events[0].success
                assert events[1].success
            }
        }

        and:
        def guestList = guestListRepository.find(guestId).block()
        guestList != null
        guestList.listSortOrder == listId1.toString()
    }

    def "Guest creates list 2"() {
        given:
        def listRequestTO = new ListRequestTO(LIST_CHANNEL.WEB, "list1", 1375L, "short", true, null, null)

        ListMetaDataTO metadata = new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.PENDING)
        def pendingCartResponse = cartDataProvider.getCartResponse(UUID.randomUUID(), guestId,
            listRequestTO.channel, CartType.LIST,
            listRequestTO.listTitle, listRequestTO.shortDescription,
            null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))

        ListMetaDataTO completedCartMetadata = new ListMetaDataTO(false, "SHOPPING", LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse = cartDataProvider.getCartResponse(UUID.randomUUID(), guestId, pendingCartResponse.cartId.toString(),
            cartDataProvider.getMetaData(completedCartMetadata, new UserMetaDataTO()))

        def listMetaData = cartDataProvider.getListMetaDataFromCart(pendingCartResponse.metadata)

        mockServer.get({ path -> path.contains("/carts/v4")},_) >> [status: 200, body: []]
        mockServer.post({ path -> path.contains("/carts/v4")},_,_) >>> [[status: 200, body: pendingCartResponse],
                                                                        [status: 200, body: completedCartResponse]]

        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == CreateListNotifyEvent.getEventType()) {
                    def createList = CreateListNotifyEvent.deserialize(data)
                    if ([pendingCartResponse.cartId, completedCartResponse.cartId].contains(createList.listId)) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        def actual = createListService.createList(guestId, listRequestTO).block()
        listId2 = actual.listId

        then:
        actual.listId == pendingCartResponse.cartId
        actual.channel == LIST_CHANNEL.valueOf(pendingCartResponse.cartChannel)
        actual.listTitle == pendingCartResponse.tenantCartName
        actual.shortDescription == pendingCartResponse.tenantCartDescription
        actual.listType == listMetaData.listType
        actual.defaultList == listMetaData.defaultList

        testEventListener.verifyEvents { events ->
            conditions.eventually {
                assert events.size() == 2
                assert events[0].success
                assert events[1].success
            }
        }

        and:
        def guestList = guestListRepository.find(guestId).block()
        guestList.listSortOrder == listId2.toString() + "," + listId1.toString()
    }

    def "Guest creates list 3"() {
        given:
        def listRequestTO = new ListRequestTO(LIST_CHANNEL.WEB, "list1", 1375L, "short", true, null, null)

        ListMetaDataTO metadata = new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.PENDING)
        def pendingCartResponse = cartDataProvider.getCartResponse(UUID.randomUUID(), guestId,
            listRequestTO.channel, CartType.LIST,
            listRequestTO.listTitle, listRequestTO.shortDescription,
            null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))

        ListMetaDataTO completedCartMetadata = new ListMetaDataTO(false, "SHOPPING", LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse = cartDataProvider.getCartResponse(UUID.randomUUID(), guestId, pendingCartResponse.cartId.toString(),
            cartDataProvider.getMetaData(completedCartMetadata, new UserMetaDataTO()))

        def listMetaData = cartDataProvider.getListMetaDataFromCart(pendingCartResponse.metadata)

        mockServer.get({ path -> path.contains("/carts/v4")},_) >> [status: 200, body: []]
        mockServer.post({ path -> path.contains("/carts/v4")},_,_) >>> [[status: 200, body: pendingCartResponse],
                                                                        [status: 200, body: completedCartResponse]]

        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == CreateListNotifyEvent.getEventType()) {
                    def createList = CreateListNotifyEvent.deserialize(data)
                    if ([pendingCartResponse.cartId, completedCartResponse.cartId].contains(createList.listId)) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        def actual = createListService.createList(guestId, listRequestTO).block()
        listId3 = actual.listId

        then:
        actual.listId == pendingCartResponse.cartId
        actual.channel == LIST_CHANNEL.valueOf(pendingCartResponse.cartChannel)
        actual.listTitle == pendingCartResponse.tenantCartName
        actual.shortDescription == pendingCartResponse.tenantCartDescription
        actual.listType == listMetaData.listType
        actual.defaultList == listMetaData.defaultList

        testEventListener.verifyEvents { events ->
            conditions.eventually {
                assert events.size() == 2
                assert events[0].success
                assert events[1].success
            }
        }

        and:
        def guestList = guestListRepository.find(guestId).block()
        guestList.listSortOrder == listId3.toString() + "," + listId2.toString() + "," + listId1.toString()
    }

    def "Guest creates list 4"() {
        given:
        def listRequestTO = new ListRequestTO(LIST_CHANNEL.WEB, "list1", 1375L, "short", true, null, null)

        ListMetaDataTO metadata = new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.PENDING)
        def pendingCartResponse = cartDataProvider.getCartResponse(UUID.randomUUID(), guestId,
            listRequestTO.channel, CartType.LIST,
            listRequestTO.listTitle, listRequestTO.shortDescription,
            null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))

        ListMetaDataTO completedCartMetadata = new ListMetaDataTO(false, "SHOPPING", LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse = cartDataProvider.getCartResponse(UUID.randomUUID(), guestId, pendingCartResponse.cartId.toString(),
            cartDataProvider.getMetaData(completedCartMetadata, new UserMetaDataTO()))

        def listMetaData = cartDataProvider.getListMetaDataFromCart(pendingCartResponse.metadata)

        mockServer.get({ path -> path.contains("/carts/v4")},_) >> [status: 200, body: []]
        mockServer.post({ path -> path.contains("/carts/v4")},_,_) >>> [[status: 200, body: pendingCartResponse],
                                                                        [status: 200, body: completedCartResponse]]

        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == CreateListNotifyEvent.getEventType()) {
                    def createList = CreateListNotifyEvent.deserialize(data)
                    if ([pendingCartResponse.cartId, completedCartResponse.cartId].contains(createList.listId)) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        def actual = createListService.createList(guestId, listRequestTO).block()
        listId4 = actual.listId

        then:
        actual.listId == pendingCartResponse.cartId
        actual.channel == LIST_CHANNEL.valueOf(pendingCartResponse.cartChannel)
        actual.listTitle == pendingCartResponse.tenantCartName
        actual.shortDescription == pendingCartResponse.tenantCartDescription
        actual.listType == listMetaData.listType
        actual.defaultList == listMetaData.defaultList

        testEventListener.verifyEvents { events ->
            conditions.eventually {
                assert events.size() == 2
                assert events[0].success
                assert events[1].success
            }
        }

        and:
        def guestList = guestListRepository.find(guestId).block()
        guestList.listSortOrder == listId4.toString() + "," + listId3.toString() + "," + listId2.toString() + "," + listId1.toString()
    }

    def "Guest sorts list order by moving listId2 to position above of listId3"() {
        given:
        def editListRequest = new EditListSortOrderRequestTO(listId2, listId3, Direction.ABOVE)

        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == EditListSortOrderActionEvent.getEventType()) {
                    def editListSortOrder = EditListSortOrderActionEvent.deserialize(data)
                    if (editListSortOrder.editListSortOrderRequestTO == editListRequest) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        def actual = editListSortOrderService.editListPosition(guestId, editListRequest).block()

        then:
        actual

        testEventListener.verifyEvents { events ->
            conditions.eventually {
                assert events.size() == 1
                assert events[0].success
            }
        }

        and:
        def guestList = guestListRepository.find(guestId).block()
        guestList.listSortOrder == listId4.toString() + "," + listId2.toString() + "," + listId3.toString() + "," + listId1.toString()
    }

    def "Guest sorts list order by moving listId1 to position above of listId4"() {
        given:
        def editListRequest = new EditListSortOrderRequestTO(listId1, listId4, Direction.ABOVE)

        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == EditListSortOrderActionEvent.getEventType()) {
                    def editListSortOrder = EditListSortOrderActionEvent.deserialize(data)
                    if (editListSortOrder.editListSortOrderRequestTO == editListRequest) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        def actual = editListSortOrderService.editListPosition(guestId, editListRequest).block()

        then:
        actual

        testEventListener.verifyEvents { events ->
            conditions.eventually {
                assert events.size() == 1
                assert events[0].success
            }
        }

        and:
        def guestList = guestListRepository.find(guestId).block()
        guestList.listSortOrder == listId1.toString() + "," + listId4.toString() + "," + listId2.toString() + "," + listId3.toString()
    }

    def "Guest sorts list order by moving listId2 to position below of listId3"() {
        given:
        def editListRequest = new EditListSortOrderRequestTO(listId2, listId3, Direction.BELOW)

        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == EditListSortOrderActionEvent.getEventType()) {
                    def editListSortOrder = EditListSortOrderActionEvent.deserialize(data)
                    if (editListSortOrder.editListSortOrderRequestTO == editListRequest) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        def actual = editListSortOrderService.editListPosition(guestId, editListRequest).block()

        then:
        actual

        testEventListener.verifyEvents { events ->
            conditions.eventually {
                assert events.size() == 1
                assert events[0].success
            }
        }

        and:
        def guestList = guestListRepository.find(guestId).block()
        guestList.listSortOrder == listId1.toString() + "," + listId4.toString() + "," + listId3.toString() + "," + listId2.toString()
    }

    def "Guest sorts list order by moving listId2 to position below of listId1"() {
        given:
        def editListRequest = new EditListSortOrderRequestTO(listId2, listId1, Direction.BELOW)

        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == EditListSortOrderActionEvent.getEventType()) {
                    def editListSortOrder = EditListSortOrderActionEvent.deserialize(data)
                    if (editListSortOrder.editListSortOrderRequestTO == editListRequest) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        def actual = editListSortOrderService.editListPosition(guestId, editListRequest).block()

        then:
        actual

        testEventListener.verifyEvents { events ->
            conditions.eventually {
                assert events.size() == 1
                assert events[0].success
            }
        }

        and:
        def guestList = guestListRepository.find(guestId).block()
        guestList.listSortOrder == listId1.toString() + "," + listId2.toString() + "," + listId4.toString() + "," + listId3.toString()
    }

    def "Guest gets all lists sorting by list position and ascending"() {
        given:
        ListMetaDataTO metadata = new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.PENDING)
        def cart1 = cartDataProvider.getCartResponse(listId1, guestId, LIST_CHANNEL.WEB, CartType.LIST, "list 1", "list 1", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))
        def cartContents1 = cartDataProvider.getCartContentsResponse(listId1, 2)
        def cart2 = cartDataProvider.getCartResponse(listId2, guestId, LIST_CHANNEL.WEB, CartType.LIST, "list 1", "list 1", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))
        def cartContents2 = cartDataProvider.getCartContentsResponse(listId2, 2)
        def cart3 = cartDataProvider.getCartResponse(listId3, guestId, LIST_CHANNEL.WEB, CartType.LIST, "list 1", "list 1", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))
        def cartContents3 = cartDataProvider.getCartContentsResponse(listId3, 2)
        def cart4 = cartDataProvider.getCartResponse(listId4, guestId, LIST_CHANNEL.WEB, CartType.LIST, "list 1", "list 1", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))
        def cartContents4 = cartDataProvider.getCartContentsResponse(listId4, 2)
        mockServer.get({ path -> path.contains("/carts/v4/?guest_id=")}, _) >> [status: 200, body: [cart1,cart2,cart3,cart4]]
        mockServer.get({ path -> path.contains("/carts/v4/cart_contents/" + listId1)}, _) >> [status: 200, body: cartContents1]
        mockServer.get({ path -> path.contains("/carts/v4/cart_contents/" + listId2)}, _) >> [status: 200, body: cartContents2]
        mockServer.get({ path -> path.contains("/carts/v4/cart_contents/" + listId3)}, _) >> [status: 200, body: cartContents3]
        mockServer.get({ path -> path.contains("/carts/v4/cart_contents/" + listId4)}, _) >> [status: 200, body: cartContents4]

        when:
        def actual = getAllListService.getAllListsForUser(guestId, ListSortFieldGroup.LIST_POSITION, null).block()

        then:
        actual[0].listId == listId1
        actual[1].listId == listId2
        actual[2].listId == listId4
        actual[3].listId == listId3
    }

    def "Guest delete listId2 from the list"() {
        given:
        ListMetaDataTO metadata = new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.PENDING)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId2, guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "list title1", "short",
            null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))

        ListMetaDataTO completedCartMetadata = new ListMetaDataTO(false, "SHOPPING", LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse = cartDataProvider.getCartResponse(UUID.randomUUID(), guestId,
            pendingCartResponse.cartId.toString(), cartDataProvider.getMetaData(completedCartMetadata, new UserMetaDataTO()))

        testEventListener.preDispatchLambda = new PreDispatchLambda() {
            @Override
            boolean onPreDispatchConsumerEvent(@NotNull EventHeaders eventHeaders, @NotNull byte[] data, boolean isPoisonEvent) {
                if (eventHeaders.eventType == DeleteListNotifyEvent.getEventType()) {
                    def deleteList = DeleteListNotifyEvent.deserialize(data)
                    if ([pendingCartResponse.cartId, completedCartResponse.cartId].contains(deleteList.listId)) {
                        return true
                    }
                }
                return false
            }
        }

        when:
        def actual = deleteListService.deleteList(guestId, listId2).block()

        then:
        actual.listId == listId2

        1 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=")}, _) >> [status: 200, body: [completedCartResponse, pendingCartResponse]]
        2 * mockServer.post({ path -> path.contains("/carts/v4/cart_deletes")},_, _) >>> [[status: 200, body: completedCartResponse],
                                                                                          [status: 200, body: pendingCartResponse]]
        testEventListener.verifyEvents { events ->
            conditions.eventually {
                assert events.size() == 2
                assert events[0].success
                assert events[1].success
            }
        }

        and:
        def guestList = guestListRepository.find(guestId).block()
        guestList.listSortOrder == listId1.toString() + "," + listId4.toString() + "," + listId3.toString()
    }
}
