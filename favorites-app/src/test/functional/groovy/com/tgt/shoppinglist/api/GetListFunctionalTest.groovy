package com.tgt.shoppinglist.api

import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.item.search.client.transport.Item
import com.tgt.lists.item.search.client.transport.ItemDetails
import com.tgt.lists.item.search.client.transport.ItemSearchResponse
import com.tgt.lists.item.search.client.transport.SearchResponse
import com.tgt.lists.lib.api.transport.*
import com.tgt.lists.lib.api.util.*
import com.tgt.lists.msgbus.ListsMessageBusProducer
import com.tgt.lists.msgbus.event.EventLifecycleNotificationProvider
import com.tgt.shoppinglist.util.BaseFunctionalTest
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.test.annotation.MicronautTest
import io.micronaut.test.annotation.MockBean
import spock.lang.Shared

import javax.inject.Inject

import static com.tgt.shoppinglist.util.DataProvider.*

@MicronautTest
class GetListFunctionalTest  extends BaseFunctionalTest {

    String guestId = "1234"

    @Shared
    @Inject
    EventLifecycleNotificationProvider eventNotificationsProvider

    @MockBean(ListsMessageBusProducer.class)
    ListsMessageBusProducer createMockListsMessageBusProducer() {
        return newMockMsgbusKafkaProducerClient(eventNotificationsProvider)
    }

    def "test get list returns 206 when completed items request fail and include items field is ALL"() {
        given:
        def cartId = "fe9c3360-b44a-11e9-987d-03d970ca1c28"
        def completedCartId = UUID.randomUUID()
        def uri = Constants.LISTS_BASEPATH + "/" + cartId + "?location_id=1375" + "&start_x=41" + "&start_y=41.6" +"&start_floor=1"
        def cartUri = "/carts/v4/cart_contents/" + cartId
        def completedCartUri = "/carts/v4/cart_contents/" + completedCartId.toString()
        def itemSearchUri = "/search/v3/items/keyword_search"
        def sicUri = "/stores"
        def cartwheelItemUri = "/ssa/cwlservice/api/v16/items/offers?sort=relevance_desc&storeId=1375&tcins=1234"
        def cartwheelUri = "/ssa/cwlservice/api/v16/offers/search?q=coffee&offset=0&limit=40&sort=relevance_desc&storeId=1375"

        ListMetaDataTO metadata = new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.PENDING)
        ListMetaDataTO completedMetadata = new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.COMPLETED)
        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.GENERIC_ITEM, LIST_ITEM_STATE.PENDING)

        def cartResponse = cartDataProvider.getCartResponse(UUID.fromString(cartId), guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))
        def completedCartResponse = cartDataProvider.getCartResponse(completedCartId, guestId, cartId,
            cartDataProvider.getMetaData(completedMetadata, new UserMetaDataTO()))

        def cartItemResponse1 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), "1234",
            null, "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))
        def cartItemResponse2 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), null,
            "coffee", "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()))

        Map response = ["cart" : cartResponse, "cart_items" : [cartItemResponse1, cartItemResponse2]]

        def itemSearchResponse = new ItemSearchResponse(new SearchResponse(new Item([new ItemDetails("5678")])))
        def itemLocations = sicDataProvider.getItemLocations(["1234", "5678"])

        def cartWheelItemOfferList1 = cartWheelDataProvider.getCartWheelItemOffers(2)
        def promoOfferCount1 = cartItemResponse1.getEligibleDiscounts().size()
        def cartWheelOfferCount1 = cartWheelItemOfferList1.size()

        def cartWheelOfferList2 = cartWheelDataProvider.getCartWheelOffers(3)
        def promoOfferCount2 = cartItemResponse2.getEligibleDiscounts().size()
        def cartWheelOfferCount2 = 3

        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)
        def listItem1MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse1.metadata)
        def listItem2MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse2.metadata)

        when:
        HttpResponse<ListResponseTO> listResponse = client.toBlocking()
            .exchange(HttpRequest.GET(uri).headers(getHeaders(guestId)), ListResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.PARTIAL_CONTENT

        actual.listId == cartResponse.cartId
        actual.channel == LIST_CHANNEL.valueOf(cartResponse.cartChannel)
        actual.listTitle == cartResponse.tenantCartName
        actual.shortDescription == cartResponse.tenantCartDescription
        actual.listType == listMetaData.listType
        actual.defaultList == listMetaData.defaultList
        actual.maxPendingItemsCount == 3

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 2
        pendingItems[0].listItemId == cartItemResponse1.cartItemId
        pendingItems[0].tcin == cartItemResponse1.tcin
        pendingItems[0].itemTitle == cartItemResponse1.tenantItemName
        pendingItems[0].itemNote == cartItemResponse1.notes
        pendingItems[0].price == cartItemResponse1.price
        pendingItems[0].listPrice == cartItemResponse1.listPrice
        pendingItems[0].images == cartItemResponse1.images
        pendingItems[0].offerCount == promoOfferCount1 + cartWheelOfferCount1
        pendingItems[0].itemType == listItem1MetaData.itemType
        pendingItems[0].itemExpiration == listItem1MetaData.itemExpiration

        pendingItems[1].listItemId == cartItemResponse2.cartItemId
        pendingItems[1].tcin == cartItemResponse2.tcin
        pendingItems[1].itemTitle == cartItemResponse2.tenantItemName
        pendingItems[1].itemNote == cartItemResponse2.notes
        pendingItems[1].price == cartItemResponse2.price
        pendingItems[1].listPrice == cartItemResponse2.listPrice
        pendingItems[1].images == cartItemResponse2.images
        pendingItems[1].offerCount == promoOfferCount2 + cartWheelOfferCount2
        pendingItems[1].itemType == listItem2MetaData.itemType
        pendingItems[1].itemExpiration == listItem2MetaData.itemExpiration

        actual.completedListItems == null

        2 * mockServer.get({ path -> path.contains(cartUri) }, _) >> [status: 200, body: response]
        1 * mockServer.get({ path -> path.contains(getCartURI(guestId,cartId)) }, _) >> [status: 200, body: [completedCartResponse]]
        1 * mockServer.get({ path -> path.contains(completedCartUri) }, _) >> [status: 500]
        1 * mockServer.get({ path -> path.contains(itemSearchUri)}, _) >> [status: 200, body: itemSearchResponse]
        1 * mockServer.get({ path -> path.contains(sicUri) }, { headers -> checkHeaders(headers) }) >> [status: 200, body: itemLocations]
        1 * mockServer.get({ path -> path.contains(cartwheelItemUri) }, { headers -> checkCartWheelApiHeaders(headers) }) >> [status: 200, body: cartWheelItemOfferList1]
        1 * mockServer.get({ path -> path.contains(cartwheelUri) }, { headers -> checkCartWheelApiHeaders(headers) }) >> [status: 200, body: cartWheelOfferList2]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test get list returns 206 when getting completed cart request fail and include items field is ALL"() {
        given:
        def cartId = "fe9c3360-b44a-11e9-987d-03d970ca1c28"
        def completedCartId = UUID.randomUUID()
        def uri = Constants.LISTS_BASEPATH + "/" + cartId + "?location_id=1375"
        def cartUri = "/carts/v4/cart_contents/" + cartId
        def completedCartUri = "/carts/v4/cart_contents/" + completedCartId.toString()
        def itemSearchUri = "/search/v3/items/keyword_search"
        def sicUri = "/stores"
        def cartwheelItemUri = "/ssa/cwlservice/api/v16/items/offers?sort=relevance_desc&storeId=1375&tcins=1234"
        def cartwheelUri = "/ssa/cwlservice/api/v16/offers/search?q=coffee&offset=0&limit=40&sort=relevance_desc&storeId=1375"

        ListMetaDataTO metadata = new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.PENDING)
        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.GENERIC_ITEM, LIST_ITEM_STATE.PENDING)

        def cartResponse = cartDataProvider.getCartResponse(UUID.fromString(cartId), guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))

        def cartItemResponse1 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), "1234",
            null, "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))
        def cartItemResponse2 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), null,
            "coffee", "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()))

        Map response = ["cart" : cartResponse, "cart_items" : [cartItemResponse1, cartItemResponse2]]

        def itemSearchResponse = new ItemSearchResponse(new SearchResponse(new Item([new ItemDetails("5678")])))
        def itemLocations = sicDataProvider.getItemLocations(["1234", "5678"])

        def cartWheelItemOfferList1 = cartWheelDataProvider.getCartWheelItemOffers(2)
        def promoOfferCount1 = cartItemResponse1.getEligibleDiscounts().size()
        def cartWheelOfferCount1 = cartWheelItemOfferList1.size()

        def cartWheelOfferList2 = cartWheelDataProvider.getCartWheelOffers(3)
        def promoOfferCount2 = cartItemResponse2.getEligibleDiscounts().size()
        def cartWheelOfferCount2 = 3

        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)
        def listItem1MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse1.metadata)
        def listItem2MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse2.metadata)

        when:
        HttpResponse<ListResponseTO> listResponse = client.toBlocking()
            .exchange(HttpRequest.GET(uri).headers(getHeaders(guestId)), ListResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.PARTIAL_CONTENT

        actual.listId == cartResponse.cartId
        actual.channel == LIST_CHANNEL.valueOf(cartResponse.cartChannel)
        actual.listTitle == cartResponse.tenantCartName
        actual.shortDescription == cartResponse.tenantCartDescription
        actual.listType == listMetaData.listType
        actual.defaultList == listMetaData.defaultList
        actual.maxPendingItemsCount == 3

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 2
        pendingItems[0].listItemId == cartItemResponse1.cartItemId
        pendingItems[0].tcin == cartItemResponse1.tcin
        pendingItems[0].itemTitle == cartItemResponse1.tenantItemName
        pendingItems[0].itemNote == cartItemResponse1.notes
        pendingItems[0].price == cartItemResponse1.price
        pendingItems[0].listPrice == cartItemResponse1.listPrice
        pendingItems[0].images == cartItemResponse1.images
        pendingItems[0].offerCount == promoOfferCount1 + cartWheelOfferCount1
        pendingItems[0].itemType == listItem1MetaData.itemType
        pendingItems[0].itemExpiration == listItem1MetaData.itemExpiration

        pendingItems[1].listItemId == cartItemResponse2.cartItemId
        pendingItems[1].tcin == cartItemResponse2.tcin
        pendingItems[1].itemTitle == cartItemResponse2.tenantItemName
        pendingItems[1].itemNote == cartItemResponse2.notes
        pendingItems[1].price == cartItemResponse2.price
        pendingItems[1].listPrice == cartItemResponse2.listPrice
        pendingItems[1].images == cartItemResponse2.images
        pendingItems[1].offerCount == promoOfferCount2 + cartWheelOfferCount2
        pendingItems[1].itemType == listItem2MetaData.itemType
        pendingItems[1].itemExpiration == listItem2MetaData.itemExpiration

        actual.completedListItems == null

        2 * mockServer.get({ path -> path.contains(cartUri) }, _) >> [status: 200, body: response]
        1 * mockServer.get({ path -> path.contains(getCartURI(guestId,cartId)) }, _) >> [ status: 500 ]
        1 * mockServer.get({ path -> path.contains(itemSearchUri)}, _) >> [status: 200, body: itemSearchResponse]
        1 * mockServer.get({ path -> path.contains(sicUri) }, { headers -> checkHeaders(headers) }) >> [status: 200, body: itemLocations]
        1 * mockServer.get({ path -> path.contains(cartwheelItemUri) }, { headers -> checkCartWheelApiHeaders(headers) }) >> [status: 200, body: cartWheelItemOfferList1]
        1 * mockServer.get({ path -> path.contains(cartwheelUri) }, { headers -> checkCartWheelApiHeaders(headers) }) >> [status: 200, body: cartWheelOfferList2]
        0 * mockServer.get({ path -> path.contains(completedCartUri) }, _)

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test get list integrity 1"() {
        given:
        def cartId = "fe9c3360-b44a-11e9-987d-03d970ca1c28"
        def completedCartId = UUID.randomUUID()
        def uri = Constants.LISTS_BASEPATH + "/" + cartId + "?location_id=1375" + "&include_items=" + ItemIncludeFields.COMPLETED
        def cartUri = "/carts/v4/cart_contents/" + cartId
        def completedCartUri = "/carts/v4/cart_contents/" + completedCartId.toString()

        ListMetaDataTO metadata = new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.PENDING)
        ListMetaDataTO completedMetadata = new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.COMPLETED)
        ListItemMetaDataTO itemMetaDataCompleted = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)

        def cartResponse = cartDataProvider.getCartResponse(UUID.fromString(cartId), guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))
        def completedCartResponse = cartDataProvider.getCartResponse(completedCartId, guestId, cartId,
            cartDataProvider.getMetaData(completedMetadata, new UserMetaDataTO()))

        def cartItemResponse3 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), "1235",
            null, "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaDataCompleted, new UserItemMetaDataTO()))

        Map response = ["cart" : cartResponse]
        Map completedCartContentResponse = ["cart" : completedCartResponse, "cart_items" : [cartItemResponse3]]

        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)
        def listItem3MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse3.metadata)

        when:
        HttpResponse<ListResponseTO> listResponse = client.toBlocking()
            .exchange(HttpRequest.GET(uri).headers(getHeaders(guestId)), ListResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.OK

        actual.listId == cartResponse.cartId
        actual.channel == LIST_CHANNEL.valueOf(cartResponse.cartChannel)
        actual.listTitle == cartResponse.tenantCartName
        actual.shortDescription == cartResponse.tenantCartDescription
        actual.listType == listMetaData.listType
        actual.defaultList == listMetaData.defaultList
        actual.maxPendingItemsCount == 3

        actual.pendingListItems == null

        def completedItems = actual.completedListItems
        completedItems.size() == 1
        completedItems[0].listItemId == cartItemResponse3.cartItemId
        completedItems[0].tcin == cartItemResponse3.tcin
        completedItems[0].itemTitle == cartItemResponse3.tenantItemName
        completedItems[0].itemNote == cartItemResponse3.notes
        completedItems[0].price == cartItemResponse3.price
        completedItems[0].listPrice == cartItemResponse3.listPrice
        completedItems[0].images == cartItemResponse3.images
        completedItems[0].offerCount == 0
        completedItems[0].itemType == listItem3MetaData.itemType
        completedItems[0].itemExpiration == listItem3MetaData.itemExpiration


        2 * mockServer.get({ path -> path.contains(cartUri) }, _) >> [status: 200, body: response]
        1 * mockServer.get({ path -> path.contains(getCartURI(guestId,cartId)) }, _) >> [status: 200, body: [completedCartResponse]]
        1 * mockServer.get({ path -> path.contains(completedCartUri) }, _) >> [status: 200, body: completedCartContentResponse]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test get list integrity"() {
        given:
        def cartId = "fe9c3360-b44a-11e9-987d-03d970ca1c28"
        def completedCartId = UUID.randomUUID()
        def uri = Constants.LISTS_BASEPATH + "/" + cartId + "?location_id=1375"
        def cartUri = "/carts/v4/cart_contents/" + cartId
        def completedCartUri = "/carts/v4/cart_contents/" + completedCartId.toString()
        def itemSearchUri = "/search/v3/items/keyword_search"
        def sicUri = "/stores"
        def cartwheelItemUri1 = "/ssa/cwlservice/api/v16/items/offers?sort=relevance_desc&storeId=1375&tcins=1234"
        def cartwheelItemUri2 = "/ssa/cwlservice/api/v16/items/offers?sort=relevance_desc&storeId=1375&tcins=2345"
        def cartwheelItemUri3 = "/ssa/cwlservice/api/v16/items/offers?sort=relevance_desc&storeId=1375&tcins=3456"
        def cartwheelUri = "/ssa/cwlservice/api/v16/offers/search?q=coffee&offset=0&limit=40&sort=relevance_desc&storeId=1375"

        ListMetaDataTO metadata = new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.PENDING)
        ListMetaDataTO completedMetadata = new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.COMPLETED)
        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.GENERIC_ITEM, LIST_ITEM_STATE.PENDING)
        ListItemMetaDataTO itemMetaDataCompleted = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)

        def cartResponse = cartDataProvider.getCartResponse(UUID.fromString(cartId), guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))
        def completedCartResponse = cartDataProvider.getCartResponse(completedCartId, guestId, cartId,
            cartDataProvider.getMetaData(completedMetadata, new UserMetaDataTO()))

        def cartItemResponse1 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), "1234",
            null, "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))
        def cartItemResponse2 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), null,
            "coffee", "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()))
        def cartItemResponse3 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), "2345",
            null, "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))
        def cartItemResponse4 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), "3456",
            "coffee", "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))
        def cartItemResponse5 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), "1235",
            null, "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaDataCompleted, new UserItemMetaDataTO()))

        Map response = ["cart" : cartResponse, "cart_items" : [cartItemResponse1, cartItemResponse2, cartItemResponse3, cartItemResponse4]]
        Map completedCartContentResponse = ["cart" : completedCartResponse, "cart_items" : [cartItemResponse5]]

        def itemSearchResponse = new ItemSearchResponse(new SearchResponse(new Item([new ItemDetails("5678")])))
        def itemLocations = sicDataProvider.getItemLocations(["1234", "5678"])

        def cartWheelItemOfferList1 = cartWheelDataProvider.getCartWheelItemOffers(2)
        def promoOfferCount1 = cartItemResponse1.getEligibleDiscounts().size()
        def cartWheelOfferCount1 = cartWheelItemOfferList1.size()

        def cartWheelOfferList2 = cartWheelDataProvider.getCartWheelOffers(3)
        def promoOfferCount2 = cartItemResponse2.getEligibleDiscounts().size()
        def cartWheelOfferCount2 = 3

        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)
        def listItem1MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse1.metadata)
        def listItem2MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse2.metadata)
        def listItem3MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse5.metadata)

        when:
        HttpResponse<ListResponseTO> listResponse = client.toBlocking()
            .exchange(HttpRequest.GET(uri).headers(getHeaders(guestId)), ListResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.OK

        actual.listId == cartResponse.cartId
        actual.channel == LIST_CHANNEL.valueOf(cartResponse.cartChannel)
        actual.listTitle == cartResponse.tenantCartName
        actual.shortDescription == cartResponse.tenantCartDescription
        actual.listType == listMetaData.listType
        actual.defaultList == listMetaData.defaultList

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 4
        pendingItems[0].listItemId == cartItemResponse1.cartItemId
        pendingItems[0].tcin == cartItemResponse1.tcin
        pendingItems[0].itemTitle == cartItemResponse1.tenantItemName
        pendingItems[0].itemNote == cartItemResponse1.notes
        pendingItems[0].price == cartItemResponse1.price
        pendingItems[0].listPrice == cartItemResponse1.listPrice
        pendingItems[0].images == cartItemResponse1.images
        pendingItems[0].offerCount == promoOfferCount1 + cartWheelOfferCount1
        pendingItems[0].itemType == listItem1MetaData.itemType
        pendingItems[0].itemExpiration == listItem1MetaData.itemExpiration

        pendingItems[1].listItemId == cartItemResponse2.cartItemId
        pendingItems[1].tcin == cartItemResponse2.tcin
        pendingItems[1].itemTitle == cartItemResponse2.tenantItemName
        pendingItems[1].itemNote == cartItemResponse2.notes
        pendingItems[1].price == cartItemResponse2.price
        pendingItems[1].listPrice == cartItemResponse2.listPrice
        pendingItems[1].images == cartItemResponse2.images
        pendingItems[1].offerCount == promoOfferCount2 + cartWheelOfferCount2
        pendingItems[1].itemType == listItem2MetaData.itemType
        pendingItems[1].itemExpiration == listItem2MetaData.itemExpiration

        def completedItems = actual.completedListItems
        completedItems.size() == 1
        completedItems[0].listItemId == cartItemResponse5.cartItemId
        completedItems[0].tcin == cartItemResponse5.tcin
        completedItems[0].itemTitle == cartItemResponse5.tenantItemName
        completedItems[0].itemNote == cartItemResponse5.notes
        completedItems[0].price == cartItemResponse5.price
        completedItems[0].listPrice == cartItemResponse5.listPrice
        completedItems[0].images == cartItemResponse5.images
        completedItems[0].offerCount == 0
        completedItems[0].itemType == listItem3MetaData.itemType
        completedItems[0].itemExpiration == listItem3MetaData.itemExpiration


        2 * mockServer.get({ path -> path.contains(cartUri) }, _) >> [status: 200, body: response]
        1 * mockServer.get({ path -> path.contains(getCartURI(guestId,cartId)) }, _) >> [status: 200, body: [completedCartResponse]]
        1 * mockServer.get({ path -> path.contains(completedCartUri) }, _) >> [status: 200, body: completedCartContentResponse]
        1 * mockServer.get({ path -> path.contains(itemSearchUri)}, _) >> [status: 200, body: itemSearchResponse]
        2 * mockServer.get({ path -> path.contains(sicUri) }, { headers -> checkHeaders(headers) }) >> [status: 200, body: itemLocations]
        1 * mockServer.get({ path -> path.contains(cartwheelItemUri1) }, { headers -> checkCartWheelApiHeaders(headers) }) >> [status: 200, body: cartWheelItemOfferList1]
        1 * mockServer.get({ path -> path.contains(cartwheelItemUri2) }, { headers -> checkCartWheelApiHeaders(headers) }) >> [status: 200, body: cartWheelItemOfferList1]
        1 * mockServer.get({ path -> path.contains(cartwheelItemUri3) }, { headers -> checkCartWheelApiHeaders(headers) }) >> [status: 200, body: cartWheelItemOfferList1]
        1 * mockServer.get({ path -> path.contains(cartwheelUri) }, { headers -> checkCartWheelApiHeaders(headers) }) >> [status: 200, body: cartWheelOfferList2]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test get list integrity with sortedFieldGroups and sortOrder"() {
        given:
        def cartId = "fe9c3360-b44a-11e9-987d-03d970ca1c28"
        def completedCartId = UUID.randomUUID()
        def uri = Constants.LISTS_BASEPATH + "/" + cartId + "?location_id=1375&sort_field=ITEM_TITTLE&sort_order=ASCENDING"
        def cartUri = "/carts/v4/cart_contents/" + cartId
        def completedCartUri = "/carts/v4/cart_contents/" + completedCartId.toString()
        def itemSearchUri = "/search/v3/items/keyword_search"
        def sicUri = "/stores"
        def cartwheelItemUri = "/ssa/cwlservice/api/v16/items/offers?sort=relevance_desc&storeId=1375&tcins=1234"
        def cartwheelUri = "/ssa/cwlservice/api/v16/offers/search?q=coffee&offset=0&limit=40&sort=relevance_desc&storeId=1375"

        ListMetaDataTO metadata = new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.PENDING)
        ListMetaDataTO completedMetadata = new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.COMPLETED)
        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.PENDING)
        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.GENERIC_ITEM, LIST_ITEM_STATE.PENDING)
        ListItemMetaDataTO itemMetaDataCompleted = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.TCIN, LIST_ITEM_STATE.COMPLETED)

        def cartResponse = cartDataProvider.getCartResponse(UUID.fromString(cartId), guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))
        def completedCartResponse = cartDataProvider.getCartResponse(completedCartId, guestId, cartId,
            cartDataProvider.getMetaData(completedMetadata, new UserMetaDataTO()))

        def cartItemResponse1 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), "1234",
            "banana", "some note",1,  10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))
        def cartItemResponse2 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), null,
            "coffee", "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()))
        def cartItemResponse3 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), "1235",
            "apple", "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaDataCompleted, new UserItemMetaDataTO()))
        Map response = ["cart" : cartResponse, "cart_items" : [cartItemResponse1, cartItemResponse2]]
        Map completedCartContentResponse = ["cart" : completedCartResponse, "cart_items" : [cartItemResponse3]]

        def itemSearchResponse = new ItemSearchResponse(new SearchResponse(new Item([new ItemDetails("5678")])))
        def itemLocations = sicDataProvider.getItemLocations(["1234", "5678"])

        def cartWheelItemOfferList1 = cartWheelDataProvider.getCartWheelItemOffers(2)
        def promoOfferCount1 = cartItemResponse1.getEligibleDiscounts().size()
        def cartWheelOfferCount1 = cartWheelItemOfferList1.size()

        def cartWheelOfferList2 = cartWheelDataProvider.getCartWheelOffers(3)
        def promoOfferCount2 = cartItemResponse2.getEligibleDiscounts().size()
        def cartWheelOfferCount2 = 3

        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)
        def listItem1MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse1.metadata)
        def listItem2MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse2.metadata)
        def listItem3MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse3.metadata)

        when:
        HttpResponse<ListResponseTO> listResponse = client.toBlocking()
            .exchange(HttpRequest.GET(uri).headers(getHeaders(guestId)), ListResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.OK

        actual.listId == cartResponse.cartId
        actual.channel == LIST_CHANNEL.valueOf(cartResponse.cartChannel)
        actual.listTitle == cartResponse.tenantCartName
        actual.shortDescription == cartResponse.tenantCartDescription
        actual.listType == listMetaData.listType
        actual.defaultList == listMetaData.defaultList
        actual.maxPendingItemsCount == 3

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 2
        pendingItems[0].listItemId == cartItemResponse1.cartItemId
        pendingItems[0].tcin == cartItemResponse1.tcin
        pendingItems[0].itemTitle == cartItemResponse1.tenantItemName
        pendingItems[0].itemNote == cartItemResponse1.notes
        pendingItems[0].price == cartItemResponse1.price
        pendingItems[0].listPrice == cartItemResponse1.listPrice
        pendingItems[0].images == cartItemResponse1.images
        pendingItems[0].offerCount == promoOfferCount1 + cartWheelOfferCount1
        pendingItems[0].itemType == listItem1MetaData.itemType
        pendingItems[0].itemExpiration == listItem1MetaData.itemExpiration

        pendingItems[1].listItemId == cartItemResponse2.cartItemId
        pendingItems[1].tcin == cartItemResponse2.tcin
        pendingItems[1].itemTitle == cartItemResponse2.tenantItemName
        pendingItems[1].itemNote == cartItemResponse2.notes
        pendingItems[1].price == cartItemResponse2.price
        pendingItems[1].listPrice == cartItemResponse2.listPrice
        pendingItems[1].images == cartItemResponse2.images
        pendingItems[1].offerCount == promoOfferCount2 + cartWheelOfferCount2
        pendingItems[1].itemType == listItem2MetaData.itemType
        pendingItems[1].itemExpiration == listItem2MetaData.itemExpiration

        def completedItems = actual.completedListItems
        completedItems.size() == 1
        completedItems[0].listItemId == cartItemResponse3.cartItemId
        completedItems[0].tcin == cartItemResponse3.tcin
        completedItems[0].itemTitle == cartItemResponse3.tenantItemName
        completedItems[0].itemNote == cartItemResponse3.notes
        completedItems[0].price == cartItemResponse3.price
        completedItems[0].listPrice == cartItemResponse3.listPrice
        completedItems[0].images == cartItemResponse3.images
        completedItems[0].offerCount == 0
        completedItems[0].itemType == listItem3MetaData.itemType
        completedItems[0].itemExpiration == listItem3MetaData.itemExpiration

        2 * mockServer.get({ path -> path.contains(cartUri) }, _) >> [status: 200, body: response]
        1 * mockServer.get({ path -> path.contains(getCartURI(guestId,cartId)) }, _) >> [status: 200, body: [completedCartResponse]]
        1 * mockServer.get({ path -> path.contains(completedCartUri) }, _) >> [status: 200, body: completedCartContentResponse]
        1 * mockServer.get({ path -> path.contains(itemSearchUri)}, _) >> [status: 200, body: itemSearchResponse]
        1 * mockServer.get({ path -> path.contains(sicUri) }, { headers -> checkHeaders(headers) }) >> [status: 200, body: itemLocations]
        1 * mockServer.get({ path -> path.contains(cartwheelItemUri) }, { headers -> checkCartWheelApiHeaders(headers) }) >> [status: 200, body: cartWheelItemOfferList1]
        1 * mockServer.get({ path -> path.contains(cartwheelUri) }, { headers -> checkCartWheelApiHeaders(headers) }) >> [status: 200, body: cartWheelOfferList2]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test get list integrity with expired item"() {
        given:
        def cartId = "fe9c3360-b44a-11e9-987d-03d970ca1c28"
        def completedCartId = UUID.randomUUID()
        def uri = Constants.LISTS_BASEPATH + "/" + cartId + "?location_id=1375"
        def completedCartUri = "/carts/v4/cart_contents/" + completedCartId.toString()
        def cartUri = "/carts/v4/cart_contents/" + cartId

        ListMetaDataTO metadata = new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.PENDING)
        ListMetaDataTO completedMetadata = new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.COMPLETED)
        ListItemMetaDataTO itemMetaData1 = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.OFFER, LIST_ITEM_STATE.PENDING)
        ListItemMetaDataTO itemMetaData2 = new ListItemMetaDataTO("2018-09-16T06:35:00.000Z", ItemType.OFFER, LIST_ITEM_STATE.PENDING)
        ListItemMetaDataTO itemMetaDataCompleted = new ListItemMetaDataTO(Constants.NO_EXPIRATION, ItemType.OFFER, LIST_ITEM_STATE.COMPLETED)

        def cartResponse = cartDataProvider.getCartResponse(UUID.fromString(cartId), guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))
        def completedCartResponse = cartDataProvider.getCartResponse(completedCartId, guestId, cartId,
            cartDataProvider.getMetaData(completedMetadata, new UserMetaDataTO()))

        def cartItemResponse1 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), "1234",
            null, "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData1, new UserItemMetaDataTO()))
        def cartItemResponse2 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), null,
            "coffee", "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaData2, new UserItemMetaDataTO()))
        def cartItemResponse3 = cartDataProvider.getCartItemResponse(UUID.fromString(cartId), UUID.randomUUID(), "1235",
            null, "some note", 1, 10, 10, "Stand Alone", "READY",
            "some-url", "some-image", cartDataProvider.getItemMetaData(itemMetaDataCompleted, new UserItemMetaDataTO()))
        Map response = ["cart" : cartResponse, "cart_items" : [cartItemResponse1, cartItemResponse2]]
        Map completedCartContentResponse = ["cart" : completedCartResponse, "cart_items" : [cartItemResponse3]]

        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)
        def listItem1MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse1.metadata)
        def listItem3MetaData = cartDataProvider.getListItemMetaDataFromCart(cartItemResponse3.metadata)

        when:
        HttpResponse<ListResponseTO> listResponse = client.toBlocking()
            .exchange(HttpRequest.GET(uri).headers(getHeaders(guestId)), ListResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.OK

        actual.listId == cartResponse.cartId
        actual.channel == LIST_CHANNEL.valueOf(cartResponse.cartChannel)
        actual.listTitle == cartResponse.tenantCartName
        actual.shortDescription == cartResponse.tenantCartDescription
        actual.listType == listMetaData.listType
        actual.defaultList == listMetaData.defaultList
        actual.maxPendingItemsCount == 3

        def pendingItems = actual.pendingListItems
        pendingItems.size() == 1
        pendingItems[0].listItemId == cartItemResponse1.cartItemId
        pendingItems[0].tcin == cartItemResponse1.tcin
        pendingItems[0].itemTitle == cartItemResponse1.tenantItemName
        pendingItems[0].itemNote == cartItemResponse1.notes
        pendingItems[0].price == cartItemResponse1.price
        pendingItems[0].listPrice == cartItemResponse1.listPrice
        pendingItems[0].images == cartItemResponse1.images
        pendingItems[0].offerCount == 0
        pendingItems[0].itemType == listItem1MetaData.itemType
        pendingItems[0].itemExpiration == listItem1MetaData.itemExpiration

        def completedItems = actual.completedListItems
        completedItems.size() == 1
        completedItems[0].listItemId == cartItemResponse3.cartItemId
        completedItems[0].tcin == cartItemResponse3.tcin
        completedItems[0].itemTitle == cartItemResponse3.tenantItemName
        completedItems[0].itemNote == cartItemResponse3.notes
        completedItems[0].price == cartItemResponse3.price
        completedItems[0].listPrice == cartItemResponse3.listPrice
        completedItems[0].images == cartItemResponse3.images
        completedItems[0].offerCount == 0
        completedItems[0].itemType == listItem3MetaData.itemType
        completedItems[0].itemExpiration == listItem3MetaData.itemExpiration

        2 * mockServer.get({ path -> path.contains(cartUri) }, _) >> [status: 200, body: response]
        1 * mockServer.get({ path -> path.contains(getCartURI(guestId,cartId)) }, _) >> [status: 200, body: [completedCartResponse]]
        1 * mockServer.get({ path -> path.contains(completedCartUri) }, _) >> [status: 200, body: completedCartContentResponse]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }
}
