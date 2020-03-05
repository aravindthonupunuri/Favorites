package com.tgt.favorites.api

import com.tgt.favorites.util.BaseFunctionalTest
import com.tgt.lists.cart.transport.CartResponse
import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.lib.api.transport.*
import com.tgt.lists.lib.api.util.*
import com.tgt.lists.msgbus.ListsMessageBusProducer
import com.tgt.lists.msgbus.event.EventLifecycleNotificationProvider
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.test.annotation.MicronautTest
import io.micronaut.test.annotation.MockBean
import spock.lang.Ignore
import spock.lang.Shared

import javax.inject.Inject

import static com.tgt.favorites.util.DataProvider.*

@MicronautTest
@Ignore
class GetDefaultListFunctionalTest extends BaseFunctionalTest {

    String guestId = "1234"

    @Shared
    @Inject
    EventLifecycleNotificationProvider eventNotificationsProvider

    @MockBean(ListsMessageBusProducer.class)
    ListsMessageBusProducer createMockListsMessageBusProducer() {
        return newMockMsgbusKafkaProducerClient(eventNotificationsProvider)
    }

    def "test get default list integration with no lists in v4, checking for default list in v2 and migrate if present"() {
        given:
        def uri = Constants.LISTS_BASEPATH + "/default_list?location_id=1375"
        String guestId = "1234"
        def listId = UUID.randomUUID()
        UUID completedCartId = UUID.randomUUID()

        def listGetAllResponseTOV2 = listV2DataProvider.getAllListsV2(guestId, listV2DataProvider.getListsV2())

        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId,
            LIST_CHANNEL.MOBILE, CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.PENDING), new UserMetaDataTO()))

        def completedCartResponse = cartDataProvider.getCartResponse(completedCartId,
            guestId, listId.toString(), cartDataProvider.getMetaData(new ListMetaDataTO(false, "SHOPPING", LIST_STATUS.COMPLETED), new UserMetaDataTO()))

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), "1234",
            "title1", 3, "note\nnote",10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.GENERIC_ITEM, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), null,
            "coffee", 1, "itemNote",10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        def pendingCartContentsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])

        List<CartResponse> migratedCartResponseList = [pendingCartResponse, completedCartResponse]

        when:
        HttpResponse<ListResponseTO> listResponse = client.toBlocking()
            .exchange(HttpRequest.GET(uri).headers(getHeaders(guestId)), ListResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.OK

        actual.listId == pendingCartResponse.cartId
        actual.channel == LIST_CHANNEL.valueOf(pendingCartResponse.cartChannel)
        actual.listTitle == pendingCartResponse.tenantCartName
        actual.shortDescription == pendingCartResponse.tenantCartDescription
        actual.defaultList
        actual.maxPendingItemsCount == 3

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 2
        pendingItems[0].listItemId == pendingCartItemResponse1.cartItemId
        pendingItems[0].tcin == pendingCartItemResponse1.tcin
        pendingItems[0].itemTitle == pendingCartItemResponse1.tenantItemName
        pendingItems[0].itemNote == pendingCartItemResponse1.notes
        pendingItems[0].price == pendingCartItemResponse1.price
        pendingItems[0].listPrice == pendingCartItemResponse1.listPrice
        pendingItems[0].images == pendingCartItemResponse1.images

        pendingItems[1].listItemId == pendingCartItemResponse2.cartItemId
        pendingItems[1].tcin == pendingCartItemResponse2.tcin
        pendingItems[1].itemTitle == pendingCartItemResponse2.tenantItemName
        pendingItems[1].itemNote == pendingCartItemResponse2.notes
        pendingItems[1].price == pendingCartItemResponse2.price
        pendingItems[1].listPrice == pendingCartItemResponse2.listPrice
        pendingItems[1].images == pendingCartItemResponse2.images

        def completedItems = actual.completedListItems
        completedItems == null

        3 * mockServer.get({ path -> path.contains(getCartURI(guestId))}, { headers -> checkHeaders(headers) }) >> [status: 200, body: []] // call to get all lists for the user
        1 * mockServer.get({ path -> path.contains("/lists/v2/")}, { headers -> checkHeaders(headers) }) >> [status: 200, body: listGetAllResponseTOV2]  // lists v2 call to get all lists for the user
        1 * mockServer.get({ path -> path.contains(getCartURI(guestId))},{ headers -> checkHeaders(headers) }) >> [status: 200, body: []]  // default list manager check while creating cart in v4
        1 * mockServer.post({ path -> path.contains("/carts/v4/")},_,{ headers -> checkHeaders(headers) }) >> [status: 200, body: pendingCartResponse]   // create pending cart in v4
        1 * mockServer.post({ path -> path.contains("/carts/v4/")},_,{ headers -> checkHeaders(headers) }) >> [status: 200, body: completedCartResponse]   // create completed cart in v4
        1 * mockServer.post({ path -> path.contains("/carts/v4/multi_cart_items")},_,{ headers -> checkHeaders(headers) }) >> [status: 200, body: pendingCartContentsResponse]   // add multiple cart items call in v4
        1 * mockServer.get({ path -> path.contains(getCartURI(guestId))}, { headers -> checkHeaders(headers) }) >> [status: 200, body: migratedCartResponseList] // search carts call after the list in v2 is migrated to v4
        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_contents/" + listId) }, _) >> [status: 200, body: pendingCartContentsResponse] // cart contents call  to get pending items

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test get default list integration with no lists in v4 and v2"() {
        given:
        def uri = Constants.LISTS_BASEPATH + "/default_list?location_id=1375"
        String guestId = "1234"
        def listGetAllResponseTOV2 = listV2DataProvider.getAllListsV2(guestId, [])

        when:
        HttpResponse<ListResponseTO> listResponse = client.toBlocking()
            .exchange(HttpRequest.GET(uri).headers(getHeaders(guestId)), ListResponseTO)
        def actualStatus = listResponse.status()

        then:
        actualStatus == HttpStatus.NO_CONTENT

        1 * mockServer.get({ path -> path.contains(getCartURI(guestId))}, { headers -> checkHeaders(headers) }) >> [status: 200, body: []] // call to get all lists for the user
        1 * mockServer.get({ path -> path.contains("/lists/v2/")}, { headers -> checkHeaders(headers) }) >> [status: 200, body: listGetAllResponseTOV2]  // lists v2 call to get all lists for the user

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }
}
