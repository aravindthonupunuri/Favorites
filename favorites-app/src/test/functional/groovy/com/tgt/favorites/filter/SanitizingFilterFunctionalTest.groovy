package com.tgt.favorites.filter

import com.tgt.lists.cart.transport.CartPostRequest
import com.tgt.lists.cart.transport.CartType
import com.tgt.lists.lib.api.transport.ListMetaDataTO
import com.tgt.lists.lib.api.transport.ListResponseTO
import com.tgt.lists.lib.api.transport.UserMetaDataTO
import com.tgt.favorites.api.util.FavoriteConstants
import com.tgt.lists.lib.api.util.Constants
import com.tgt.lists.lib.api.util.LIST_CHANNEL
import com.tgt.lists.lib.api.util.LIST_STATUS
import com.tgt.lists.msgbus.ListsMessageBusProducer
import com.tgt.lists.msgbus.event.EventLifecycleNotificationProvider
import com.tgt.favorites.api.util.TestUtilConstants
import com.tgt.favorites.util.BaseFunctionalTest
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
class SanitizingFilterFunctionalTest extends BaseFunctionalTest {

    @Shared
    @Inject
    EventLifecycleNotificationProvider eventNotificationsProvider

    @MockBean(ListsMessageBusProducer.class)
    ListsMessageBusProducer createMockListsMessageBusProducer() {
        return newMockMsgbusKafkaProducerClient(eventNotificationsProvider)
    }

    def "test create list data sanitization"() {
        given:
        String guestId = "1236"
        Map listRequest =
            [
                "channel"                    : "<script>hijack</script>" + LIST_CHANNEL.WEB,
                "list_title"                 : "\\fgdf\\gghfh\\fgh\\dfflist1",
                (TestUtilConstants.LIST_TYPE): "SHOPPING",
                "short_description"          : "My Favorite List<script>xss</script><input type=\"text\" name='addr' autofocus onfocus=alert(1)\\/\\/wztya",
                "location_id"                : 1375L
            ]

        ListMetaDataTO metadata = new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.PENDING)

        def cartResponse = cartDataProvider.getCartResponse(UUID.randomUUID(), guestId,
            LIST_CHANNEL.WEB, CartType.LIST, "\fgdfgghfh\fghdfflist1", "My Favorite List<input type=text autofocus alert//wztya", null, cartDataProvider.getMetaData(metadata, new UserMetaDataTO()))

        def cartLists = []

        when:
        HttpResponse<ListResponseTO> listResponse = client.toBlocking().exchange(
            HttpRequest.POST(FavoriteConstants.BASEPATH, JsonOutput.toJson(listRequest))
                .headers(getHeaders(guestId)), ListResponseTO)
        def actualStatus = listResponse.status()
        def actual = listResponse.body()

        then:
        actualStatus == HttpStatus.CREATED

        actual.listId == cartResponse.cartId
        actual.listTitle == "\fgdfgghfh\fghdfflist1"
        actual.channel == LIST_CHANNEL.WEB
        actual.shortDescription == "My Favorite List<input type=text autofocus alert//wztya"

        1 * mockServer.get({ path -> path.contains(getCartURI(guestId))},{ headers -> checkHeaders(headers) }) >> [status: 200, body: cartLists]
        1 * mockServer.post({ path -> path.contains("/carts/v4") }, { body ->
            String cartPostRequestJson = ((Optional) body).get()
            CartPostRequest cartPostRequest = cartDataProvider.jsonToCartPostRequest(cartPostRequestJson)
            if (cartPostRequest.tenantCartName != Constants.COMPLETED_CART_NAME) {
                def cartNameMatched = cartPostRequest.tenantCartName == "\fgdfgghfh\fghdfflist1"
                def descMatched = cartPostRequest.tenantCartDescription == "My Favorite List&lt;input type=&quot;text&quot; name=&apos;addr&apos; autofocus alert//wztya"
                assert cartNameMatched && descMatched
            }
            true
        }, { headers -> checkHeaders(headers) }) >> [status: 200, body: cartResponse]
    };

    def "test create list data sanitization with IntrusionException"() {
        given:
        String guestId = "1236"
        Map listRequest =
            [
                "channel"                    : "<script>hijack</script>"+LIST_CHANNEL.WEB,
                "list_title"                 : "<>\"'%()&+\\\\'\\\"list1",
                (TestUtilConstants.LIST_TYPE): "SHOPPING",
                "short_description"          : "My Favorite List<script>xss</script>",
                "location_id"                   : 1375L
            ]

        ListMetaDataTO metadata = new ListMetaDataTO(true, "SHOPPING", LIST_STATUS.PENDING)


        when:
        HttpResponse<ListResponseTO> listResponse = client.toBlocking().exchange(
            HttpRequest.POST(FavoriteConstants.BASEPATH, JsonOutput.toJson(listRequest))
                .headers(getHeaders(guestId)), ListResponseTO)

        then:
        def ex = thrown(HttpClientResponseException)
        ex.message == "Input Validation Error [<>\"'%()&+\\\\'\\\"list1]"
    }
}
