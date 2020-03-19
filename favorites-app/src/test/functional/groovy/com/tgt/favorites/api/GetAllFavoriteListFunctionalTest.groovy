package com.tgt.favorites.api

import com.tgt.favorites.api.util.FavoriteConstants
import com.tgt.favorites.transport.FavoriteGetAllListResponseTO
import com.tgt.favorites.util.BaseFunctionalTest
import com.tgt.lists.cart.transport.CartContentsResponse
import com.tgt.lists.cart.transport.CartResponse
import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.lib.api.transport.ListMetaDataTO
import com.tgt.lists.lib.api.transport.UserMetaDataTO
import com.tgt.lists.lib.api.util.*
import com.tgt.lists.msgbus.ListsMessageBusProducer
import com.tgt.lists.msgbus.event.EventLifecycleNotificationProvider
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.uri.UriTemplate
import io.micronaut.test.annotation.MicronautTest
import io.micronaut.test.annotation.MockBean
import spock.lang.Shared

import javax.inject.Inject

import static com.tgt.favorites.util.DataProvider.*

@MicronautTest
class GetAllFavoriteListFunctionalTest extends BaseFunctionalTest {

    @Shared
    @Inject
    EventLifecycleNotificationProvider eventNotificationsProvider

    @MockBean(ListsMessageBusProducer.class)
    ListsMessageBusProducer createMockListsMessageBusProducer() {
        return newMockMsgbusKafkaProducerClient(eventNotificationsProvider)
    }

    def "test get lists integration"() {
        given:
        String guestId = "1234"
        UUID cartId1 = UUID.randomUUID()
        UUID cartId2 = UUID.randomUUID()
        UUID cartId3 = UUID.randomUUID()

        ListMetaDataTO metadata1 = new ListMetaDataTO(true, "FAVORITES", LIST_STATUS.PENDING)
        CartResponse cartResponse1 = cartDataProvider.getCartResponse(cartId1, guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list1", "1st list", null, cartDataProvider.getMetaData(metadata1, new UserMetaDataTO()))

        ListMetaDataTO metadata2 = new ListMetaDataTO(false, "FAVORITES", LIST_STATUS.PENDING)
        CartResponse cartResponse2 = cartDataProvider.getCartResponse(cartId2, guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list2", "2nd list", null, cartDataProvider.getMetaData(metadata2, new UserMetaDataTO()))

        ListMetaDataTO metadata3 = new ListMetaDataTO(false, "FAVORITES", LIST_STATUS.PENDING)
        CartResponse cartResponse3 = cartDataProvider.getCartResponse(cartId3, guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list3", "3rd list", null, cartDataProvider.getMetaData(metadata3, new UserMetaDataTO()))

        List<CartResponse> cartResponseList = [cartResponse1, cartResponse2, cartResponse3]

        CartContentsResponse cartContentsResponse1 = cartDataProvider.getCartContentsResponse(cartId1, 1)

        CartContentsResponse cartContentsResponse2 = cartDataProvider.getCartContentsResponse(cartId2, 2)

        CartContentsResponse cartContentsResponse3 = cartDataProvider.getCartContentsResponse(cartId3, 3)

        when:
        HttpResponse<FavoriteGetAllListResponseTO[]> listsResponse = client.toBlocking().exchange(
            HttpRequest.GET(FavoriteConstants.BASEPATH).headers(getHeaders(guestId)), FavoriteGetAllListResponseTO[])
        def actualStatus = listsResponse.status()
        def actualBody  = listsResponse.body()

        then:
        actualStatus == HttpStatus.OK
        actualBody.length == 3

        actualBody[0].listId == cartResponse1.cartId
        actualBody[0].channel == LIST_CHANNEL.valueOf(cartResponse1.cartChannel)
        actualBody[0].listTitle == cartResponse1.tenantCartName
        actualBody[0].defaultList
        actualBody[0].totalItemsCount == 1

        actualBody[1].listId == cartResponse2.cartId
        actualBody[1].channel == LIST_CHANNEL.valueOf(cartResponse2.cartChannel)
        actualBody[1].listTitle == cartResponse2.tenantCartName
        !actualBody[1].defaultList
        actualBody[1].totalItemsCount == 2

        actualBody[2].listId == cartResponse3.cartId
        actualBody[2].channel == LIST_CHANNEL.valueOf(cartResponse3.cartChannel)
        actualBody[2].listTitle == cartResponse3.tenantCartName
        !actualBody[2].defaultList
        actualBody[2].totalItemsCount == 3

        1 * mockServer.get({ path -> path.contains(getCartURI(guestId))}, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartResponseList]
        1 * mockServer.get(
            { path ->
                path.contains("/carts/v4/cart_contents/" + cartId1.toString())}, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartContentsResponse1]
        1 * mockServer.get(
            { path ->
                path.contains("/carts/v4/cart_contents/" + cartId2.toString())}, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartContentsResponse2]
        1 * mockServer.get(
            { path ->
                path.contains("/carts/v4/cart_contents/" + cartId3.toString())}, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartContentsResponse3]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test get lists integration with sort by list title ascending"() {
        given:
        String guestId = "1235"
        UUID cartId1 = UUID.randomUUID()
        UUID cartId2 = UUID.randomUUID()
        UUID cartId3 = UUID.randomUUID()

        ListMetaDataTO metadata1 = new ListMetaDataTO(true, "FAVORITES", LIST_STATUS.PENDING)
        CartResponse cartResponse1 = cartDataProvider.getCartResponse(cartId1, guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list1", "1st list", null, cartDataProvider.getMetaData(metadata1, new UserMetaDataTO()))

        ListMetaDataTO metadata2 = new ListMetaDataTO(false, "FAVORITES", LIST_STATUS.PENDING)
        CartResponse cartResponse2 = cartDataProvider.getCartResponse(cartId2, guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list2", "2nd list", null, cartDataProvider.getMetaData(metadata2, new UserMetaDataTO()))

        ListMetaDataTO metadata3 = new ListMetaDataTO(false, "FAVORITES", LIST_STATUS.PENDING)
        CartResponse cartResponse3 = cartDataProvider.getCartResponse(cartId3, guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list3", "3rd list", null, cartDataProvider.getMetaData(metadata3, new UserMetaDataTO()))

        List<CartResponse> cartResponseList = [cartResponse1, cartResponse2, cartResponse3]

        CartContentsResponse cartContentsResponse1 = cartDataProvider.getCartContentsResponse(cartId1, 1)

        CartContentsResponse cartContentsResponse2 = cartDataProvider.getCartContentsResponse(cartId2, 2)

        CartContentsResponse cartContentsResponse3 = cartDataProvider.getCartContentsResponse(cartId3, 3)

        when:
        final requestURI = new UriTemplate(FavoriteConstants.BASEPATH + "{?sort_field,sort_order}")
            .expand(sort_field: ListSortFieldGroup.LIST_TITLE, sort_order: ListSortOrderGroup.ASCENDING)
        HttpResponse<FavoriteGetAllListResponseTO[]> listsResponse = client.toBlocking().exchange(
            HttpRequest.GET(requestURI).headers(getHeaders(guestId)), FavoriteGetAllListResponseTO[])
        def actualStatus = listsResponse.status()
        def actualBody  = listsResponse.body()

        then:
        actualStatus == HttpStatus.OK
        actualBody.length == 3

        actualBody[0].listId == cartResponse1.cartId
        actualBody[0].channel == LIST_CHANNEL.valueOf(cartResponse1.cartChannel)
        actualBody[0].listTitle == cartResponse1.tenantCartName
        actualBody[0].defaultList
        actualBody[0].totalItemsCount == 1

        actualBody[1].listId == cartResponse2.cartId
        actualBody[1].channel == LIST_CHANNEL.valueOf(cartResponse2.cartChannel)
        actualBody[1].listTitle == cartResponse2.tenantCartName
        !actualBody[1].defaultList
        actualBody[1].totalItemsCount == 2

        actualBody[2].listId == cartResponse3.cartId
        actualBody[2].channel == LIST_CHANNEL.valueOf(cartResponse3.cartChannel)
        actualBody[2].listTitle == cartResponse3.tenantCartName
        !actualBody[2].defaultList
        actualBody[2].totalItemsCount == 3

        1 * mockServer.get({ path -> path.contains(getCartURI(guestId))}, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartResponseList]
        1 * mockServer.get(
            { path ->
                path.contains("/carts/v4/cart_contents/" + cartId1.toString())}, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartContentsResponse1]
        1 * mockServer.get(
            { path ->
                path.contains("/carts/v4/cart_contents/" + cartId2.toString())}, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartContentsResponse2]
        1 * mockServer.get(
            { path ->
                path.contains("/carts/v4/cart_contents/" + cartId3.toString())}, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartContentsResponse3]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test get lists integration with sort by list title descending"() {
        given:
        String guestId = "1236"
        UUID cartId1 = UUID.randomUUID()
        UUID cartId2 = UUID.randomUUID()
        UUID cartId3 = UUID.randomUUID()

        ListMetaDataTO metadata1 = new ListMetaDataTO(true, "FAVORITES", LIST_STATUS.PENDING)
        CartResponse cartResponse1 = cartDataProvider.getCartResponse(cartId1, guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list1", "1st list", null, cartDataProvider.getMetaData(metadata1, new UserMetaDataTO()))

        ListMetaDataTO metadata2 = new ListMetaDataTO(false, "FAVORITES", LIST_STATUS.PENDING)
        CartResponse cartResponse2 = cartDataProvider.getCartResponse(cartId2, guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list2", "2nd list", null, cartDataProvider.getMetaData(metadata2, new UserMetaDataTO()))

        ListMetaDataTO metadata3 = new ListMetaDataTO(false, "FAVORITES", LIST_STATUS.PENDING)
        CartResponse cartResponse3 = cartDataProvider.getCartResponse(cartId3, guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list3", "3rd list", null, cartDataProvider.getMetaData(metadata3, new UserMetaDataTO()))

        List<CartResponse> cartResponseList = [cartResponse1, cartResponse2, cartResponse3]

        CartContentsResponse cartContentsResponse1 = cartDataProvider.getCartContentsResponse(cartId1, 1)

        CartContentsResponse cartContentsResponse2 = cartDataProvider.getCartContentsResponse(cartId2, 2)

        CartContentsResponse cartContentsResponse3 = cartDataProvider.getCartContentsResponse(cartId3, 3)

        when:
        final requestURI = new UriTemplate(FavoriteConstants.BASEPATH + "{?sort_field,sort_order}")
            .expand(sort_field: ListSortFieldGroup.LIST_TITLE, sort_order: ListSortOrderGroup.DESCENDING)
        HttpResponse<FavoriteGetAllListResponseTO[]> listsResponse = client.toBlocking().exchange(
            HttpRequest.GET(requestURI).headers(getHeaders(guestId)), FavoriteGetAllListResponseTO[])
        def actualStatus = listsResponse.status()
        def actualBody  = listsResponse.body()

        then:
        actualStatus == HttpStatus.OK
        actualBody.length == 3

        actualBody[0].listId == cartResponse3.cartId
        actualBody[0].channel == LIST_CHANNEL.valueOf(cartResponse3.cartChannel)
        actualBody[0].listTitle == cartResponse3.tenantCartName
        !actualBody[0].defaultList
        actualBody[0].totalItemsCount == 3

        actualBody[1].listId == cartResponse2.cartId
        actualBody[1].channel == LIST_CHANNEL.valueOf(cartResponse2.cartChannel)
        actualBody[1].listTitle == cartResponse2.tenantCartName
        !actualBody[1].defaultList
        actualBody[1].totalItemsCount == 2

        actualBody[2].listId == cartResponse1.cartId
        actualBody[2].channel == LIST_CHANNEL.valueOf(cartResponse1.cartChannel)
        actualBody[2].listTitle == cartResponse1.tenantCartName
        actualBody[2].defaultList
        actualBody[2].totalItemsCount == 1

        1 * mockServer.get({ path -> path.contains(getCartURI(guestId))}, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartResponseList]
        1 * mockServer.get(
            { path ->
                path.contains("/carts/v4/cart_contents/" + cartId1.toString())}, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartContentsResponse1]
        1 * mockServer.get(
            { path ->
                path.contains("/carts/v4/cart_contents/" + cartId2.toString())}, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartContentsResponse2]
        1 * mockServer.get(
            { path ->
                path.contains("/carts/v4/cart_contents/" + cartId3.toString())}, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartContentsResponse3]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }
}
