package com.tgt.shoppinglist.api

import com.tgt.lists.lib.api.transport.ListItemMetaDataTO
import com.tgt.lists.lib.api.transport.ListItemResponseTO
import com.tgt.lists.lib.api.transport.UserItemMetaDataTO
import com.tgt.lists.lib.api.util.Constants
import com.tgt.lists.lib.api.util.ItemType
import com.tgt.lists.lib.api.util.LIST_ITEM_STATE
import com.tgt.lists.msgbus.ListsMessageBusProducer
import com.tgt.lists.msgbus.event.EventLifecycleNotificationProvider
import com.tgt.shoppinglist.util.BaseFunctionalTest
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MicronautTest
import io.micronaut.test.annotation.MockBean
import spock.lang.Shared

import javax.inject.Inject

import static com.tgt.shoppinglist.util.DataProvider.*

@MicronautTest
class GetListItemFunctionalTest extends BaseFunctionalTest {

    String guestId = "1234"

    @Shared
    @Inject
    EventLifecycleNotificationProvider eventNotificationsProvider

    @MockBean(ListsMessageBusProducer.class)
    ListsMessageBusProducer createMockListsMessageBusProducer() {
        return newMockMsgbusKafkaProducerClient(eventNotificationsProvider)
    }

    def "get list item integrity test"() {
        def cartId = UUID.randomUUID()
        def cartItemId = "aaaaaaaa-1111-bbbb-2222-cccccccccccc"
        def uri = Constants.LISTS_BASEPATH + "/" + cartId + "/list_items/" + cartItemId + "?location_id=1375"
        def cartUri = "/carts/v4/cart_items/aaaaaaaa-1111-bbbb-2222-cccccccccccc?cart_id=" + cartId
        def cartwheelItemUri = "/ssa/cwlservice/api/v16/items/offers?sort=relevance_desc&storeId=1375&tcins=1234"
        def sicUri = "/stores"

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(cartId, UUID.fromString(cartItemId), "1234",
            "some title", "some note", 1, 10, 10, "Stand Alone", "READY_FOR_LAUNCH",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))
        def cartResponse = cartDataProvider.getCartResponse(cartId, guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        def itemLocation1 = sicDataProvider.getItemLocations(["1234"])
        def cartWheelItemOfferList = cartWheelDataProvider.getCartWheelItemOffers(2)
        def promoOfferCount = cartItemResponse.getEligibleDiscounts().size()
        def cartWheelOfferCount = cartWheelItemOfferList.size()
        def listItem1MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse.metadata)

        when:
        HttpResponse<ListItemResponseTO> listItemResponse = client.toBlocking()
            .exchange(HttpRequest.GET(uri).headers(getHeaders(guestId)), ListItemResponseTO)
        def actualStatus = listItemResponse.status()
        def actual = listItemResponse.body()

        then:
        actualStatus == HttpStatus.OK

        actual.listItemId == cartItemResponse.cartItemId
        actual.tcin == cartItemResponse.tcin
        actual.itemTitle == cartItemResponse.tenantItemName
        actual.itemNote == cartItemResponse.notes
        actual.price == cartItemResponse.price
        actual.listPrice == cartItemResponse.listPrice
        actual.images == cartItemResponse.images
        actual.itemType == listItem1MetaData.itemType
        actual.itemExpiration == listItem1MetaData.itemExpiration
        actual.offerCount == promoOfferCount + cartWheelOfferCount

        1 * mockServer.get({ path -> path.contains(getCartContentURI(cartId))}, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartContentsResponse]
        1 * mockServer.get({ path -> path.contains(cartUri) }, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartItemResponse]
        1 * mockServer.get({ path -> path.contains(sicUri) }, { headers -> checkHeaders(headers) }) >> [status: 200, body: itemLocation1]
        1 * mockServer.get({ path -> path.contains(cartwheelItemUri) }, { headers -> checkCartWheelApiHeaders(headers) }) >> [status: 200, body: cartWheelItemOfferList]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test get list item returns 206 when cartwheel request fails"() {
        def cartId = UUID.randomUUID()
        def cartItemId = "aaaaaaaa-1111-bbbb-2222-cccccccccccc"
        def uri = Constants.LISTS_BASEPATH + "/" + cartId + "/list_items/" + cartItemId + "?location_id=1375"
        def cartUri = "/carts/v4/cart_items/aaaaaaaa-1111-bbbb-2222-cccccccccccc?cart_id=" + cartId
        def cartwheelItemUri = "/ssa/cwlservice/api/v16/items/offers?sort=relevance_desc&storeId=1375&tcins=1234"
        def sicUri = "/stores"

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(cartId, UUID.fromString(cartItemId), "1234",
            "some title", "some note", 1, 10, 10, "Stand Alone", "READY_FOR_LAUNCH",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))
        def cartResponse = cartDataProvider.getCartResponse(cartId, guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        def itemLocation1 = sicDataProvider.getItemLocations(["1234"])

        when:
        HttpResponse<ListItemResponseTO> listItemResponse = client.toBlocking()
            .exchange(HttpRequest.GET(uri).headers(getHeaders(guestId)), ListItemResponseTO)
        def actualStatus = listItemResponse.status()

        then:
        actualStatus == HttpStatus.PARTIAL_CONTENT

        1 * mockServer.get({ path -> path.contains(getCartContentURI(cartId))}, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartContentsResponse]
        1 * mockServer.get({ path -> path.contains(cartUri) }, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartItemResponse]
        1 * mockServer.get({ path -> path.contains(sicUri) }, { headers -> checkHeaders(headers) }) >> [status: 200, body: itemLocation1]
        1 * mockServer.get({ path -> path.contains(cartwheelItemUri) }, { headers -> checkCartWheelApiHeaders(headers) }) >> [status: 400, body: '{"message": "400 bad request"}']

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test get list item returns 206 when store item coordinates request fails"() {
        def cartId = UUID.randomUUID()
        def cartItemId = "aaaaaaaa-1111-bbbb-2222-cccccccccccc"
        def uri = Constants.LISTS_BASEPATH + "/" + cartId + "/list_items/" + cartItemId + "?location_id=1375"
        def cartUri = "/carts/v4/cart_items/aaaaaaaa-1111-bbbb-2222-cccccccccccc?cart_id=" + cartId
        def cartwheelItemUri = "/ssa/cwlservice/api/v16/items/offers?sort=relevance_desc&storeId=1375&tcins=1234"

        def sicUri = "/stores"

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)

        def cartItemResponse = cartDataProvider.getCartItemResponse(cartId, UUID.fromString(cartItemId), "1234",
            "some title", "some note", 1, 10, 10, "Stand Alone", "READY_FOR_LAUNCH",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))
        def cartResponse = cartDataProvider.getCartResponse(cartId, guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)
        def cartWheelItemOfferList = cartWheelDataProvider.getCartWheelItemOffers(2)

        when:
        HttpResponse<ListItemResponseTO> listItemResponse = client.toBlocking()
            .exchange(HttpRequest.GET(uri).headers(getHeaders(guestId)), ListItemResponseTO)
        def actualStatus = listItemResponse.status()

        then:
        actualStatus == HttpStatus.PARTIAL_CONTENT

        1 * mockServer.get({ path -> path.contains(getCartContentURI(cartId))}, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartContentsResponse]
        1 * mockServer.get({ path -> path.contains(cartUri) }, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartItemResponse]
        1 * mockServer.get({ path -> path.contains(sicUri) }, { headers -> checkHeaders(headers) }) >> [status: 400, body: '{"message": "400 bad request"}']
        1 * mockServer.get({ path -> path.contains(cartwheelItemUri) }, { headers -> checkCartWheelApiHeaders(headers) }) >> [status: 200, body: cartWheelItemOfferList]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "get get list item test when location_id is not passed"() {
        def cartId = UUID.randomUUID()
        def cartItemId = "aaaaaaaa-1111-bbbb-2222-cccccccccccc"
        def uri = Constants.LISTS_BASEPATH + "/" + cartId + "/list_items/" + cartItemId
        def cartUri = "/carts/v4/cart_items/aaaaaaaa-1111-bbbb-2222-cccccccccccc?cart_id=" + cartId
        def cartwheelItemUri = "/ssa/cwlservice/api/v16/items/offers?sort=relevance_desc&storeId=1375&tcins=1234"

        def sicUri = "/stores"

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)

        def cartItemResponse = cartDataProvider.getCartItemResponse(cartId, UUID.fromString(cartItemId), "1234",
            "some title", "some note", 1, 10, 10, "Stand Alone", "READY_FOR_LAUNCH",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))
        def cartResponse = cartDataProvider.getCartResponse(cartId, guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)
        def itemLocation1 = sicDataProvider.getItemLocations(["1234"])
        def cartWheelItemOfferList = cartWheelDataProvider.getCartWheelItemOffers(2)

        when:
        HttpResponse<ListItemResponseTO> listItemResponse = client.toBlocking()
            .exchange(HttpRequest.GET(uri).headers(getHeaders(guestId)), ListItemResponseTO)

        then:
        def error = thrown(HttpClientResponseException)
        error.status == HttpStatus.BAD_REQUEST

        1 * mockServer.get({ path -> path.contains(getCartContentURI(cartId))}, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartContentsResponse]
        0 * mockServer.get({ path -> path.contains(cartUri) }, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartItemResponse]
        0 * mockServer.get({ path -> path.contains(sicUri) }, { headers -> checkHeaders(headers) }) >> [status: 200, body: itemLocation1]
        0 * mockServer.get({ path -> path.contains(cartwheelItemUri) }, { headers -> checkCartWheelApiHeaders(headers) }) >> [status: 200, body: cartWheelItemOfferList]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }
}


