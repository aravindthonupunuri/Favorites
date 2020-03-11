package com.tgt.favorites.api

import com.tgt.favorites.transport.FavouritesListResponseTO
import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.lib.api.transport.ListMetaDataTO
import com.tgt.lists.lib.api.transport.ListResponseTO
import com.tgt.lists.lib.api.transport.UserMetaDataTO
import com.tgt.favorites.api.util.FavoriteConstants
import com.tgt.lists.lib.api.util.LIST_CHANNEL
import com.tgt.lists.lib.api.util.LIST_STATUS
import com.tgt.favorites.api.util.CartDataProvider
import com.tgt.favorites.api.util.TestUtilConstants
import com.tgt.favorites.util.BaseKafkaFunctionalTest
import groovy.json.JsonOutput
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Ignore
import spock.lang.Unroll

import static com.tgt.favorites.util.DataProvider.*

@MicronautTest
class CreateFavoriteListFunctionalTest extends BaseKafkaFunctionalTest {

    CartDataProvider cartDataProvider = new CartDataProvider()

    @Unroll
    def "Test create list for bad request"() {
        given:
        String guestId = "1239"
        Map listRequest =
            [
                "channel"                    : channel,
                "list_title"                 : listTitle,
                (TestUtilConstants.LIST_TYPE): "SHOPPING",
                "short_description"          : "my favorite list",
                "location_id"                : 1375L
            ]

        when:
        client.toBlocking()
            .exchange(HttpRequest.POST(FavoriteConstants.BASEPATH, JsonOutput.toJson(listRequest))
            .headers(getHeaders(guestId, false)))

        then:
        def error = thrown(HttpClientResponseException)
        error.status == HttpStatus.BAD_REQUEST

        where:
        channel                 |   listTitle                   |   listType
            null                |       "listTitle"             |       "SHOPPING"
            LIST_CHANNEL.WEB    |       ""                      |       "SHOPPING"
            LIST_CHANNEL.WEB    |       null                    |       "SHOPPING"
    }

    @Ignore
    @Unroll
    def "test create list for bad response"() {
        given:
        String guestId = "1235"
        Map listRequest =
            [
                "channel": LIST_CHANNEL.WEB,
                "list_title": "list1",
                (TestUtilConstants.LIST_TYPE) : "SHOPPING",
                "short_description": "my favorite list",
                "location_id"            : 1375L
            ]

        ListMetaDataTO metadata = new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.PENDING)

        def cartResponse = cartDataProvider.getCartResponse(listId, guestId,
            listChannel, CartType.LIST, listTitle, "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))

        when:
        client.toBlocking().exchange(HttpRequest.POST(FavoriteConstants.BASEPATH, JsonOutput.toJson(listRequest))
            .headers(getHeaders(guestId)), FavouritesListResponseTO)

        then:
        2 * mockServer.post({ path -> path.contains("/carts/v4")},_,{ headers -> checkHeaders(headers) }) >> [status: 200, body: cartResponse]

        def error = thrown(HttpClientResponseException)
        error.status == HttpStatus.INTERNAL_SERVER_ERROR

        where:
        listId                  |   listChannel         |      listType                |   listTitle
        null                    |       LIST_CHANNEL.WEB           |       "SHOPPING"      |   "some title"
        UUID.randomUUID()       |       null            |       "SHOPPING"      |   "some title"
        UUID.randomUUID()       |       LIST_CHANNEL.WEB           |       "SHOPPING"      |   ""
        UUID.randomUUID()       |       LIST_CHANNEL.WEB           |       "SHOPPING"      |   null
    }

    def "test create list integrity"() {
        given:
        String guestId = "1236"
        Map listRequest =
            [
                "channel": LIST_CHANNEL.WEB,
                "list_title": "list1",
                "short_description": "my favorite list"
            ]

        ListMetaDataTO metadata = new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.PENDING)

        def cartResponse = cartDataProvider.getCartResponse(UUID.randomUUID(), guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))

        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)

        def cartLists = []

        when:
        HttpResponse<ListResponseTO> favouritesListResponse = client.toBlocking().exchange(
            HttpRequest.POST(FavoriteConstants.BASEPATH, JsonOutput.toJson(listRequest))
                .headers(getHeaders(guestId)), FavouritesListResponseTO)
        def actualStatus = favouritesListResponse.status()
        def actual = favouritesListResponse.body()

        then:
        actualStatus == HttpStatus.CREATED

        actual.listId == cartResponse.cartId
        actual.channel == LIST_CHANNEL.valueOf(cartResponse.cartChannel)
        actual.listTitle == cartResponse.tenantCartName
        actual.shortDescription == cartResponse.tenantCartDescription
        actual.listType == listMetaData.listType
        actual.defaultList == listMetaData.defaultList

        1 * mockServer.get({ path -> path.contains(getCartURI(guestId))},{ headers -> checkHeaders(headers) }) >> [status: 200, body: cartLists]
        1 * mockServer.post({ path -> path.contains("/carts/v4/")},_,{ headers -> checkHeaders(headers) }) >> [status: 200, body: cartResponse]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }
}
