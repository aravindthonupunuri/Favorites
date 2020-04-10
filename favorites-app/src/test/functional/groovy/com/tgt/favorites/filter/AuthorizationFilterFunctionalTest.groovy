package com.tgt.favorites.filter

import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.lib.api.transport.ListGetAllResponseTO
import com.tgt.favorites.util.FavoriteConstants
import com.tgt.lists.lib.api.util.LIST_CHANNEL
import com.tgt.lists.msgbus.ListsMessageBusProducer
import com.tgt.lists.msgbus.event.EventLifecycleNotificationProvider
import com.tgt.favorites.api.util.TestUtilConstants
import com.tgt.favorites.util.BaseFunctionalTest
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MicronautTest
import io.micronaut.test.annotation.MockBean
import spock.lang.Shared

import javax.inject.Inject

import static com.tgt.favorites.util.DataProvider.*

@MicronautTest
class AuthorizationFilterFunctionalTest extends BaseFunctionalTest {

    String guestId = "1234"

    @Shared
    @Inject
    EventLifecycleNotificationProvider eventNotificationsProvider

    @MockBean(ListsMessageBusProducer.class)
    ListsMessageBusProducer createMockListsMessageBusProducer() {
        return newMockMsgbusKafkaProducerClient(eventNotificationsProvider)
    }

    def "test authorization success"() {
        given:
        def cartId = "fe9c3360-b44a-11e9-987d-03d970ca1c28"
        def uri = FavoriteConstants.BASEPATH + "/${cartId}?location_id=1375"
        def cartUri = "/carts/v4/cart_contents/${cartId}"
        def cartResponse = cartDataProvider.getCartResponse(UUID.fromString(cartId), "1234",
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, [(TestUtilConstants.DEFAULT_LIST_IND): true, (TestUtilConstants.LIST_TYPE): "SHOPPING"])
        Map response = ["cart" : cartResponse, "cart_items" : []]

        when:
        HttpResponse<List<ListGetAllResponseTO>> listsResponse = client.toBlocking().exchange(
            HttpRequest.GET(uri).headers(getHeaders(guestId)), List)
        def actualStatus = listsResponse.status()
        def actualBody  = listsResponse.body()

        then:
        actualStatus == HttpStatus.OK
        actualBody.size() == 1

        2 * mockServer.get({ path -> path.contains(cartUri) }, { headers -> checkHeaders(headers) }) >> [status: 200, body: response]
    }

    def "test authorization failure without profile_id"() {
        given:
        def cartId = "fe9c3360-b44a-11e9-987d-03d970ca1c28"
        def uri = FavoriteConstants.BASEPATH + "/${cartId}?location_id=1375"
        def cartUri = "/carts/v4/cart_contents/${cartId}"

        def cartResponse = cartDataProvider.getCartResponse(UUID.fromString(cartId), "1234",
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, [(TestUtilConstants.DEFAULT_LIST_IND): true, (TestUtilConstants.LIST_TYPE): "SHOPPING"])
        Map response = ["cart" : cartResponse, "cart_items" : []]

        when:
        client.toBlocking().exchange(HttpRequest.GET(uri), List)

        then:
        def error = thrown(HttpClientResponseException)
        error.status == HttpStatus.FORBIDDEN
     }

    def "test authorization failure with unauthorized profile_id access"() {
        given:
        def cartId = "fe9c3360-b44a-11e9-987d-03d970ca1c28"
        def uri = FavoriteConstants.BASEPATH + "/${cartId}?location_id=1375"
        def cartResponse = cartDataProvider.getCartResponse(UUID.fromString(cartId), guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        when:
        client.toBlocking().exchange(HttpRequest.GET(uri).headers(getHeaders("1235")), List)

        then:
        def error = thrown(HttpClientResponseException)
        error.status == HttpStatus.FORBIDDEN

        1 * mockServer.get({ path -> path.contains(getCartContentURI(cartId))}, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartContentsResponse]
    }

    def "test authorization failure for cart 404"() {
        given:
        def cartId = "fe9c3360-b44a-11e9-987d-03d970ca1c28"
        def uri = FavoriteConstants.BASEPATH + "/${cartId}?location_id=1375"

        when:
        client.toBlocking().exchange(HttpRequest.GET(uri).headers(getHeaders("1235")), List)

        then:
        def error = thrown(HttpClientResponseException)
        error.status == HttpStatus.NOT_FOUND

        1 * mockServer.get({ path -> path.contains(getCartContentURI(cartId))}, { headers -> checkHeaders(headers) }) >> [status: 404, body: null]
    }

    def "test authorization failure with cart 4xx"() {
        given:
        def cartId = "fe9c3360-b44a-11e9-987d-03d970ca1c28"
        def uri = FavoriteConstants.BASEPATH + "/${cartId}?location_id=1375"

        when:
        client.toBlocking().exchange(HttpRequest.GET(uri).headers(getHeaders("1235")), List)

        then:
        def error = thrown(HttpClientResponseException)
        error.status == HttpStatus.BAD_REQUEST

        1 * mockServer.get({ path -> path.contains(getCartContentURI(cartId))}, { headers -> checkHeaders(headers) }) >> [status: 400, body: '{"message": "bad data"}']
    }
}
