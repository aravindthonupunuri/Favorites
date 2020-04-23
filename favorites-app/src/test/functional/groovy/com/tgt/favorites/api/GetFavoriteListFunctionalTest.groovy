package com.tgt.favorites.api

import com.tgt.favorites.api.util.RedskyDataProvider
import com.tgt.favorites.client.redsky.RedskyResponseTO
import com.tgt.favorites.client.redsky.getitemhydration.ItemDetailVO
import com.tgt.favorites.transport.FavouritesListResponseTO
import com.tgt.favorites.util.FavoriteConstants
import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.lib.api.transport.*
import com.tgt.lists.lib.api.util.*
import com.tgt.favorites.util.BaseFunctionalTest
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.test.annotation.MicronautTest

import static com.tgt.favorites.util.DataProvider.*

@MicronautTest
class GetFavoriteListFunctionalTest extends BaseFunctionalTest {

    String guestId = "1234"
    RedskyDataProvider redskyDataProvider = new RedskyDataProvider()

    def "test get list integrity"() {
        given:
        def cartId = "fe9c3360-b44a-11e9-987d-03d970ca1c28"
        def uri = FavoriteConstants.BASEPATH + "/" + cartId + "?location_id=1375"
        def cartUri = "/carts/v4/cart_contents/" + cartId

        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def tcin1 = "1234"; def tcin2 = "1235"; def tcin3 = "2345"; def tcin4 = "3456"

        def cartResponse = cartDataProvider.getCartResponse(UUID.fromString(cartId), guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))

        def cartItemResponse1 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), tcin1, tcin1,
            null, "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()))
        def cartItemResponse2 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), tcin2, tcin2,
            "coffee", "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()))
        def cartItemResponse3 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), tcin3, tcin3,
            null, "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()))
        def cartItemResponse4 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), tcin4, tcin4,
            "coffee", "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()))

        ItemDetailVO itemDetailVO = redskyDataProvider.getItemDetailVO([tcin1, tcin2, tcin3, tcin4])
        RedskyResponseTO redskyResponseTO = new RedskyResponseTO(null, itemDetailVO)

        Map response = ["cart" : cartResponse, "cart_items" : [cartItemResponse1, cartItemResponse2, cartItemResponse3, cartItemResponse4]]

        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)

        when:
        HttpResponse<FavouritesListResponseTO> listResponse = client.toBlocking()
            .exchange(HttpRequest.GET(uri).headers(getHeaders(guestId)), FavouritesListResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.OK

        actual.listId == cartResponse.cartId
        actual.channel == LIST_CHANNEL.valueOf(cartResponse.cartChannel)
        actual.listTitle == cartResponse.tenantCartName
        actual.shortDescription == cartResponse.tenantCartDescription
        actual.listType == "FAVORITES"
        actual.defaultList == listMetaData.defaultList

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
        actual.listItems[2].tcin == tcin3
        actual.listItems[2].item == itemDetailVO.products[2].item
        actual.listItems[2].price == itemDetailVO.products[2].price
        actual.listItems[2].averageOverallRating == itemDetailVO.products[2].ratingsAndReviews.statistics.rating.average
        actual.listItems[2].totalReviewCount == itemDetailVO.products[2].ratingsAndReviews.statistics.reviewCount
        actual.listItems[2].availableToPromise == itemDetailVO.products[2].availableToPromise
        actual.listItems[3].tcin == tcin4
        actual.listItems[3].item == itemDetailVO.products[3].item
        actual.listItems[3].price == itemDetailVO.products[3].price
        actual.listItems[3].averageOverallRating == itemDetailVO.products[3].ratingsAndReviews.statistics.rating.average
        actual.listItems[3].totalReviewCount == itemDetailVO.products[3].ratingsAndReviews.statistics.reviewCount
        actual.listItems[3].availableToPromise == itemDetailVO.products[3].availableToPromise

        2 * mockServer.get({ path -> path.contains(cartUri) }, _) >> [status: 200, body: response] //TODO: check why two calls here
        1 * mockServer.get({ path -> path.contains("/redsky_aggregations/v1/lists/favorites_list_item_hydration_v1")}, _) >> [status: 200, body: redskyResponseTO]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test get list for a given page value"() {
        given:
        def cartId = "fe9c3360-b44a-11e9-987d-03d970ca1c28"
        def uri = FavoriteConstants.BASEPATH + "/" + cartId + "?location_id=1375&sort_field=ITEM_TITTLE&sort_order=ASCENDING" + "&page=2"
        def cartUri = "/carts/v4/cart_contents/" + cartId

        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def tcin1 = "1234"; def tcin2 = "1235"; def tcin3 = "2345"; def tcin4 = "3456"

        def cartResponse = cartDataProvider.getCartResponse(UUID.fromString(cartId), guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))

        def cartItemResponse1 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), tcin1, tcin1,
            "A", "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()))
        def cartItemResponse2 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), tcin2, tcin2,
            "B", "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()))
        def cartItemResponse3 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), tcin3, tcin3,
            "C", "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()))
        def cartItemResponse4 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), tcin4, tcin4,
            "D", "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()))

        ItemDetailVO itemDetailVO = redskyDataProvider.getItemDetailVO([tcin3, tcin4])
        RedskyResponseTO redskyResponseTO = new RedskyResponseTO(null, itemDetailVO)

        Map response = ["cart" : cartResponse, "cart_items" : [cartItemResponse1, cartItemResponse2, cartItemResponse3, cartItemResponse4]]

        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)

        when:
        HttpResponse<FavouritesListResponseTO> listResponse = client.toBlocking()
            .exchange(HttpRequest.GET(uri).headers(getHeaders(guestId)), FavouritesListResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.OK

        actual.listId == cartResponse.cartId
        actual.channel == LIST_CHANNEL.valueOf(cartResponse.cartChannel)
        actual.listTitle == cartResponse.tenantCartName
        actual.shortDescription == cartResponse.tenantCartDescription
        actual.listType == "FAVORITES"
        actual.defaultList == listMetaData.defaultList

        actual.listItems[0].tcin == tcin3
        actual.listItems[0].item == itemDetailVO.products[0].item
        actual.listItems[0].price == itemDetailVO.products[0].price
        actual.listItems[0].averageOverallRating == itemDetailVO.products[0].ratingsAndReviews.statistics.rating.average
        actual.listItems[0].totalReviewCount == itemDetailVO.products[0].ratingsAndReviews.statistics.reviewCount
        actual.listItems[0].availableToPromise == itemDetailVO.products[0].availableToPromise
        actual.listItems[1].tcin == tcin4
        actual.listItems[1].item == itemDetailVO.products[1].item
        actual.listItems[1].price == itemDetailVO.products[1].price
        actual.listItems[1].averageOverallRating == itemDetailVO.products[1].ratingsAndReviews.statistics.rating.average
        actual.listItems[1].totalReviewCount == itemDetailVO.products[1].ratingsAndReviews.statistics.reviewCount
        actual.listItems[1].availableToPromise == itemDetailVO.products[1].availableToPromise

        2 * mockServer.get({ path -> path.contains(cartUri) }, _) >> [status: 200, body: response] //TODO: check why two calls here
        1 * mockServer.get({ path -> path.contains("/redsky_aggregations/v1/lists/favorites_list_item_hydration_v1")}, _) >> [status: 200, body: redskyResponseTO]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test get list integrity with sortedFieldGroups and sortOrder"() {
        given:
        def cartId = "fe9c3360-b44a-11e9-987d-03d970ca1c28"
        def uri = FavoriteConstants.BASEPATH + "/" + cartId + "?location_id=1375&sort_field=ITEM_TITTLE&sort_order=ASCENDING"
        def cartUri = "/carts/v4/cart_contents/" + cartId

        def tcin1 = "1234"; def tcin2 = "1235"
        ListMetaDataTO metadata = new ListMetaDataTO(true, LIST_STATUS.PENDING)
        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)

        def cartResponse = cartDataProvider.getCartResponse(UUID.fromString(cartId), guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))

        def cartItemResponse1 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), tcin1, tcin1,
            "banana", "some note",1,  10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()))
        def cartItemResponse2 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), tcin2, tcin2,
            "coffee", "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()))

        ItemDetailVO itemDetailVO = redskyDataProvider.getItemDetailVO([tcin1, tcin2])
        RedskyResponseTO redskyResponseTO = new RedskyResponseTO(null, itemDetailVO)

        Map response = ["cart" : cartResponse, "cart_items" : [cartItemResponse1, cartItemResponse2]]

        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)

        when:
        HttpResponse<FavouritesListResponseTO> listResponse = client.toBlocking()
            .exchange(HttpRequest.GET(uri).headers(getHeaders(guestId)), FavouritesListResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.OK

        actual.listId == cartResponse.cartId
        actual.channel == LIST_CHANNEL.valueOf(cartResponse.cartChannel)
        actual.listTitle == cartResponse.tenantCartName
        actual.shortDescription == cartResponse.tenantCartDescription
        actual.listType == "FAVORITES"
        actual.defaultList == listMetaData.defaultList

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

        2 * mockServer.get({ path -> path.contains(cartUri) }, _) >> [status: 200, body: response] //TODO: check why two calls here
        1 * mockServer.get({ path -> path.contains("/redsky_aggregations/v1/lists/favorites_list_item_hydration_v1")}, _) >> [status: 200, body: redskyResponseTO]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }
}
