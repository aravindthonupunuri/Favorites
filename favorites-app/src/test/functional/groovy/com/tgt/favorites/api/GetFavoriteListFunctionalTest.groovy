package com.tgt.favorites.api

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
        def listItem1MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse1.metadata)
        def listItem2MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse2.metadata)

        when:
        HttpResponse<ListResponseTO> listResponse = client.toBlocking()
            .exchange(HttpRequest.GET(uri).headers(getHeaders(guestId)), ListResponseTO)
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

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 4
        pendingItems[0].listItemId == cartItemResponse1.cartItemId
        pendingItems[0].tcin == cartItemResponse1.tcin
        pendingItems[0].itemTitle == cartItemResponse1.tenantItemName
        pendingItems[0].itemNote == cartItemResponse1.notes
        pendingItems[0].price == cartItemResponse1.price
        pendingItems[0].listPrice == cartItemResponse1.listPrice
        pendingItems[0].images == cartItemResponse1.images
        pendingItems[0].itemType == listItem1MetaData.itemType

        pendingItems[1].listItemId == cartItemResponse2.cartItemId
        pendingItems[1].tcin == cartItemResponse2.tcin
        pendingItems[1].itemTitle == cartItemResponse2.tenantItemName
        pendingItems[1].itemNote == cartItemResponse2.notes
        pendingItems[1].price == cartItemResponse2.price
        pendingItems[1].listPrice == cartItemResponse2.listPrice
        pendingItems[1].images == cartItemResponse2.images
        pendingItems[1].itemType == listItem2MetaData.itemType

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
        def listItem3MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse3.metadata)
        def listItem4MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse4.metadata)

        when:
        HttpResponse<ListResponseTO> listResponse = client.toBlocking()
            .exchange(HttpRequest.GET(uri).headers(getHeaders(guestId)), ListResponseTO)
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

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 2
        pendingItems[0].listItemId == cartItemResponse3.cartItemId
        pendingItems[0].tcin == cartItemResponse3.tcin
        pendingItems[0].itemTitle == cartItemResponse3.tenantItemName
        pendingItems[0].itemNote == cartItemResponse3.notes
        pendingItems[0].price == cartItemResponse3.price
        pendingItems[0].listPrice == cartItemResponse3.listPrice
        pendingItems[0].images == cartItemResponse3.images
        pendingItems[0].itemType == listItem3MetaData.itemType

        pendingItems[1].listItemId == cartItemResponse4.cartItemId
        pendingItems[1].tcin == cartItemResponse4.tcin
        pendingItems[1].itemTitle == cartItemResponse4.tenantItemName
        pendingItems[1].itemNote == cartItemResponse4.notes
        pendingItems[1].price == cartItemResponse4.price
        pendingItems[1].listPrice == cartItemResponse4.listPrice
        pendingItems[1].images == cartItemResponse4.images
        pendingItems[1].itemType == listItem4MetaData.itemType

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
        def listItem1MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse1.metadata)
        def listItem2MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse2.metadata)

        when:
        HttpResponse<ListResponseTO> listResponse = client.toBlocking()
            .exchange(HttpRequest.GET(uri).headers(getHeaders(guestId)), ListResponseTO)
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
        actual.maxPendingItemsCount == 3

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 2
        pendingItems[0].listItemId == cartItemResponse1.cartItemId
        pendingItems[0].tcin == cartItemResponse1.tcin
        pendingItems[0].itemTitle == cartItemResponse1.tenantItemName
        pendingItems[0].itemNote == cartItemResponse1.notes
        pendingItems[0].price == cartItemResponse1.price
        pendingItems[0].listPrice == cartItemResponse1.listPrice
        pendingItems[0].images == cartItemResponse1.images
        pendingItems[0].itemType == listItem1MetaData.itemType

        pendingItems[1].listItemId == cartItemResponse2.cartItemId
        pendingItems[1].tcin == cartItemResponse2.tcin
        pendingItems[1].itemTitle == cartItemResponse2.tenantItemName
        pendingItems[1].itemNote == cartItemResponse2.notes
        pendingItems[1].price == cartItemResponse2.price
        pendingItems[1].listPrice == cartItemResponse2.listPrice
        pendingItems[1].images == cartItemResponse2.images
        pendingItems[1].itemType == listItem2MetaData.itemType

        2 * mockServer.get({ path -> path.contains(cartUri) }, _) >> [status: 200, body: response] //TODO: check why two calls here

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }
}
