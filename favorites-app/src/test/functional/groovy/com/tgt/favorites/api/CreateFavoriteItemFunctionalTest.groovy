package com.tgt.favorites.api

import com.tgt.favorites.api.util.FavoriteConstants
import com.tgt.favorites.transport.GuestFavoritesResponseTO
import com.tgt.favorites.util.BaseFunctionalTest
import com.tgt.lists.lib.api.util.ItemType
import groovy.json.JsonOutput
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.uri.UriTemplate
import io.micronaut.test.annotation.MicronautTest

import static com.tgt.favorites.util.DataProvider.getCartURI
import static com.tgt.favorites.util.DataProvider.getCheckHeaders
import static com.tgt.favorites.util.DataProvider.getHeaders

@MicronautTest
class CreateFavoriteItemFunctionalTest extends BaseFunctionalTest {

    def "test create default item for favourites lists integration"() {
        given:
        String guestId = "1234"
        UUID cartId1 = UUID.randomUUID()
        UUID cartId2 = UUID.randomUUID()
        UUID cartItemId1 = UUID.randomUUID()
        def uri = FavoriteConstants.BASEPATH + "/" + "/list_items?"
        def listItemRequest =
            [
                "item_type": ItemType.TCIN,
                "tcin"     : "53692059",
                "location_id" : 1375L
            ]

        when:
        final requestURI = new UriTemplate(FavoriteConstants.BASEPATH + "/list_items")
        HttpResponse<GuestFavoritesResponseTO[]> listsResponse = client.toBlocking().exchange(
            HttpRequest.POST(uri, JsonOutput.toJson(listItemRequest)).headers(getHeaders(guestId)), GuestFavoritesResponseTO[])
        def actualStatus = listsResponse.status()
        def actualBody = listsResponse.body()


        then:
        actualStatus == HttpStatus.OK

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')

    }
}
