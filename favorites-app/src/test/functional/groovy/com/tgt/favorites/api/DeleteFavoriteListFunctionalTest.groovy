package com.tgt.favorites.api

import com.tgt.favorites.util.BaseKafkaFunctionalTest
import com.tgt.lists.cart.transport.CartDeleteResponse
import com.tgt.lists.cart.transport.CartResponse
import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.lib.api.transport.ListDeleteResponseTO
import com.tgt.lists.lib.api.transport.ListMetaDataTO
import com.tgt.lists.lib.api.transport.UserMetaDataTO
import com.tgt.favorites.api.util.FavoriteConstants
import com.tgt.lists.lib.api.util.LIST_CHANNEL
import com.tgt.lists.lib.api.util.LIST_STATUS
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.test.annotation.MicronautTest

import static com.tgt.favorites.util.DataProvider.*

@MicronautTest
class DeleteFavoriteListFunctionalTest extends BaseKafkaFunctionalTest {

    def "test delete list success scenario"() {
        given:
        String guestId = "1234"
        UUID listId = UUID.randomUUID()
        CartDeleteResponse pendingDeleteResponse = cartDataProvider.getCartDeleteResponse(listId)
        ListMetaDataTO pendingCartMetadata = new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.PENDING)
        CartResponse pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(pendingCartMetadata, new UserMetaDataTO()))
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, null)

        when:
        HttpResponse<ListDeleteResponseTO> deletedListResponseVO =
            client.toBlocking().exchange(
                HttpRequest.DELETE(FavoriteConstants.BASEPATH + '/'+listId).headers(getHeaders(guestId)), ListDeleteResponseTO)

        def actualStatus = deletedListResponseVO.status()

        then:
        actualStatus == HttpStatus.NO_CONTENT

        1 * mockServer.get({ path -> path.contains(getCartContentURI(listId))},_) >> [status: 200, body: cartContentsResponse]
        1 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=")}, { headers -> checkHeaders(headers) }) >> [status: 200, body: [pendingCartResponse]]
        1 * mockServer.post({ path -> path.contains("/carts/v4/cart_deletes")},_, { headers -> checkHeaders(headers) }) >> [[status: 200, body: pendingDeleteResponse]]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }
}
