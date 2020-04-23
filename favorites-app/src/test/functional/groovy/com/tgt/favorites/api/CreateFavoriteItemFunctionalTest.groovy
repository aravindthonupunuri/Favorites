package com.tgt.favorites.api

import com.tgt.favorites.util.FavoriteConstants
import com.tgt.favorites.transport.FavoriteListItemPostResponseTO
import com.tgt.favorites.util.BaseFunctionalTest
import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.lib.api.transport.ListItemMetaDataTO
import com.tgt.lists.lib.api.transport.ListMetaDataTO
import com.tgt.lists.lib.api.transport.UserItemMetaDataTO
import com.tgt.lists.lib.api.transport.UserMetaDataTO
import com.tgt.lists.lib.api.util.ItemType
import com.tgt.lists.lib.api.util.LIST_CHANNEL
import com.tgt.lists.lib.api.util.LIST_ITEM_STATE
import com.tgt.lists.lib.api.util.LIST_STATUS
import com.tgt.lists.msgbus.ListsMessageBusProducer
import com.tgt.lists.msgbus.event.EventLifecycleNotificationProvider
import com.tgt.shoppinglist.util.HelperKt
import groovy.json.JsonOutput
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.test.annotation.MicronautTest
import io.micronaut.test.annotation.MockBean
import spock.lang.Shared
import javax.inject.Inject

import static com.tgt.favorites.util.DataProvider.getCartURI
import static com.tgt.favorites.util.DataProvider.getCheckHeaders
import static com.tgt.favorites.util.DataProvider.getHeaders

@MicronautTest
class CreateFavoriteItemFunctionalTest extends BaseFunctionalTest {

    @Shared
    @Inject
    EventLifecycleNotificationProvider eventNotificationsProvider

    @MockBean(ListsMessageBusProducer.class)
    ListsMessageBusProducer createMockListsMessageBusProducer() {
        return newMockMsgbusKafkaProducerClient(eventNotificationsProvider)
    }

    def "test create default item for favourites default list integration"() {
        given:
        def uri = FavoriteConstants.BASEPATH + "/list_items"
        String guestId = "1234"
        def listId = UUID.randomUUID()
        def itemId = UUID.randomUUID()
        def listItemRequest =
            [
                "item_type"  : ItemType.TCIN,
                "channel"    : LIST_CHANNEL.WEB,
                "tcin"       : "53692059",
                "item_ref_id": "222",
                "location_id": 1375L
            ]
        def tenantRefId1 = HelperKt.populateItemRefId(ItemType.TCIN, "222", null, null)
        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId,
            LIST_CHANNEL.MOBILE, CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(new ListMetaDataTO(true, LIST_STATUS.PENDING), new UserMetaDataTO()))

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), tenantRefId1, "1234",
            "title1", 3, "note\nnote", 10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, itemId, tenantRefId1, listItemRequest.tcin,
            "itemTitle", "itemNote", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))

        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.GENERIC_ITEM, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), tenantRefId1, null,
            "coffee", 1, "itemNote", 10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)
        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def pendingCartContentsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])
        def addcartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse])
        when:
        HttpResponse<FavoriteListItemPostResponseTO> listItemResponse = client.toBlocking()
            .exchange(HttpRequest.POST(uri, JsonOutput.toJson(listItemRequest)).headers(getHeaders(guestId)), FavoriteListItemPostResponseTO)
        def actualStatus = listItemResponse.status()
        def actual = listItemResponse.body()

        then:
        actualStatus == HttpStatus.CREATED
        actual.listItemId == cartItemResponse.cartItemId
        actual.tcin == cartItemResponse.getTcin()
        actual.itemTitle == cartItemResponse.tenantItemName

        1 * mockServer.get({ path -> path.contains(getCartURI(guestId)) }, { headers -> checkHeaders(headers) }) >> [status: 200, body: [pendingCartResponse]]
        2 * mockServer.get({ path -> path.contains("/carts/v4/cart_contents/" + listId) }, _) >> [status: 200, body: pendingCartContentsResponse]
        1 * mockServer.post({ path -> path.contains("/carts/v4/multi_cart_items")},_,{ headers -> checkHeaders(headers) }) >> [status: 200, body: addcartContentsResponse]
        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }


    def "test create default item for favourites if it does not have default list"() {
        given:
        def uri = FavoriteConstants.BASEPATH + "/list_items"
        String guestId = "1234"
        def listId = UUID.randomUUID()
        def listId1 = UUID.randomUUID()
        def itemId = UUID.randomUUID()
        def listItemRequest =
            [
                "item_type"  : ItemType.TCIN,
                "channel"    : LIST_CHANNEL.WEB,
                "tcin"       : "53692059",
                "item_ref_id": "222",
                "location_id": 1375L
            ]

        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId,
            LIST_CHANNEL.MOBILE, CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(new ListMetaDataTO(true, LIST_STATUS.PENDING), new UserMetaDataTO()))

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), "222", "1234",
            "title1", 3, "note\nnote", 10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, itemId, "222", listItemRequest.tcin,
            "itemTitle", "itemNote", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))

        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.GENERIC_ITEM, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), "222", null,
            "coffee", 1, "itemNote", 10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        def pendingCartContentsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])

        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)

        def cartResponse = cartDataProvider.getCartResponse(listId1, guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))
        def addcartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse])
        when:
        HttpResponse<FavoriteListItemPostResponseTO> listItemResponse = client.toBlocking()
            .exchange(HttpRequest.POST(uri, JsonOutput.toJson(listItemRequest)).headers(getHeaders(guestId)), FavoriteListItemPostResponseTO)
        def actualStatus = listItemResponse.status()
        def actual = listItemResponse.body()

        then:
        actualStatus == HttpStatus.CREATED
        actual.listItemId == cartItemResponse.cartItemId
        actual.tcin == cartItemResponse.getTcin()
        actual.itemTitle == cartItemResponse.tenantItemName

        2 * mockServer.get({ path -> path.contains(getCartURI(guestId)) }, { headers -> checkHeaders(headers) }) >> [status: 200, body: []]
        0 * mockServer.get({ path -> path.contains("/carts/v4/cart_contents/" + listId) }, _) >> [status: 200, body: pendingCartContentsResponse]
        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_contents/" + listId1) }, _) >> [status: 200, body: pendingCartContentsResponse]
        1 * mockServer.post({ path -> path.contains("/carts/v4/multi_cart_items")},_,{ headers -> checkHeaders(headers) }) >> [status: 200, body: addcartContentsResponse]
        1 * mockServer.post({ path -> path.contains("/carts/v4/")},_,{ headers -> checkHeaders(headers) }) >> [status: 200, body: cartResponse]
        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }
}
