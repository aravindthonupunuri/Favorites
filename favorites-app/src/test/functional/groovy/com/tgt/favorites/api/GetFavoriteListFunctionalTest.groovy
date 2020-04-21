package com.tgt.favorites.api

import com.tgt.favorites.transport.FavouritesListResponseTO
import com.tgt.favorites.util.FavoriteConstants
import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.lib.api.transport.*
import com.tgt.lists.lib.api.util.*
import com.tgt.lists.msgbus.ListsMessageBusProducer
import com.tgt.lists.msgbus.event.EventLifecycleNotificationProvider
import com.tgt.favorites.util.BaseFunctionalTest
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.test.annotation.MicronautTest
import io.micronaut.test.annotation.MockBean
import spock.lang.Shared

import javax.inject.Inject

import static com.tgt.favorites.util.DataProvider.*

@MicronautTest
class GetFavoriteListFunctionalTest extends BaseFunctionalTest {

    String guestId = "1234"

    @Shared
    @Inject
    EventLifecycleNotificationProvider eventNotificationsProvider

    @MockBean(ListsMessageBusProducer.class)
    ListsMessageBusProducer createMockListsMessageBusProducer() {
        return newMockMsgbusKafkaProducerClient(eventNotificationsProvider)
    }

    def "test get list integrity"() {
        given:
        def cartId = "fe9c3360-b44a-11e9-987d-03d970ca1c28"
        def uri = FavoriteConstants.BASEPATH + "/" + cartId + "?location_id=1375"
        def cartUri = "/carts/v4/cart_contents/" + cartId

        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.GENERIC_ITEM, LIST_ITEM_STATE.PENDING)

        def cartResponse = cartDataProvider.getCartResponse(UUID.fromString(cartId), guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))

        def cartItemResponse1 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), "1234", "1234",
            null, "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))
        def cartItemResponse2 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), "1234", null,
            "coffee", "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()))
        def cartItemResponse3 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), "1234", "2345",
            null, "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))
        def cartItemResponse4 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), "1234", "3456",
            "coffee", "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))

        Map response = ["cart" : cartResponse, "cart_items" : [cartItemResponse1, cartItemResponse2, cartItemResponse3, cartItemResponse4]]

        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)

        when:
        HttpResponse<FavouritesListResponseTO> listResponse = client.toBlocking()
            .exchange(HttpRequest.GET(uri).headers(getHeaders(guestId)), FavouritesListResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.OK

        actual.listId == cartResponse.cartId
        actual.channel == LIST_CHANNEL.valueOf(cartResponse.cartChannel)
        actual.listTitle == cartResponse.tenantCartName
        actual.shortDescription == cartResponse.tenantCartDescription
        actual.listType == "FAVORITES"
        actual.defaultList == listMetaData.defaultList

        2 * mockServer.get({ path -> path.contains(cartUri) }, _) >> [status: 200, body: response] //TODO: check why two calls here

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test get list for a given page value"() {
        given:
        def cartId = "fe9c3360-b44a-11e9-987d-03d970ca1c28"
        def uri = FavoriteConstants.BASEPATH + "/" + cartId + "?location_id=1375&sort_field=ITEM_TITTLE&sort_order=ASCENDING" + "&page=2"
        def cartUri = "/carts/v4/cart_contents/" + cartId

        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.GENERIC_ITEM, LIST_ITEM_STATE.PENDING)

        def cartResponse = cartDataProvider.getCartResponse(UUID.fromString(cartId), guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))

        def cartItemResponse1 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), "1234", "1234",
            "A", "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))
        def cartItemResponse2 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), "1234", null,
            "B", "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()))
        def cartItemResponse3 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), "1234", "2345",
            "C", "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))
        def cartItemResponse4 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), "1234", "3456",
            "D", "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))

        Map response = ["cart" : cartResponse, "cart_items" : [cartItemResponse1, cartItemResponse2, cartItemResponse3, cartItemResponse4]]

        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)

        when:
        HttpResponse<FavouritesListResponseTO> listResponse = client.toBlocking()
            .exchange(HttpRequest.GET(uri).headers(getHeaders(guestId)), FavouritesListResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.OK

        actual.listId == cartResponse.cartId
        actual.channel == LIST_CHANNEL.valueOf(cartResponse.cartChannel)
        actual.listTitle == cartResponse.tenantCartName
        actual.shortDescription == cartResponse.tenantCartDescription
        actual.listType == "FAVORITES"
        actual.defaultList == listMetaData.defaultList

        2 * mockServer.get({ path -> path.contains(cartUri) }, _) >> [status: 200, body: response] //TODO: check why two calls here

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test get list integrity with sortedFieldGroups and sortOrder"() {
        given:
        def cartId = "fe9c3360-b44a-11e9-987d-03d970ca1c28"
        def uri = FavoriteConstants.BASEPATH + "/" + cartId + "?location_id=1375&sort_field=ITEM_TITTLE&sort_order=ASCENDING"
        def cartUri = "/carts/v4/cart_contents/" + cartId

        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.GENERIC_ITEM, LIST_ITEM_STATE.PENDING)

        def cartResponse = cartDataProvider.getCartResponse(UUID.fromString(cartId), guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))

        def cartItemResponse1 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), "1234", "1234",
            "banana", "some note",1,  10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))
        def cartItemResponse2 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), "1234", null,
            "coffee", "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()))
        Map response = ["cart" : cartResponse, "cart_items" : [cartItemResponse1, cartItemResponse2]]

        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)

        when:
        HttpResponse<FavouritesListResponseTO> listResponse = client.toBlocking()
            .exchange(HttpRequest.GET(uri).headers(getHeaders(guestId)), FavouritesListResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.OK

        actual.listId == cartResponse.cartId
        actual.channel == LIST_CHANNEL.valueOf(cartResponse.cartChannel)
        actual.listTitle == cartResponse.tenantCartName
        actual.shortDescription == cartResponse.tenantCartDescription
        actual.listType == "FAVORITES"
        actual.defaultList == listMetaData.defaultList

        2 * mockServer.get({ path -> path.contains(cartUri) }, _) >> [status: 200, body: response] //TODO: check why two calls here

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }
}
