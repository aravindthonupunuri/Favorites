package com.tgt.shoppinglist.api

import com.tgt.lists.cart.transport.CartDeleteResponse
import com.tgt.lists.cart.transport.CartResponse
import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.lib.api.transport.ListDeleteResponseTO
import com.tgt.lists.lib.api.transport.ListMetaDataTO
import com.tgt.lists.lib.api.transport.UserMetaDataTO
import com.tgt.lists.lib.api.util.Constants
import com.tgt.lists.lib.api.util.LIST_CHANNEL
import com.tgt.lists.lib.api.util.LIST_STATUS
import com.tgt.shoppinglist.util.BaseKafkaFunctionalTest
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.test.annotation.MicronautTest

import static com.tgt.shoppinglist.util.DataProvider.*

@MicronautTest
class DeleteListFunctionalTest extends BaseKafkaFunctionalTest {

    def "test delete list success scenario"() {
        given:
        String guestId = "1234"
        UUID listId = UUID.randomUUID()
        UUID completedListId = UUID.randomUUID()
        CartDeleteResponse pendingDeleteResponse = cartDataProvider.getCartDeleteResponse(listId)
        CartDeleteResponse completedDeleteResponse = cartDataProvider.getCartDeleteResponse(completedListId)
        ListMetaDataTO pendingCartMetadata = new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.PENDING)
        CartResponse pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(pendingCartMetadata, new UserMetaDataTO()))
        ListMetaDataTO completedCartMetadata = new ListMetaDataTO(false, "SHOPPING", LIST_STATUS.COMPLETED)
        CartResponse completedCartResponse = cartDataProvider.getCartResponse(completedListId, guestId, listId.toString(),
            cartDataProvider.getMetaData(completedCartMetadata, new UserMetaDataTO()))
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, null)

        when:
        HttpResponse<ListDeleteResponseTO> deletedListResponseVO =
            client.toBlocking().exchange(
                HttpRequest.DELETE(Constants.LISTS_BASEPATH + '/'+listId).headers(getHeaders(guestId)), ListDeleteResponseTO)

        def actualStatus = deletedListResponseVO.status()
        def actualBody = deletedListResponseVO.body()

        then:
        actualStatus == HttpStatus.NO_CONTENT

        1 * mockServer.get({ path -> path.contains(getCartContentURI(listId))},_) >> [status: 200, body: cartContentsResponse]
        1 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=")}, { headers -> checkHeaders(headers) }) >> [status: 200, body: [pendingCartResponse, completedCartResponse]]
        2 * mockServer.post({ path -> path.contains("/carts/v4/cart_deletes")},_, { headers -> checkHeaders(headers) }) >>> [[status: 200, body: completedDeleteResponse], [status: 200, body: pendingDeleteResponse]]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test delete list when completed cart not present"() {
        given:
        String guestId = "1235"
        UUID listId = UUID.randomUUID()
        CartDeleteResponse pendingDeleteResponse = cartDataProvider.getCartDeleteResponse(listId)
        ListMetaDataTO pendingCartMetadata = new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.PENDING)
        CartResponse pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(pendingCartMetadata, new UserMetaDataTO()))
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, null)

        when:
        HttpResponse<ListDeleteResponseTO> deletedListResponseVO =
            client.toBlocking().exchange(
                HttpRequest.DELETE(Constants.LISTS_BASEPATH + '/'+listId).headers(getHeaders(guestId)), ListDeleteResponseTO)

        def actualStatus = deletedListResponseVO.status()
        def actualBody = deletedListResponseVO.body()

        then:
        actualStatus == HttpStatus.NO_CONTENT

        1 * mockServer.get({ path -> path.contains(getCartContentURI(listId))},_) >> [status: 200, body: cartContentsResponse]
        1 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=")}, { headers -> checkHeaders(headers) }) >> [status: 200, body: [pendingCartResponse]]
        1 * mockServer.post({ path -> path.contains("/carts/v4/cart_deletes")},_, { headers -> checkHeaders(headers) }) >> [status: 200, body: pendingDeleteResponse]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }
}
