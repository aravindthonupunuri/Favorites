package com.tgt.shoppinglist.api

import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.lib.api.transport.ListItemMetaDataTO
import com.tgt.lists.lib.api.transport.ListItemMultiDeleteResponseTO
import com.tgt.lists.lib.api.transport.UserItemMetaDataTO
import com.tgt.lists.lib.api.util.*
import com.tgt.shoppinglist.api.util.TestUtilConstants
import com.tgt.shoppinglist.util.BaseKafkaFunctionalTest
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.test.annotation.MicronautTest

import java.time.LocalDateTime

import static com.tgt.shoppinglist.util.DataProvider.*

@MicronautTest
class DeleteMultipleListItemFunctionalTest extends BaseKafkaFunctionalTest {

    String guestId = "1234"

    def "test delete multiple list item based on ItemIncludeFields"() {
        def cartId = UUID.randomUUID()
        def completedCartId = UUID.randomUUID()

        def pendingCartItemId1 = UUID.randomUUID()
        def pendingCartItemId2 = UUID.randomUUID()

        def completedCartItemId1 = UUID.randomUUID()
        def completedCartItemId2 = UUID.randomUUID()

        def uri = Constants.LISTS_BASEPATH + "/" + cartId + "/list_items?include_items=ALL"

        def pendingCartResponse = cartDataProvider.getCartResponse(cartId, guestId, null)
        def completedCartResponse = cartDataProvider.getCartResponse(completedCartId, guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])
        def cartLists = [completedCartResponse]

        ListItemMetaDataTO pendingItemMetaData = new ListItemMetaDataTO(null, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(cartId, pendingCartItemId1, "53692059",
            "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
            null, LocalDateTime.now(), null)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(cartId, pendingCartItemId2, "53692060",
            "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
            null, LocalDateTime.now(), null)
        def pendingCartContentsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])

        def pendingCartItemDeleteResponse = cartDataProvider.getDeleteMultiCartItemsResponse(cartId, [pendingCartItemId1,pendingCartItemId2], [])

        ListItemMetaDataTO completedItemMetaData = new ListItemMetaDataTO(null, ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(cartId, pendingCartItemId1, "53692061",
            "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
            null, LocalDateTime.now(), null)
        def completedCartItemResponse2 = cartDataProvider.getCartItemResponse(cartId, pendingCartItemId2, "53692062",
            "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
            null, LocalDateTime.now(), null)
        def completedCartContentsResponse = cartDataProvider.getCartContentsResponse(completedCartResponse, [completedCartItemResponse1, completedCartItemResponse2])

        def completedCartItemDeleteResponse = cartDataProvider.getDeleteMultiCartItemsResponse(completedCartId, [completedCartItemId1, completedCartItemId2], [])

        when:
        HttpResponse<ListItemMultiDeleteResponseTO> listResponse = client.toBlocking().exchange(
            HttpRequest.DELETE(uri).headers(getHeaders(guestId)), ListItemMultiDeleteResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.OK
        actual.listId == cartId
        actual.successListItemIds.size() == 4

        //*********************************** delete completed cart items **********************************************
        1 * mockServer.get({ path -> path.contains(getCartContentURI(cartId)) }, _) >> [status: 200, body: pendingCartContentsResponse]
        1 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=1234&cart_state=PENDING") }, _) >> [status: 200, body: cartLists]
        1 * mockServer.get({ path -> path.contains(getCartContentURI(completedCartId)) }, _) >> [status: 200, body: completedCartContentsResponse]
        1 * mockServer.post({ path -> path.contains("/carts/v4/delete_multi_cart_items") }, _, { headers -> checkHeaders(headers) }) >> [status: 200, body: completedCartItemDeleteResponse]

        //*********************************** delete pending cart items ************************************************
        1 * mockServer.get({ path -> path.contains(getCartContentURI(cartId)) }, _) >> [status: 200, body: pendingCartContentsResponse]
        1 * mockServer.post({ path -> path.contains("/carts/v4/delete_multi_cart_items") }, _, { headers -> checkHeaders(headers) }) >> [status: 200, body: pendingCartItemDeleteResponse]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test delete multiple list item based on itemIds passed as request"() {
        def cartId = UUID.randomUUID()
        def completedCartId = UUID.randomUUID()

        def pendingCartItemId1 = UUID.randomUUID()
        def pendingCartItemId2 = UUID.randomUUID()

        def completedCartItemId1 = UUID.randomUUID()
        def completedCartItemId2 = UUID.randomUUID()

        def uri = Constants.LISTS_BASEPATH + "/" + cartId + "/list_items?include_items=ALL&itemIds=" + pendingCartItemId1.toString() + ',' + pendingCartItemId2.toString() + ',' + completedCartItemId1.toString() + ',' + completedCartItemId2.toString()

        def pendingCartResponse = cartDataProvider.getCartResponse(cartId, guestId, null)
        def completedCartResponse = cartDataProvider.getCartResponse(completedCartId, guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, [(TestUtilConstants.LIST_TYPE): "SHOPPING"])
        def cartLists = [completedCartResponse]

        ListItemMetaDataTO pendingItemMetaData = new ListItemMetaDataTO(null, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        def pendingCartItemResponse1 = cartDataProvider.getCartItemResponse(cartId, pendingCartItemId1, "53692059",
            "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
            null, LocalDateTime.now(), null)
        def pendingCartItemResponse2 = cartDataProvider.getCartItemResponse(cartId, pendingCartItemId2, "53692060",
            "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(pendingItemMetaData, new UserItemMetaDataTO()),
            null, LocalDateTime.now(), null)
        def pendingCartContentsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [pendingCartItemResponse1, pendingCartItemResponse2])

        def pendingCartItemDeleteResponse = cartDataProvider.getDeleteMultiCartItemsResponse(cartId, [pendingCartItemId1], [pendingCartItemId2])

        ListItemMetaDataTO completedItemMetaData = new ListItemMetaDataTO(null, ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)
        def completedCartItemResponse1 = cartDataProvider.getCartItemResponse(cartId, completedCartItemId1, "53692061",
            "title", 3, "notes1", 0, 0, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
            null, LocalDateTime.now(), null)
        def completedCartItemResponse2 = cartDataProvider.getCartItemResponse(cartId, completedCartItemId2, "53692062",
            "title", 3, "notes2", 0, 0, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(completedItemMetaData, new UserItemMetaDataTO()),
            null, LocalDateTime.now(), null)
        def completedCartContentsResponse = cartDataProvider.getCartContentsResponse(pendingCartResponse, [completedCartItemResponse1, completedCartItemResponse2])
        def completedCartItemDeleteResponse = cartDataProvider.getDeleteMultiCartItemsResponse(completedCartId, [completedCartItemId1, completedCartItemId2], [])

        when:
        HttpResponse<ListItemMultiDeleteResponseTO> listResponse = client.toBlocking().exchange(
            HttpRequest.DELETE(uri).headers(getHeaders(guestId)), ListItemMultiDeleteResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.OK
        actual.listId == cartId
        actual.successListItemIds.size() == 3
        actual.failedListItemIds.size() == 1
        actual.failedListItemIds.contains(pendingCartItemId2)

        //*********************************** delete items from pending cart  ******************************************
        2 * mockServer.get({ path -> path.contains(getCartContentURI(cartId)) }, _) >> [status: 200, body: pendingCartContentsResponse]
        1 * mockServer.post({ path -> path.contains("/carts/v4/delete_multi_cart_items") }, _, { headers -> checkHeaders(headers) }) >> [status: 200, body: pendingCartItemDeleteResponse]

        //*********************************** delete failed items from completed cart **********************************
        1 * mockServer.get({ path -> path.contains("/carts/v4/?guest_id=1234&cart_state=PENDING") }, _) >> [status: 200, body: cartLists]
        1 * mockServer.get({ path -> path.contains(getCartContentURI(completedCartId)) }, _) >> [status: 200, body: completedCartContentsResponse]
        1 * mockServer.post({ path -> path.contains("/carts/v4/delete_multi_cart_items") }, _, { headers -> checkHeaders(headers) }) >> [status: 200, body: completedCartItemDeleteResponse]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }
}
