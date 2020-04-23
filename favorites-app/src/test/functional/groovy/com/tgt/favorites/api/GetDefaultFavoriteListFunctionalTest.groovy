package com.tgt.favorites.api

import com.tgt.favorites.api.util.RedskyDataProvider
import com.tgt.favorites.client.redsky.RedskyResponseTO
import com.tgt.favorites.client.redsky.getitemhydration.ItemDetailVO
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
    RedskyDataProvider redskyDataProvider = new RedskyDataProvider()

    def "test get default list integration"() {
        given:
        def uri = FavoriteConstants.BASEPATH + "/default_list?location_id=1375"
        String guestId = "1234"
        def listId = UUID.randomUUID()
        def tcin1 = "1234"; def tcin2 = "1235"

        def pendingCartResponse = cartDataProvider.getCartResponse(listId, guestId,
            LIST_CHANNEL.MOBILE, CartType.LIST, "My list", "My first list", null,
            cartDataProvider.getMetaData(new ListMetaDataTO(true, LIST_STATUS.PENDING), new UserMetaDataTO()))

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), tcin1, tcin1,
            "title1", 3, "note\nnote",10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID(), tcin2, tcin2,
            "coffee", 1, "itemNote",10, 10, "Stand Alone",
            "READY", "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()), null, null, null)

        def pendingCartContentsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])

        ItemDetailVO itemDetailVO = redskyDataProvider.getItemDetailVO([tcin1, tcin2])
        RedskyResponseTO redskyResponseTO = new RedskyResponseTO(null, itemDetailVO)

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
        actual.listItems.size() == 2
        actual.listItems[0].tcin == tcin1
        actual.listItems[0].item == itemDetailVO.products[0].item
        actual.listItems[0].price == itemDetailVO.products[0].price
        actual.listItems[0].averageOverallRating == itemDetailVO.products[0].ratingsAndReviews.statistics.rating.average
        actual.listItems[0].totalReviewCount == itemDetailVO.products[0].ratingsAndReviews.statistics.reviewCount
        actual.listItems[0].availableToPromise == itemDetailVO.products[0].availableToPromise
        actual.listItems[1].tcin == tcin2
        actual.listItems[1].item == itemDetailVO.products[1].item
        actual.listItems[1].price == itemDetailVO.products[1].price
        actual.listItems[1].averageOverallRating == itemDetailVO.products[1].ratingsAndReviews.statistics.rating.average
        actual.listItems[1].totalReviewCount == itemDetailVO.products[1].ratingsAndReviews.statistics.reviewCount
        actual.listItems[1].availableToPromise == itemDetailVO.products[1].availableToPromise

        1 * mockServer.get({ path -> path.contains(getCartURI(guestId))},{ headers -> checkHeaders(headers) }) >> [status: 200, body: [pendingCartResponse]]
        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_contents/" + listId) }, _) >> [status: 200, body: pendingCartContentsResponse]
        1 * mockServer.get({ path -> path.contains("/redsky_aggregations/v1/lists/favorites_list_item_hydration_v1")}, _) >> [status: 200, body: redskyResponseTO]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }
}
