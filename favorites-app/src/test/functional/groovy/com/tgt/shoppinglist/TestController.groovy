package com.tgt.shoppinglist

import com.tgt.lists.cart.transport.CartPostRequest
import com.tgt.lists.cart.transport.CartResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import reactor.core.publisher.Mono

import javax.validation.Valid

/**
 * A dummy Test Controller to enable body contents for MockServerFilter.
 *
 * Micronaut doesn't pass the body contents to a Server Filter if there is no actual controller
 * to handle url. Since MockServer relies on a MockServerFilter, and if a test requires to check the
 * body contents in mock condition, then a real controller is required to see body contents within mock condition.
 */

@Controller("/")
class TestController {

    /**
     * A placeholder method to let micronaut pass body contents to MockServerFilter. The method doesn't
     * need to do anything because MockServerFilter will intercept and respond without this method
     * ever being called.
     */
    @Post("/carts/v4")
    Mono<CartResponse> postCartResponse(@Body @Valid CartPostRequest cartPostRequest, @QueryValue("key") String key) {
        return Mono.empty()
    }
}
