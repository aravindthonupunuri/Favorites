package com.tgt.favorites.api

import com.tgt.favorites.util.BaseKafkaFunctionalTest
import com.tgt.lists.lib.api.transport.ListItemMetaDataTO
import com.tgt.lists.lib.api.transport.ListItemMultiAddResponseTO
import com.tgt.lists.lib.api.transport.UserItemMetaDataTO
import com.tgt.lists.lib.api.util.Constants
import com.tgt.lists.lib.api.util.ItemType
import com.tgt.lists.lib.api.util.LIST_ITEM_STATE
import com.tgt.favorites.api.util.CartDataProvider
import groovy.json.JsonOutput
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.test.annotation.MicronautTest

import java.time.LocalDateTime

import static com.tgt.favorites.util.DataProvider.*

@MicronautTest
class AddMultipleListItemFunctionalTest extends BaseKafkaFunctionalTest {

    CartDataProvider cartDataProvider = new CartDataProvider()
    String guestId = "1234"

    def  "test add multiple list item integrity"() {
        def listId = UUID.randomUUID()
        def uri = Constants.LISTS_BASEPATH + "/" + listId + "/multiple_list_items?" + "location_id=1375"
        def listItemMultiAddRequest = [ "items": "1234,5678,1000" ]

        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)

        def cartItemResponse1 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID() , "1234",
            "title", 2, "notes1", 0, 0, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()),
            null, LocalDateTime.now(), null)
        def cartItemResponse2 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID() , "5678",
            "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()),
            null, LocalDateTime.now(), null)
        def cartItemResponse3 = cartDataProvider.getCartItemResponse(listId, UUID.randomUUID() , "1000",
            "title", 1, "notes3", 0, 0, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()),
            null, LocalDateTime.now(), null)
        def updatedCartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse1, cartItemResponse2, cartItemResponse3])

        when:
        HttpResponse<ListItemMultiAddResponseTO> listItemMultiAddResponse = client.toBlocking().exchange(
            HttpRequest.POST(uri, JsonOutput.toJson(listItemMultiAddRequest)).headers(getHeaders(guestId)), ListItemMultiAddResponseTO)
        def actualStatus = listItemMultiAddResponse.status()
        def actual = listItemMultiAddResponse.body()

        then:
        actualStatus == HttpStatus.CREATED

        actual.items.size() == 3
        actual.items[0].tcin == cartItemResponse1.tcin
        actual.items[1].tcin == cartItemResponse2.tcin
        actual.items[2].tcin == cartItemResponse3.tcin

        2 * mockServer.get({ path -> path.contains(getCartContentURI(listId))}, _) >> [status: 200, body: cartContentsResponse]
        1 * mockServer.post({ path -> path.contains("/carts/v4/multi_cart_items")},_,{ headers -> checkHeaders(headers) }) >> [status: 200, body: updatedCartContentsResponse]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }


}
