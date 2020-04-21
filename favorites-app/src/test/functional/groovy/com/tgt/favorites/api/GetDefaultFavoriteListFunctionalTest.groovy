package com.tgt.favorites.api

import com.tgt.favorites.transport.FavouritesListResponseTO
import com.tgt.favorites.util.FavoriteConstants
import com.tgt.favorites.util.BaseFunctionalTest
import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.lib.api.transport.*
import com.tgt.lists.lib.api.util.*
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.test.annotation.MicronautTest

import static com.tgt.favorites.util.DataProvider.*

@MicronautTest
class GetDefaultFavoriteListFunctionalTest extends BaseFunctionalTest {

    String guestId = "1234"

    def "test get default list integration"() {
        given:
        def uri = FavoriteConstants.BASEPATH + "/default_list?location_id=1375"
        String guestId = "1234"
        def listId = UUID.randomUUID()

        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId,
            LIST_CHANNEL.MOBILE, CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(new ListMetaDataTO(true, LIST_STATUS.PENDING), new UserMetaDataTO()))

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), "1234", "1234",
            "title1", 3, "note\nnote",10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.GENERIC_ITEM, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), "1234", null,
            "coffee", 1, "itemNote",10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        def pendingCartContentsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])

        when:
        HttpResponse<FavouritesListResponseTO> listResponse = client.toBlocking()
            .exchange(HttpRequest.GET(uri).headers(getHeaders(guestId)), FavouritesListResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.OK

        actual.listId == pendingCartResponse.cartId
        actual.channel == LIST_CHANNEL.valueOf(pendingCartResponse.cartChannel)
        actual.listTitle == pendingCartResponse.tenantCartName
        actual.shortDescription == pendingCartResponse.tenantCartDescription
        actual.defaultList

        1 * mockServer.get({ path -> path.contains(getCartURI(guestId))},{ headers -> checkHeaders(headers) }) >> [status: 200, body: [pendingCartResponse]]
        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_contents/" + listId) }, _) >> [status: 200, body: pendingCartContentsResponse]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }
}
