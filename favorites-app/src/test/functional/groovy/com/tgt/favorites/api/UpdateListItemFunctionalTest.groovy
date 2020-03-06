package com.tgt.favorites.api

import com.tgt.favorites.util.BaseKafkaFunctionalTest
import com.tgt.lists.lib.api.transport.ListItemMetaDataTO
import com.tgt.lists.lib.api.transport.ListItemResponseTO
import com.tgt.lists.lib.api.transport.UserItemMetaDataTO
import com.tgt.lists.lib.api.util.Constants
import com.tgt.lists.lib.api.util.ItemType
import com.tgt.lists.lib.api.util.LIST_ITEM_STATE
import groovy.json.JsonOutput
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MicronautTest

import static com.tgt.favorites.util.DataProvider.*

@MicronautTest
class UpdateListItemFunctionalTest extends BaseKafkaFunctionalTest {

    String guestId = "1234"
    String listId = "aaaaaaaa-1111-bbbb-2222-cccccccccccc"
    String listItemId = "40689bf0-c56f-11e9-b988-b394861ac09e"

    def "test Update list item integrity"() {
        def uri = Constants.LISTS_BASEPATH + "/" + listId + "/list_items/" + listItemId+ "?location_id=1375"
        def listItemUpdateRequest =
            [
                "item_title":"updated item title",
                "item_note":"updated item note"
            ]

        def cartResponse = cartDataProvider.getCartResponse(UUID.fromString(listId), guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(UUID.fromString(listId), UUID.fromString(listItemId), "1234",
            "itemTitle", 1, "itemNote",10, 10, "Stand Alone", "READY",
            "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        def updatedCartItemResponse = cartDataProvider.getCartItemResponse(UUID.fromString(listId), UUID.fromString(listItemId), "1234",
            "updated item title", 1, "updated item note",10, 10, "Stand Alone", "READY",
            "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)


        def updatedListItemMetaData = cartDataProvider.getListItemMetaDataFromCart(updatedCartItemResponse.metadata)

        when:
        HttpResponse<ListItemResponseTO> listResponse = client.toBlocking().exchange(
            HttpRequest.PUT(uri, JsonOutput.toJson(listItemUpdateRequest)).headers(getHeaders(guestId)), ListItemResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.OK

        actual.listItemId == updatedCartItemResponse.cartItemId
        actual.tcin == updatedCartItemResponse.tcin
        actual.itemTitle == updatedCartItemResponse.tenantItemName
        actual.itemNote == updatedCartItemResponse.notes
        actual.price == updatedCartItemResponse.price
        actual.listPrice == updatedCartItemResponse.listPrice
        actual.images == updatedCartItemResponse.images
        actual.itemType == updatedListItemMetaData.itemType
        actual.itemState == updatedListItemMetaData.itemState

        1 * mockServer.get({ path -> path.contains(getCartContentURI(listId))}, _) >> [status: 200, body: cartContentsResponse]    // Authorization filter call
        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/40689bf0-c56f-11e9-b988-b394861ac09e") }, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartItemResponse]  // get cart item call
        1 * mockServer.put({ path -> path.contains("/carts/v4/cart_items/40689bf0-c56f-11e9-b988-b394861ac09e")},_,{ headers -> checkHeaders(headers) }) >> [status: 200, body: updatedCartItemResponse] // update call to update the item

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test update item state from pending to completed"() {
        def uri = Constants.LISTS_BASEPATH + "/" + listId + "/list_items/" + listItemId+ "?location_id=1375"
        def listItemUpdateRequest =
            [
                "item_title":"updated item title",
                "item_note":"updated item note",
                "item_state" : LIST_ITEM_STATE.COMPLETED.toString()
            ]

        def cartResponse = cartDataProvider.getCartResponse(UUID.fromString(listId), guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(UUID.fromString(listId), UUID.fromString(listItemId), "1234",
            "itemTitle", 1, "itemNote",10, 10, "Stand Alone", "READY",
            "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        def updatedCartItemResponse = cartDataProvider.getCartItemResponse(UUID.fromString(listId), UUID.fromString(listItemId), "1234",
            "updated item title", 1, "updated item note",10, 10, "Stand Alone", "READY",
            "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        when:
        HttpResponse<ListItemResponseTO> listResponse = client.toBlocking().exchange(
            HttpRequest.PUT(uri, JsonOutput.toJson(listItemUpdateRequest)).headers(getHeaders(guestId)), ListItemResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.OK

        actual.listItemId == updatedCartItemResponse.cartItemId
        actual.tcin == updatedCartItemResponse.tcin
        actual.itemTitle == updatedCartItemResponse.tenantItemName
        actual.itemNote == updatedCartItemResponse.notes
        actual.price == updatedCartItemResponse.price
        actual.listPrice == updatedCartItemResponse.listPrice
        actual.images == updatedCartItemResponse.images
        actual.itemState == LIST_ITEM_STATE.COMPLETED

        1 * mockServer.get({ path -> path.contains(getCartContentURI(listId))}, _) >> [status: 200, body: cartContentsResponse]  // Authorization filter call
        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/40689bf0-c56f-11e9-b988-b394861ac09e") }, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartItemResponse] // get cart item call
        1 * mockServer.put({ path -> path.contains("/carts/v4/cart_items/40689bf0-c56f-11e9-b988-b394861ac09e")},_,{ headers -> checkHeaders(headers) }) >> [status: 200, body: updatedCartItemResponse] // update call to update the item

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test Edit list item - updated requested quantity not present - would default it to 1"() {
        def uri = Constants.LISTS_BASEPATH + "/" + listId + "/list_items/" + listItemId+ "?location_id=1375"
        def listItemUpdateRequest =
            [
                "item_title":"updated item title",
                "item_note":"updated item note"
            ]

        def cartResponse = cartDataProvider.getCartResponse(UUID.fromString(listId), guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def cartItemResponse = cartDataProvider.getCartItemResponse(UUID.fromString(listId), UUID.fromString(listItemId), "1234",
            "itemTitle", 1, "itemNote",10, 10, "Stand Alone", "READY",
            "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)

        def updatedCartItemResponse = cartDataProvider.getCartItemResponse(UUID.fromString(listId), UUID.fromString(listItemId), "1234",
            "updated item title", 1, "updated item note",10, 10, "Stand Alone", "READY",
            "some-url", "some-image",
            cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()), null, null, null)


        def updatedListItemMetaData = cartDataProvider.getListItemMetaDataFromCart(updatedCartItemResponse.metadata)

        when:
        HttpResponse<ListItemResponseTO> listResponse = client.toBlocking().exchange(
            HttpRequest.PUT(uri, JsonOutput.toJson(listItemUpdateRequest)).headers(getHeaders(guestId)), ListItemResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.OK

        actual.listItemId == updatedCartItemResponse.cartItemId
        actual.tcin == updatedCartItemResponse.tcin
        actual.itemTitle == updatedCartItemResponse.tenantItemName
        actual.itemNote == updatedCartItemResponse.notes
        actual.price == updatedCartItemResponse.price
        actual.listPrice == updatedCartItemResponse.listPrice
        actual.images == updatedCartItemResponse.images
        actual.itemType == updatedListItemMetaData.itemType
        actual.itemState == updatedListItemMetaData.itemState
        actual.requestedQuantity == updatedCartItemResponse.requestedQuantity

        1 * mockServer.get({ path -> path.contains(getCartContentURI(listId))}, _) >> [status: 200, body: cartContentsResponse]
        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/40689bf0-c56f-11e9-b988-b394861ac09e") }, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartItemResponse]
        1 * mockServer.put({ path -> path.contains("/carts/v4/cart_items/40689bf0-c56f-11e9-b988-b394861ac09e")},_,{ headers -> checkHeaders(headers) }) >> [status: 200, body: updatedCartItemResponse]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test Edit list item - update requested quantity to 0 throws an error"() {
        def uri = Constants.LISTS_BASEPATH + "/" + listId + "/list_items/" + listItemId+ "?location_id=1375"
        def listItemUpdateRequest =
            [
                "item_title":"updated item title",
                "item_note":"updated item note",
                "requested_quantity" : 0
            ]

        when:
        HttpResponse<ListItemResponseTO> listResponse = client.toBlocking().exchange(
            HttpRequest.PUT(uri, JsonOutput.toJson(listItemUpdateRequest)).headers(getHeaders(guestId)), ListItemResponseTO)

        then:
        thrown(HttpClientResponseException)


        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }
}
