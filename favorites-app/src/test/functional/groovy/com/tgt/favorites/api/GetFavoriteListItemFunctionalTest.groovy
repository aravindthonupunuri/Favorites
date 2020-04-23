package com.tgt.favorites.api

import com.tgt.favorites.api.util.RedskyDataProvider
import com.tgt.favorites.client.redsky.RedskyResponseTO
import com.tgt.favorites.client.redsky.getitemhydration.ItemDetailVO
import com.tgt.favorites.transport.FavoriteListItemGetResponseTO
import com.tgt.favorites.util.BaseFunctionalTest
import com.tgt.lists.lib.api.transport.ListItemMetaDataTO
import com.tgt.lists.lib.api.transport.ListItemResponseTO
import com.tgt.lists.lib.api.transport.UserItemMetaDataTO
import com.tgt.favorites.util.FavoriteConstants
import com.tgt.lists.lib.api.util.ItemType
import com.tgt.lists.lib.api.util.LIST_ITEM_STATE
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MicronautTest

import static com.tgt.favorites.util.DataProvider.*

@MicronautTest
class GetFavoriteListItemFunctionalTest extends BaseFunctionalTest {

    String guestId = "1234"
    RedskyDataProvider redskyDataProvider = new RedskyDataProvider()

    def "get list item integrity test"() {
        def cartId = UUID.randomUUID()
        def cartItemId = "aaaaaaaa-1111-bbbb-2222-cccccccccccc"
        def uri = FavoriteConstants.BASEPATH + "/" + cartId + "/list_items/" + cartItemId + "?location_id=1375"
        def cartUri = "/carts/v4/cart_items/aaaaaaaa-1111-bbbb-2222-cccccccccccc?cart_id=" + cartId
        def tcin1 = "1234"

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(cartId, UUID.fromString(cartItemId), tcin1, tcin1,
            "some title", "some note", 1, 10, 10, "Stand Alone", "READY_FOR_LAUNCH",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))
        def cartResponse = cartDataProvider.getCartResponse(cartId, guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        ItemDetailVO itemDetailVO = redskyDataProvider.getItemDetailVO([tcin1])
        RedskyResponseTO redskyResponseTO = new RedskyResponseTO(null, itemDetailVO)

        when:
        HttpResponse<FavoriteListItemGetResponseTO> listItemResponse = client.toBlocking()
            .exchange(HttpRequest.GET(uri).headers(getHeaders(guestId)), FavoriteListItemGetResponseTO)
        def actualStatus = listItemResponse.status()
        def actual = listItemResponse.body()

        then:
        actualStatus == HttpStatus.OK

        actual.listItemId == cartItemResponse.cartItemId
        actual.tcin == cartItemResponse.tcin
        actual.itemTitle == cartItemResponse.tenantItemName
        actual.itemNote == cartItemResponse.notes
        actual.images == cartItemResponse.images
        actual.item == itemDetailVO.products[0].item
        actual.price == itemDetailVO.products[0].price
        actual.averageOverallRating == itemDetailVO.products[0].ratingsAndReviews.statistics.rating.average
        actual.totalReviewCount == itemDetailVO.products[0].ratingsAndReviews.statistics.reviewCount
        actual.availableToPromise == itemDetailVO.products[0].availableToPromise

        1 * mockServer.get({ path -> path.contains(getCartContentURI(cartId))}, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartContentsResponse]
        1 * mockServer.get({ path -> path.contains(cartUri) }, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartItemResponse]
        1 * mockServer.get({ path -> path.contains("/redsky_aggregations/v1/lists/favorites_list_item_hydration_v1")}, _) >> [status: 200, body: redskyResponseTO]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "get get list item test when location_id is not passed"() {
        def cartId = UUID.randomUUID()
        def cartItemId = "aaaaaaaa-1111-bbbb-2222-cccccccccccc"
        def uri = FavoriteConstants.BASEPATH + "/" + cartId + "/list_items/" + cartItemId
        def cartResponse = cartDataProvider.getCartResponse(cartId, guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        when:
        client.toBlocking()
            .exchange(HttpRequest.GET(uri).headers(getHeaders(guestId)), ListItemResponseTO)

        then:
        def error = thrown(HttpClientResponseException)
        error.status == HttpStatus.BAD_REQUEST

        1 * mockServer.get({ path -> path.contains(getCartContentURI(cartId))}, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartContentsResponse]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }
}


