package com.tgt.favorites.api

import com.tgt.favorites.util.BaseKafkaFunctionalTest
import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.lib.api.transport.ListMetaDataTO
import com.tgt.lists.lib.api.transport.ListResponseTO
import com.tgt.lists.lib.api.transport.UserMetaDataTO
import com.tgt.lists.lib.api.util.Constants
import com.tgt.lists.lib.api.util.LIST_CHANNEL
import com.tgt.lists.lib.api.util.LIST_STATUS
import com.tgt.lists.msgbus.ListsMessageBusProducer
import com.tgt.lists.msgbus.event.EventLifecycleNotificationProvider
import com.tgt.favorites.api.util.CartDataProvider
import com.tgt.favorites.api.util.TestUtilConstants
import groovy.json.JsonOutput
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.test.annotation.MicronautTest
import io.micronaut.test.annotation.MockBean
import spock.lang.Shared

import javax.inject.Inject

import static com.tgt.favorites.util.DataProvider.*

@MicronautTest
class UpdateListFunctionalTest  extends BaseKafkaFunctionalTest {

    CartDataProvider cartDataProvider
    CartType cartType = CartType.LIST
    LIST_CHANNEL cartChannel = LIST_CHANNEL.WEB
    String cartLocation = "3991"
    String guestId = "1234"

    @Shared
    @Inject
    EventLifecycleNotificationProvider eventNotificationsProvider

    @MockBean(ListsMessageBusProducer.class)
    ListsMessageBusProducer createMockListsMessageBusProducer() {
        return newMockMsgbusKafkaProducerClient(eventNotificationsProvider)
    }

    def setup() {
        cartDataProvider = new CartDataProvider()
    }

    def "test update list integrity"() {
        def listId = UUID.randomUUID()
        def uri = Constants.LISTS_BASEPATH + "/" + listId
        def cartUri = "/carts/v4/" + listId
        def listRequest =
            [
                "list_title": "My updated list",
                "short_description": "My first updated list"
            ]

        ListMetaDataTO metadata = new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.PENDING)

        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, cartChannel, CartType.LIST,
            "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))

        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        def updatedCartResponse = cartDataProvider.getCartResponse(listId, guestId, cartChannel, CartType.LIST,
            "My updated list", "My first updated list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))

        def listMetaData = cartDataProvider.getListMetaDataFromCart(cartResponse.metadata)

        def cartLists = []

        when:
        HttpResponse<ListResponseTO> listResponse = client.toBlocking().exchange(
            HttpRequest.PUT(uri, JsonOutput.toJson(listRequest)).headers(getHeaders(guestId)), ListResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.OK

        actual.listId == updatedCartResponse.cartId
        actual.channel == LIST_CHANNEL.valueOf(cartResponse.cartChannel)
        actual.listTitle == updatedCartResponse.tenantCartName
        actual.shortDescription == updatedCartResponse.tenantCartDescription
        actual.listType == listMetaData.listType
        actual.defaultList == listMetaData.defaultList

        1 * mockServer.get({ path -> path.contains(getCartContentURI(listId))}, _) >> [status: 200, body: cartContentsResponse] // Authorization filter call
        1 * mockServer.get({ path -> path.contains(getCartURI(guestId))}, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartLists] // search call in default list manager
        1 * mockServer.get({ path -> path.contains(getCartContentURI(listId))}, _) >> [status: 200, body: cartContentsResponse]
        1 * mockServer.put({ path -> path.contains(cartUri)},_, { headers -> checkHeaders(headers) }) >> [status: 200, body: updatedCartResponse]

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }

    def "test update list integrity with preexisting default list"() {
        def listId = UUID.randomUUID()
        def uri = Constants.LISTS_BASEPATH + "/" + listId
        def listRequest =
            [
                "list_title"                        : "My updated list",
                "short_description"                 : "My updated first list",
                (TestUtilConstants.DEFAULT_LIST_IND): true
            ]

        ListMetaDataTO metadata = new ListMetaDataTO(false, "SHOPPING", LIST_STATUS.PENDING)
        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, cartChannel, CartType.LIST, "My list", "My first list", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))

        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)

        def cartLists = [getCart(listId, cartType , cartChannel, cartLocation, guestId, "My list", "My first list", cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))]

        ListMetaDataTO updatedMetadata = new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.PENDING)
        def updatedCartResponse = cartDataProvider.getCartResponse(listId, guestId, cartChannel, CartType.LIST, "My updated list", "My updated first list", null, cartDataProvider.getMetaData(updatedMetadata, new UserMetaDataTO()))

        def updatedListMetaData = cartDataProvider.getListMetaDataFromCart(updatedCartResponse.metadata)

        when:
        HttpResponse<ListResponseTO> listResponse = client.toBlocking().exchange(
            HttpRequest.PUT(uri, JsonOutput.toJson(listRequest)).headers(getHeaders(guestId)), ListResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.OK

        actual.listId == updatedCartResponse.cartId
        actual.channel == LIST_CHANNEL.valueOf(cartResponse.cartChannel)
        actual.listTitle == updatedCartResponse.tenantCartName
        actual.shortDescription == updatedCartResponse.tenantCartDescription
        actual.listType == updatedListMetaData.listType
        actual.defaultList == updatedListMetaData.defaultList

        1 * mockServer.get({ path -> path.contains(getCartContentURI(listId))}, _) >> [status: 200, body: cartContentsResponse] // Authorization filter call
        1 * mockServer.get({ path -> path.contains(getCartURI(guestId))}, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartLists] // search call in default list manager
        1 * mockServer.get({ path -> path.contains(getCartContentURI(listId))}, _) >> [status: 200, body: cartContentsResponse]
        1 * mockServer.put({ path -> path.contains("/carts/v4/" + listId)},_, { headers -> checkHeaders(headers) }) >> [status: 200, body: updatedCartResponse] // updateByListId cart response

        when: 'circuit is still closed'
        String metrics = client.toBlocking().retrieve(HttpRequest.GET("/prometheus"))

        then:
        metrics.contains('resilience4j_circuitbreaker_state{name="carts-api",state="closed",} 1.0')
    }
}
