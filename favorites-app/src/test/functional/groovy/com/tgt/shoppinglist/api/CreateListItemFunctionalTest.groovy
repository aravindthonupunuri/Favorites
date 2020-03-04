package com.tgt.shoppinglist.api

import com.tgt.lists.lib.api.transport.ListItemMetaDataTO
import com.tgt.lists.lib.api.transport.ListItemResponseTO
import com.tgt.lists.lib.api.transport.UserItemMetaDataTO
import com.tgt.lists.lib.api.util.Constants
import com.tgt.lists.lib.api.util.ItemType
import com.tgt.lists.lib.api.util.LIST_ITEM_STATE
import com.tgt.shoppinglist.api.util.TestUtilConstants
import com.tgt.shoppinglist.util.BaseKafkaFunctionalTest
import groovy.json.JsonOutput
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MicronautTest

import java.time.LocalDateTime

import static com.tgt.shoppinglist.util.DataProvider.*

@MicronautTest
class CreateListItemFunctionalTest extends BaseKafkaFunctionalTest {

    String guestId = "1234"

    def "test create list item integrity"() {
        def listId = UUID.randomUUID()
        def itemId = UUID.randomUUID()
        def uri = Constants.LISTS_BASEPATH + "/" + listId + "/list_items?" + "location_id=1375"
        def cartwheelItemUri = "/ssa/cwlservice/api/v16/items/offers?sort=relevance_desc&storeId=1375&tcins=53692059"
        def sicUri = "/stores"
        def listItemRequest =
            [
                "item_type": ItemType.TCIN,
                "tcin"     : "53692059",
                "location_id" : 1375L
            ]
        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)

        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, itemId, listItemRequest.tcin,
            "itemTitle", "itemNote", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))

        def itemLocations = sicDataProvider.getItemLocations(["53692059"])
        def cartWheelItemOfferList = cartWheelDataProvider.getCartWheelItemOffers(2)
        def promoOfferCount = cartItemResponse.getEligibleDiscounts().size()
        def cartWheelOfferCount = cartWheelItemOfferList.size()

        when:
        HttpResponse<ListItemResponseTO> listResponse = client.toBlocking().exchange(
            HttpRequest.POST(uri, JsonOutput.toJson(listItemRequest)).headers(getHeaders(guestId)), ListItemResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.CREATED

        actual.listItemId == cartItemResponse.cartItemId
        actual.tcin == cartItemResponse.tcin
        actual.itemTitle == cartItemResponse.tenantItemName
        actual.itemNote == cartItemResponse.notes
        actual.price == cartItemResponse.price
        actual.listPrice == cartItemResponse.listPrice
        actual.images == cartItemResponse.images
        actual.itemType == itemMetaData1.itemType
        actual.itemExpiration == itemMetaData1.itemExpiration
        actual.requestedQuantity == cartItemResponse.requestedQuantity
        actual.offerCount == promoOfferCount + cartWheelOfferCount

        2 * mockServer.get({ path -> path.contains(getCartContentURI(listId))}, _) >> [status: 200, body: cartContentsResponse]
        1 * mockServer.post({ path -> path.contains("/carts/v4/cart_items")},_,{ headers -> checkHeaders(headers) }) >> [status: 200, body: cartItemResponse]
        1 * mockServer.get({ path -> path.contains(sicUri) }, { headers -> checkHeaders(headers) }) >> [status: 200, body: itemLocations]
        1 * mockServer.get({ path -> path.contains(cartwheelItemUri) }, { headers -> checkCartWheelApiHeaders(headers) }) >> [status: 200, body: cartWheelItemOfferList]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test create list item integrity with multiple pre existing matching items"() {
        def listId = UUID.randomUUID()
        def itemId1 = UUID.randomUUID()
        def itemId2 = UUID.randomUUID()
        def cartwheelItemUri = "/ssa/cwlservice/api/v16/items/offers?sort=relevance_desc&storeId=1375&tcins=53692059"
        def sicUri = "/stores"
        def uri = Constants.LISTS_BASEPATH + "/" + listId + "/list_items?" + "location_id=1375"
        def listItemRequest =
            [
                (TestUtilConstants.ITEM_TYPE): ItemType.TCIN,
                "tcin"                       : "53692059",
                "requested_quantity"         : 2,
                "item_note"                  : "itemNote1",
                "location_id"                : 1375L
            ]

        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(null, ItemType.TCIN, LIST_ITEM_STATE.PENDING)

        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def cartItemResponse1 = cartDataProvider.getCartItemResponse(listId, itemId1, "53692059",
            "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()),
            null, LocalDateTime.now(), null)
        def cartItemResponse2 = cartDataProvider.getCartItemResponse(listId, itemId2, "53692059",
            "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()),
            null, LocalDateTime.now(), null)
        def existingItemList = [cartItemResponse1, cartItemResponse2]
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, existingItemList)

        def multiCartItemDeleteResponse = cartDataProvider.getDeleteMultiCartItemsResponse(listId, [itemId2], [])

        def updatedRequestedQuantity = listItemRequest.requested_quantity + cartItemResponse1.requestedQuantity + cartItemResponse2.requestedQuantity
        def updatedItemNote = listItemRequest.item_note + cartItemResponse1.notes + cartItemResponse2.notes

        def updatedCartItemResponse = cartDataProvider.getCartItemResponse(listId, itemId1, "53692059",
            "title", updatedRequestedQuantity, updatedItemNote, 0, 0, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()),
            null, LocalDateTime.now(), null)

        def itemLocation = sicDataProvider.getItemLocations(["53692059"])
        def cartWheelItemOfferList = cartWheelDataProvider.getCartWheelItemOffers(2)
        def promoOfferCount = cartItemResponse1.getEligibleDiscounts().size()
        def cartWheelOfferCount = cartWheelItemOfferList.size()

        when:
        HttpResponse<ListItemResponseTO> listResponse = client.toBlocking().exchange(
            HttpRequest.POST(uri, JsonOutput.toJson(listItemRequest)).headers(getHeaders(guestId)), ListItemResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.CREATED

        actual.listItemId == updatedCartItemResponse.cartItemId
        actual.tcin == updatedCartItemResponse.tcin
        actual.itemTitle == updatedCartItemResponse.tenantItemName
        actual.itemNote == updatedCartItemResponse.notes
        actual.price == updatedCartItemResponse.price
        actual.listPrice == updatedCartItemResponse.listPrice
        actual.images == updatedCartItemResponse.images
        actual.requestedQuantity == updatedCartItemResponse.requestedQuantity
        actual.offerCount == promoOfferCount + cartWheelOfferCount

        2 * mockServer.get({ path -> path.contains(getCartContentURI(listId))}, _) >> [status: 200, body: cartContentsResponse]
        1 * mockServer.get({ path -> path.contains("/carts/v4/cart_items/")}, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartItemResponse1]
        1 * mockServer.put({ path -> path.contains("/carts/v4/cart_items/")},_,{ headers -> checkHeaders(headers) }) >> [status: 200, body: updatedCartItemResponse]
        1 * mockServer.post({ path -> path.contains("/carts/v4/delete_multi_cart_items") }, _, { headers -> checkHeaders(headers) }) >> [status: 200, body: multiCartItemDeleteResponse]
        1 * mockServer.get({ path -> path.contains(sicUri) }, { headers -> checkHeaders(headers) }) >> [status: 200, body: itemLocation]
        1 * mockServer.get({ path -> path.contains(cartwheelItemUri) }, { headers -> checkCartWheelApiHeaders(headers) }) >> [status: 200, body: cartWheelItemOfferList]


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
        def uri = Constants.LISTS_BASEPATH + "/" + listId + "/list_items?" + "location_id=1375"
        def listItemRequest =
            [
                "item_type": ItemType.TCIN,
                "tcin"     : "53692059"
            ]
        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)

        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, null)

        ListItemMetaDataTO itemMetaData = new ListItemMetaDataTO(null, ItemType.TCIN, LIST_ITEM_STATE.PENDING)

        def cartItemResponse1 = cartDataProvider.getCartItemResponse(listId, itemId1, "53692060",
            "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()),
            null, LocalDateTime.now(), null)

        def cartItemResponse2 = cartDataProvider.getCartItemResponse(listId, itemId2, "53692061",
            "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()),
            null, LocalDateTime.now(), null)
        def cartItemResponse3 = cartDataProvider.getCartItemResponse(listId, itemId3, "53692062",
            "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData, new UserItemMetaDataTO()),
            null, LocalDateTime.now(), null)

        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, [cartItemResponse1, cartItemResponse2, cartItemResponse3])

        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, itemId1, listItemRequest.tcin,
            "itemTitle", "itemNote", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))

        when:
        client.toBlocking().exchange(HttpRequest.POST(uri,
            JsonOutput.toJson(listItemRequest)).headers(getHeaders(guestId)), ListItemResponseTO)

        then:
        def error = thrown(HttpClientResponseException)
        error.status == HttpStatus.BAD_REQUEST

        2 * mockServer.get({ path -> path.contains(getCartContentURI(listId))}, _) >> [status: 200, body: cartContentsResponse]
        0 * mockServer.post({ path -> path.contains("/carts/v4/cart_items")},_,{ headers -> checkHeaders(headers) }) >> [status: 200, body: cartItemResponse]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test create list item when requested quantity is null"() {
        def listId = UUID.randomUUID()
        def itemId = UUID.randomUUID()
        def uri = Constants.LISTS_BASEPATH + "/" + listId + "/list_items?" + "location_id=1375"
        def listItemRequest =
            [
                "item_type": ItemType.TCIN,
                "tcin"     : "53692059",
            ]
        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)

        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        def cartItemResponse = cartDataProvider.getCartItemResponse(listId, itemId, listItemRequest.tcin,
            "itemTitle", "itemNote", null, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))

        def listItem1MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse.metadata)

        when:
        HttpResponse<ListItemResponseTO> listResponse = client.toBlocking().exchange(
            HttpRequest.POST(uri, JsonOutput.toJson(listItemRequest)).headers(getHeaders(guestId)), ListItemResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.CREATED

        actual.listItemId == cartItemResponse.cartItemId
        actual.tcin == cartItemResponse.tcin
        actual.itemTitle == cartItemResponse.tenantItemName
        actual.itemNote == cartItemResponse.notes
        actual.price == cartItemResponse.price
        actual.listPrice == cartItemResponse.listPrice
        actual.images == cartItemResponse.images
        actual.itemType == listItem1MetaData.itemType
        actual.itemExpiration == listItem1MetaData.itemExpiration
        actual.requestedQuantity == 1

        2 * mockServer.get({ path -> path.contains(getCartContentURI(listId))}, _) >> [status: 200, body: cartContentsResponse]
        1 * mockServer.post({ path -> path.contains("/carts/v4/cart_items")},_,{ headers -> checkHeaders(headers) }) >> [status: 200, body: cartItemResponse]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test create list item when requested quantity is set to 0 throws an exception"() {
        def listId = UUID.randomUUID()
        def uri = "lists/v4/" + listId + "/list_items"
        def listItemRequest =
            [
                "item_type": ItemType.TCIN,
                "tcin"     : "53692059",
                "requested_quantity" : 0
            ]

        when:
        client.toBlocking().exchange(
            HttpRequest.POST(uri, JsonOutput.toJson(listItemRequest)).headers(getHeaders(guestId)), ListItemResponseTO)

        then:
        thrown(HttpClientResponseException)

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }
}
