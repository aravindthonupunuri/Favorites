package com.tgt.favorites.api.filter

import com.tgt.favorites.api.util.FavoriteConstants
import com.tgt.lists.cart.CartClient
import com.tgt.lists.cart.transport.CartContentsFieldGroup
import com.tgt.lists.cart.types.CartContentsFieldGroups
import com.tgt.lists.lib.api.domain.ListPermissionManager
import com.tgt.lists.lib.api.exception.NotAuthorizedException
import com.tgt.favorites.api.util.CartDataProvider
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.filter.ServerFilterChain
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.Specification

class AuthorizationFilterTest extends Specification {

    AuthorizationFilter authorizationFilter
    CartClient cartClient
    String guestId = "1234"
    CartDataProvider cartDataProvider = new CartDataProvider()

    def setup() {
        cartClient = Mock(CartClient)
        authorizationFilter = new AuthorizationFilter(new ListPermissionManager(cartClient))
    }

    def "Test doFilterOnce() when there is no profile id"() {
        given:
        def mockRequest = Mock(HttpRequest)
        def headers = Mock(HttpHeaders)
        mockRequest.headers >> headers
        def serverFilterChain = Mock(ServerFilterChain)

        when:
        authorizationFilter.doFilterOnce(mockRequest, serverFilterChain)

        then:
        thrown(NotAuthorizedException)
        0 * serverFilterChain.proceed(mockRequest) >> Mock(Publisher)
    }

    def "Test doFilterOnce() when there is no list id in the path"() {
        given:
        def mockRequest = Mock(HttpRequest)
        def headers = Mock(HttpHeaders)
        headers.get("profile_id") >> guestId
        mockRequest.headers >> headers
        def serverFilterChain = Mock(ServerFilterChain)
        mockRequest.path >> "list"

        when:
        authorizationFilter.doFilterOnce(mockRequest, serverFilterChain)

        then:
        1 * serverFilterChain.proceed(mockRequest) >> Mock(Publisher)
    }

    def "Test doFilterOnce() when it is successful"() {
        given:
        def listId = UUID.randomUUID()
        def mockRequest = Mock(HttpRequest)
        def headers = Mock(HttpHeaders)
        headers.get("profile_id") >> guestId
        mockRequest.headers >> headers
        def serverFilterChain = Mock(ServerFilterChain)
        mockRequest.path >> FavoriteConstants.BASEPATH + "/" + listId
        def cartResponse = cartDataProvider.getCartResponse(listId, guestId, null)
        def cartContentsResponse = cartDataProvider.getCartContentsResponse(cartResponse, null)
        def fieldGroups = new CartContentsFieldGroups([CartContentsFieldGroup.CART])
        def expected = Mock(Publisher)

        when:
        authorizationFilter.doFilterOnce(mockRequest, serverFilterChain).subscribe()

        then:
        1 * cartClient.getCartContents(listId, false, fieldGroups) >> Mono.just(cartContentsResponse)
        1 * serverFilterChain.proceed(mockRequest) >> expected
    }

    def "Test doFilterOnce() when the list id is not found"() {
        given:
        def listId = UUID.randomUUID()
        def mockRequest = Mock(HttpRequest)
        def headers = Mock(HttpHeaders)
        headers.get("profile_id") >> guestId
        mockRequest.headers >> headers
        def serverFilterChain = Mock(ServerFilterChain)
        mockRequest.path >> FavoriteConstants.BASEPATH + "/" + listId
        def exception = new HttpClientResponseException("Cart id is not found", HttpResponse.notFound())
        def fieldGroups = new CartContentsFieldGroups([CartContentsFieldGroup.CART])

        when:
        authorizationFilter.doFilterOnce(mockRequest, serverFilterChain).subscribe()

        then:
        1 * cartClient.getCartContents(listId, false, fieldGroups) >> Mono.error(exception)
        0 * serverFilterChain.proceed(mockRequest)
    }
}
