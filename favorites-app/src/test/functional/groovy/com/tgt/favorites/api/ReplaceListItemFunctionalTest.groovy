package com.tgt.favorites.api

import com.tgt.favorites.util.BaseKafkaFunctionalTest
import com.tgt.lists.lib.api.transport.ListItemMetaDataTO
import com.tgt.lists.lib.api.transport.ListItemResponseTO
import com.tgt.lists.lib.api.transport.UserItemMetaDataTO
import com.tgt.lists.lib.api.util.Constants
import com.tgt.lists.lib.api.util.ItemType
import com.tgt.lists.lib.api.util.LIST_ITEM_STATE
import com.tgt.lists.msgbus.ListsMessageBusProducer
import com.tgt.lists.msgbus.event.EventLifecycleNotificationProvider
import groovy.json.JsonOutput
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MicronautTest
import io.micronaut.test.annotation.MockBean
import spock.lang.Shared

import javax.inject.Inject

import static com.tgt.favorites.util.DataProvider.*

@MicronautTest
class ReplaceListItemFunctionalTest extends BaseKafkaFunctionalTest {

    String guestId = "1234"

    @Shared
    @Inject
    EventLifecycleNotificationProvider eventNotificationsProvider

    @MockBean(ListsMessageBusProducer.class)
    ListsMessageBusProducer createMockListsMessageBusProducer() {
        return newMockMsgbusKafkaProducerClient(eventNotificationsProvider)
    }

    def "test replace list item integrity"() {
        def listId = UUID.randomUUID()
        def sourceItemId = UUID.randomUUID()
        def uri = Constants.LISTS_BASEPATH + "/" + listId + "/replace_list_item/" + sourceItemId + "?location_id=1375"
        def cartItemDeleteResponse = ["cart_id" : listId, "cart_item_id" : sourceItemId]
        def listItemRequest =
            [
                "item_title" : "itemtitle",
                "item_type"  : ItemType.TCIN,
                "item_note"  : "itemnote",
                "tcin"       : "1234",
                "requested_quantity": 1
            ]
        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)

        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, sourceItemId, listItemRequest.tcin,
            listItemRequest.item_title, listItemRequest.item_note, listItemRequest.requested_quantity, 10, 10, "READY",
            "some-url", "some-image", "primary-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))

        when:
        HttpResponse<ListItemResponseTO> listItemResponse = client.toBlocking().exchange(
            HttpRequest.PUT(uri, JsonOutput.toJson(listItemRequest)).headers(getHeaders(guestId)), ListItemResponseTO)
        def actualStatus = listItemResponse.status()
        def actual = listItemResponse.body()

        then:
        actualStatus == HttpStatus.OK
        actual.tcin == cartItemResponse.tcin
        actual.itemTitle == cartItemResponse.tenantItemName
        actual.itemNote == cartItemResponse.notes
        actual.requestedQuantity == cartItemResponse.requestedQuantity
        actual.itemType == listItemRequest.item_type

        2 * mockServer.get({ path -> path.contains(getCartContentURI(listId)) }, _) >> [status: 200, body: cartContentsResponse]
        1 * mockServer.post({ path -> path.contains("/carts/v4/cart_items") }, _, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartItemResponse]
        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_items") }, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartItemResponse]
        1 * mockServer.post({ path -> path.contains("/carts/v4/deleted_cart_items")},_, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartItemDeleteResponse]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test replace list item when replace item is of type OFFER"() {
        def listId = UUID.randomUUID()
        def sourceItemId = UUID.randomUUID()
        def uri = Constants.LISTS_BASEPATH + "/" + listId + "/replace_list_item/" + sourceItemId + "?location_id=1375"
        def listItemRequest =
            [
                "item_title" : "itemtitle",
                "item_type"  : ItemType.OFFER,
                "item_note"  : "itemnote",
                "tcin"       : "1234",
                "requested_quantity": 1
            ]

        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        when:
        client.toBlocking().exchange(
            HttpRequest.PUT(uri, JsonOutput.toJson(listItemRequest)).headers(getHeaders(guestId)), ListItemResponseTO)

        then:
        thrown(HttpClientResponseException)

        1 * mockServer.get({ path -> path.contains(getCartContentURI(listId)) }, _) >> [status: 200, body: cartContentsResponse]
        0 * mockServer.post({ path -> path.contains("/carts/v4/cart_items") }, _, { headers -> checkHeaders(headers) })
        0 * mockServer.post({ path -> path.contains("/carts/v4/deleted_cart_items")},_, { headers -> checkHeaders(headers) })

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }
}

