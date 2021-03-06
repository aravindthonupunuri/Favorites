package com.tgt.favorites.api

import com.tgt.favorites.transport.FavoriteListItemPostResponseTO
import com.tgt.favorites.util.BaseKafkaFunctionalTest
import com.tgt.lists.lib.api.transport.ListItemMetaDataTO
import com.tgt.lists.lib.api.transport.ListItemResponseTO
import com.tgt.lists.lib.api.transport.UserItemMetaDataTO
import com.tgt.favorites.util.FavoriteConstants
import com.tgt.lists.lib.api.util.ItemType
import com.tgt.lists.lib.api.util.LIST_CHANNEL
import com.tgt.lists.lib.api.util.LIST_ITEM_STATE
import groovy.json.JsonOutput
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MicronautTest

import java.time.LocalDateTime

import static com.tgt.favorites.util.DataProvider.*

@MicronautTest
class CreateFavoriteListItemFunctionalTest extends BaseKafkaFunctionalTest {

    String guestId = "1234"

    def "test create list item integrity"() {
        def listId = UUID.randomUUID()
        def itemId = UUID.randomUUID()
        def uri = FavoriteConstants.BASEPATH + "/" + listId + "/list_items?" + "location_id=1375"
        def listItemRequest =
            [
                "item_type": ItemType.TCIN,
                "channel": LIST_CHANNEL.WEB,
                "tcin"     : "53692059",
                "location_id" : 1375L
            ]
        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)

        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, itemId, "1234", listItemRequest.tcin,
            "itemTitle", "itemNote", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))
        def addcartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse])

        when:
        HttpResponse<ListItemResponseTO> favouritesListItemResponse = client.toBlocking().exchange(
            HttpRequest.POST(uri, JsonOutput.toJson(listItemRequest)).headers(getHeaders(guestId)), FavoriteListItemPostResponseTO)
        def actualStatus = favouritesListItemResponse.status()
        def actual = favouritesListItemResponse.body()

        then:
        actualStatus == HttpStatus.CREATED

        actual.listItemId == cartItemResponse.cartItemId
        actual.tcin == cartItemResponse.tcin
        actual.itemTitle == cartItemResponse.tenantItemName
        actual.itemNote == cartItemResponse.notes
        actual.itemType == itemMetaData1.itemType

        2 * mockServer.get({ path -> path.contains(getCartContentURI(listId))}, _) >> [status: 200, body: cartContentsResponse]
        1 * mockServer.post({ path -> path.contains("/carts/v4/multi_cart_items")},_,{ headers -> checkHeaders(headers) }) >> [status: 200, body: addcartContentsResponse]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test create list item when max pending item count has reached"() {
        def listId = UUID.randomUUID()
        def itemId1 = UUID.randomUUID()
        def itemId2 = UUID.randomUUID()
        def itemId3 = UUID.randomUUID()
        def uri = FavoriteConstants.BASEPATH + "/" + listId + "/list_items?" + "location_id=1375"
        def listItemRequest =
            [
                "item_type": ItemType.TCIN,
                "channel": LIST_CHANNEL.WEB,
                "tcin"     : "53692059"
            ]
        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)

        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, null)

        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(ItemType.TCIN, LIST_ITEM_STATE.PENDING)

        def cartItemResponse1 = cartDataProvider.getCartItemResponse(listId, itemId1, "1234", "53692060",
            "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()),
            null, LocalDateTime.now(), null)

        def cartItemResponse2 = cartDataProvider.getCartItemResponse(listId, itemId2, "1234", "53692061",
            "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()),
            null, LocalDateTime.now(), null)
        def cartItemResponse3 = cartDataProvider.getCartItemResponse(listId, itemId3, "1234", "53692062",
            "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()),
            null, LocalDateTime.now(), null)

        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse1, cartItemResponse2, cartItemResponse3])

        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, itemId1, "1234", listItemRequest.tcin,
            "itemTitle", "itemNote", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))
        def addcartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse])

        when:
        client.toBlocking().exchange(HttpRequest.POST(uri,
            JsonOutput.toJson(listItemRequest)).headers(getHeaders(guestId)), FavoriteListItemPostResponseTO)

        then:
        def error = thrown(HttpClientResponseException)
        error.status == HttpStatus.BAD_REQUEST

        2 * mockServer.get({ path -> path.contains(getCartContentURI(listId))}, _) >> [status: 200, body: cartContentsResponse]
        0 * mockServer.post({ path -> path.contains("/carts/v4/multi_cart_items")},_,{ headers -> checkHeaders(headers) }) >> [status: 200, body: addcartContentsResponse]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }
}
