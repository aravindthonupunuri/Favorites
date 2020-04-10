package com.tgt.favorites.api

import com.tgt.favorites.api.util.FavoriteConstants
import com.tgt.favorites.transport.GuestFavoritesResponseTO
import com.tgt.favorites.transport.ListItemDetailsTO
import com.tgt.favorites.util.BaseFunctionalTest
import com.tgt.lists.cart.transport.CartContentsResponse
import com.tgt.lists.cart.transport.CartItemResponse
import com.tgt.lists.cart.transport.CartResponse
import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.lib.api.transport.ListMetaDataTO
import com.tgt.lists.lib.api.transport.UserMetaDataTO
import com.tgt.lists.lib.api.util.LIST_CHANNEL
import com.tgt.lists.lib.api.util.LIST_STATUS
import com.tgt.lists.msgbus.ListsMessageBusProducer
import com.tgt.lists.msgbus.event.EventLifecycleNotificationProvider
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.uri.UriTemplate
import io.micronaut.test.annotation.MicronautTest
import io.micronaut.test.annotation.MockBean
import spock.lang.Shared
import javax.inject.Inject
import static com.tgt.favorites.util.DataProvider.getCartURI
import static com.tgt.favorites.util.DataProvider.getCheckHeaders
import static com.tgt.favorites.util.DataProvider.getHeaders

@MicronautTest
class GetFavoritesTcinFunctionalTest extends BaseFunctionalTest  {

    @Shared
    @Inject
    EventLifecycleNotificationProvider eventNotificationsProvider

    @MockBean(ListsMessageBusProducer.class)
    ListsMessageBusProducer createMockListsMessageBusProducer() {
        return newMockMsgbusKafkaProducerClient(eventNotificationsProvider)
    }

    def "test get tcins from favourites lists integration"() {
        given:
        String guestId = "1234"
        UUID cartId1 = UUID.randomUUID()
        UUID cartId2 = UUID.randomUUID()
        UUID cartItemId1 = UUID.randomUUID()
        UUID cartItemId2 = UUID.randomUUID()
        UUID cartItemId3 = UUID.randomUUID()
        UUID cartItemId4 = UUID.randomUUID()

        ListMetaDataTO metadata1 = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        CartResponse cartResponse1 = cartDataProvider.getCartResponse(cartId1, guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list1", "1st list", null, cartDataProvider.getMetaData(metadata1, new UserMetaDataTO()))

        ListMetaDataTO metadata2 = new ListMetaDataTO(false, LIST_STATUS.PENDING)
        CartResponse cartResponse2 = cartDataProvider.getCartResponse(cartId2, guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list2", "2nd list", null, cartDataProvider.getMetaData(metadata2, new UserMetaDataTO()))

        List<CartResponse> cartResponseList = [cartResponse1, cartResponse2]

        CartItemResponse cartItemResponse1 = cartDataProvider.getCartItemResponse(cartId1, cartItemId1, "19487487", "1234", "item 1")
        CartItemResponse cartItemResponse2 = cartDataProvider.getCartItemResponse(cartId1, cartItemId2, "19487487", "5678", "item 2")
        CartItemResponse cartItemResponse3 = cartDataProvider.getCartItemResponse(cartId2, cartItemId3, "19487487", "1234", "item 3")
        CartItemResponse cartItemResponse4 = cartDataProvider.getCartItemResponse(cartId2, cartItemId4, "19487487", "5678", "item 4")

        ListItemDetailsTO listItemDetails1TO = new ListItemDetailsTO(cartId1, "My list1", cartItemId1)
        ListItemDetailsTO listItemDetails2TO = new ListItemDetailsTO(cartId1, "My list1", cartItemId2)
        ListItemDetailsTO listItemDetails3TO = new ListItemDetailsTO(cartId2, "My list2", cartItemId3)
        ListItemDetailsTO listItemDetails4TO = new ListItemDetailsTO(cartId2, "My list2", cartItemId4)

        CartContentsResponse cartContentsResponse1 = cartDataProvider.getCartContentsResponse(cartResponse1, [cartItemResponse1, cartItemResponse2])
        CartContentsResponse cartContentsResponse2 = cartDataProvider.getCartContentsResponse(cartResponse2, [cartItemResponse3, cartItemResponse4])

        when:
        final requestURI = new UriTemplate(FavoriteConstants.BASEPATH + "/guest_favourites{?tcins}")
            .expand(tcins: "1234,5678")
        HttpResponse<GuestFavoritesResponseTO[]> listsResponse = client.toBlocking().exchange(
            HttpRequest.GET(requestURI).headers(getHeaders(guestId)), GuestFavoritesResponseTO[])
        def actualStatus = listsResponse.status()
        def actualBody = listsResponse.body()

        then:
        actualStatus == HttpStatus.OK
        actualBody[0].tcin == "1234"
        actualBody[1].tcin == "5678"
        actualBody[0].listItemDetails[1] == listItemDetails3TO || listItemDetails1TO
        actualBody[0].listItemDetails[0] == listItemDetails1TO || listItemDetails3TO
        actualBody[1].listItemDetails[0] == listItemDetails2TO || listItemDetails4TO
        actualBody[1].listItemDetails[1] == listItemDetails4TO || listItemDetails2TO

        1 * mockServer.get({ path -> path.contains(getCartURI(guestId))}, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartResponseList]
        1 * mockServer.get(
            { path ->
                path.contains("/carts/v4/cart_contents/" + cartId1.toString())}, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartContentsResponse1]
        1 * mockServer.get(
            { path ->
                path.contains("/carts/v4/cart_contents/" + cartId2.toString())}, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartContentsResponse2]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')

    }

    def "test when tcin count exceeds max count specified from favourites lists"() {
        given:
        String guestId = "1234"

        String tcinString = "1234,5678,9876"

        when:
        final requestURI = new UriTemplate(FavoriteConstants.BASEPATH + "/guest_favourites{?tcins}")
            .expand(tcins: tcinString)

        client.toBlocking().exchange(HttpRequest.GET(requestURI).headers(getHeaders(guestId)), GuestFavoritesResponseTO[])

        then:
        def error = thrown(HttpClientResponseException)
        error.status == HttpStatus.BAD_REQUEST
    }
}
