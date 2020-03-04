package com.tgt.shoppinglist.api

import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.lib.api.transport.ListItemDeleteResponseTO
import com.tgt.lists.lib.api.transport.ListItemMetaDataTO
import com.tgt.lists.lib.api.transport.UserItemMetaDataTO
import com.tgt.lists.lib.api.util.*
import com.tgt.shoppinglist.api.util.TestUtilConstants
import com.tgt.shoppinglist.util.BaseKafkaFunctionalTest
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MicronautTest

import static com.tgt.shoppinglist.util.DataProvider.*

@MicronautTest
class DeleteListItemFunctionalTest extends BaseKafkaFunctionalTest {

    String guestId = "1234"

    def "test delete list item from pending cart"() {
        def cartId = UUID.randomUUID()
        def cartItemId = UUID.randomUUID()
        def uri = Constants.LISTS_BASEPATH + "/" + cartId + "/list_items/" + cartItemId
        def cartItemUri = "/carts/v4/cart_items/" + cartItemId + "?cart_id=" + cartId
        def cartItemDeleteResponse = ["cart_id" : cartId, "cart_item_id" : cartItemId]
        def cartResponse = cartDataProvider.getCartResponse(cartId, guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(cartId, cartItemId, "1234",
            "some title", "some note", 1, 10, 10, "Stand Alone", "READY_FOR_LAUNCH",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))

        when:
        HttpResponse<ListItemDeleteResponseTO> listResponse = client.toBlocking().exchange(
            HttpRequest.DELETE(uri).headers(getHeaders(guestId)), ListItemDeleteResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.NO_CONTENT

        1 * mockServer.get({ path -> path.contains(getCartContentURI(cartId))},_) >> [status: 200, body: cartContentsResponse]
        1 * mockServer.get({ path -> path.contains(cartItemUri) }, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartItemResponse]
        1 * mockServer.post({ path -> path.contains("/carts/v4/deleted_cart_items")},_, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartItemDeleteResponse]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test exception deleting list item from pending cart"() {
        def cartId = UUID.randomUUID()
        def cartItemId = UUID.randomUUID()
        def uri = Constants.LISTS_BASEPATH + "/" + cartId + "/list_items/" + cartItemId
        def cartItemUri = "/carts/v4/cart_items/" + cartItemId + "?cart_id=" + cartId
        def cartResponse = cartDataProvider.getCartResponse(cartId, guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(cartId, cartItemId, "1234",
            "some title", "some note", 1, 10, 10, "Stand Alone", "READY_FOR_LAUNCH",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))

        1 * mockServer.get({ path -> path.contains(getCartContentURI(cartId))},_) >> [status: 200, body: cartContentsResponse]
        1 * mockServer.get({ path -> path.contains(cartItemUri) }, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartItemResponse]
        1 * mockServer.post({ path -> path.contains("/carts/v4/deleted_cart_items")},_, { headers -> checkHeaders(headers) }) >> [status: 400, body: '{"message": "bad data"}']

        when:
        client.toBlocking().exchange(HttpRequest.DELETE(uri)
            .headers(getHeaders(guestId)), ListItemDeleteResponseTO)

        then:
        thrown(HttpClientResponseException)

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test delete list item from completed cart"() {
        def cartId = UUID.randomUUID()
        def completedCartId = UUID.randomUUID()
        def cartItemId = UUID.randomUUID()
        def uri = Constants.LISTS_BASEPATH + "/" + cartId + "/list_items/" + cartItemId
        def cartItemUri = "/carts/v4/cart_items/" + cartItemId + "?cart_id=" + cartId
        def completedCartItemUri = "/carts/v4/cart_items/" + cartItemId + "?cart_id=" + completedCartId
        def cartResponse = cartDataProvider.getCartResponse(cartId, guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        def completedCartResponse = cartDataProvider.getCartResponse(completedCartId, "1234",
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])
        def cartLists = [completedCartResponse]

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def cartItemResponse = cartDataProvider.getCartItemResponse(completedCartId, cartItemId, "1234",
            "some title", "some note", 1, 10, 10, "Stand Alone", "READY_FOR_LAUNCH",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))

        def cartItemDeleteResponse = ["cart_id" : completedCartId, "cart_item_id" : cartItemId]

        when:
        HttpResponse<ListItemDeleteResponseTO> listResponse = client.toBlocking().exchange(
            HttpRequest.DELETE(uri).headers(getHeaders(guestId)), ListItemDeleteResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.NO_CONTENT

        1 * mockServer.get({ path -> path.contains(getCartContentURI(cartId))},_) >> [status: 200, body: cartContentsResponse]
        1 * mockServer.get({ path -> path.contains(cartItemUri) }, { headers -> checkHeaders(headers) }) >> [status: 404]
        1 * mockServer.get({ path -> path.contains(completedCartItemUri) }, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartItemResponse]
        1 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=1234&cart_state=PENDING")}, _) >> [status: 200, body: cartLists]
        1 * mockServer.post({ path -> path.contains("/carts/v4/deleted_cart_items")},_, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartItemDeleteResponse]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test exception getting completed cart while deleting list item from completed cart"() {
        def cartId = UUID.randomUUID()
        def cartItemId = UUID.randomUUID()
        def uri = Constants.LISTS_BASEPATH + "/" + cartId + "/list_items/" + cartItemId
        def cartItemUri = "/carts/v4/cart_items/" + cartItemId + "?cart_id=" + cartId
        def cartResponse = cartDataProvider.getCartResponse(cartId, guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        when:
        client.toBlocking().exchange(HttpRequest.DELETE(uri).headers(getHeaders(guestId)), ListItemDeleteResponseTO)

        then:
        thrown(HttpClientResponseException)

        1 * mockServer.get({ path -> path.contains(getCartContentURI(cartId))},_) >> [status: 200, body: cartContentsResponse]
        1 * mockServer.get({ path -> path.contains(cartItemUri) }, { headers -> checkHeaders(headers) }) >> [status: 404]
        1 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=1234&cart_state=PENDING")}, _) >> [status: 400, body: '{"message": "bad data"}']
        0 * mockServer.post({ path -> path.contains("/carts/v4/deleted_cart_items")},_, { headers -> checkHeaders(headers) }) >> [status: 404, body: null]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test exception deleting list item from completed cart"() {
        def cartId = UUID.randomUUID()
        def completedCartId = UUID.randomUUID()
        def cartItemId = UUID.randomUUID()
        def uri = Constants.LISTS_BASEPATH + "/" + cartId + "/list_items/" + cartItemId
        def cartItemUri = "/carts/v4/cart_items/" + cartItemId + "?cart_id=" + cartId
        def completedCartItemUri = "/carts/v4/cart_items/" + cartItemId + "?cart_id=" + completedCartId
        def cartResponse = cartDataProvider.getCartResponse(cartId, guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        def completedCartResponse = cartDataProvider.getCartResponse(completedCartId, "1234",
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])
        def cartLists = [completedCartResponse]

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def cartItemResponse = cartDataProvider.getCartItemResponse(completedCartId, cartItemId, "1234",
            "some title", "some note", 1, 10, 10, "Stand Alone", "READY_FOR_LAUNCH",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))

        when:
        client.toBlocking().exchange(HttpRequest.DELETE(uri).headers(getHeaders(guestId)), ListItemDeleteResponseTO)

        then:
        thrown(HttpClientResponseException)

        1 * mockServer.get({ path -> path.contains(getCartContentURI(cartId))},_) >> [status: 200, body: cartContentsResponse]
        1 * mockServer.get({ path -> path.contains(cartItemUri) }, { headers -> checkHeaders(headers) }) >> [status: 404]
        1 * mockServer.get({ path -> path.contains(completedCartItemUri) }, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartItemResponse]
        1 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=1234&cart_state=PENDING")}, _) >> [status: 200, body: cartLists]
        1 * mockServer.post({ path -> path.contains("/carts/v4/deleted_cart_items")},_, { headers -> checkHeaders(headers) }) >> [status: 400, body: '{"message": "bad data"}']

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test deleting list item from completed cart with no existing completed cart"() {
        def cartId = UUID.randomUUID()
        def cartItemId = UUID.randomUUID()
        def uri = Constants.LISTS_BASEPATH + "/" + cartId + "/list_items/" + cartItemId
        def cartItemUri = "/carts/v4/cart_items/" + cartItemId + "?cart_id=" + cartId
        def cartResponse = cartDataProvider.getCartResponse(cartId, guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        when:
        client.toBlocking().exchange(HttpRequest.DELETE(uri).headers(getHeaders(guestId)), ListItemDeleteResponseTO)

        then:
        thrown(HttpClientResponseException)

        1 * mockServer.get({ path -> path.contains(getCartContentURI(cartId))},_) >> [status: 200, body: cartContentsResponse]
        1 * mockServer.get({ path -> path.contains(cartItemUri) }, { headers -> checkHeaders(headers) }) >> [status: 404]
        1 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=1234&cart_state=PENDING")}, _) >> [status: 404, body: null]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test deleting list item from completed cart with no cart item to delete"() {
        def cartId = UUID.randomUUID()
        def completedCartId = UUID.randomUUID()
        def cartItemId = UUID.randomUUID()
        def uri = Constants.LISTS_BASEPATH + "/" + cartId + "/list_items/" + cartItemId
        def cartItemUri = "/carts/v4/cart_items/" + cartItemId + "?cart_id=" + cartId
        def completedCartItemUri = "/carts/v4/cart_items/" + cartItemId + "?cart_id=" + completedCartId
        def cartResponse = cartDataProvider.getCartResponse(cartId, guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        def completedCartResponse = cartDataProvider.getCartResponse(completedCartId, "1234",
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])
        def cartLists = [completedCartResponse]

        when:
        client.toBlocking().exchange(HttpRequest.DELETE(uri).headers(getHeaders(guestId)), ListItemDeleteResponseTO)

        then:
        thrown(HttpClientResponseException)

        1 * mockServer.get({ path -> path.contains(getCartContentURI(cartId))},_) >> [status: 200, body: cartContentsResponse]
        1 * mockServer.get({ path -> path.contains(cartItemUri) }, { headers -> checkHeaders(headers) }) >> [status: 404]
        1 * mockServer.get({ path -> path.contains(completedCartItemUri) }, { headers -> checkHeaders(headers) }) >> [status: 404]
        1 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=1234&cart_state=PENDING")}, _) >> [status: 200, body: cartLists]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }
}


