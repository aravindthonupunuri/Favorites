package com.tgt.shoppinglist.api.exception

import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.http.context.event.HttpRequestTerminatedEvent
import mu.KotlinLogging
import javax.inject.Singleton

@Singleton
class ListsApplicationEventListener<E> : ApplicationEventListener<E> {

    private val logger = KotlinLogging.logger {}

    override fun onApplicationEvent(event: E) {
        // micronaut does NOT throw any exception for idle-timeout based timeouts and silently terminates the connection
        // where caller just gets "Error: socket hang up" i.e. there is no http status response
        // But micronaut also fires HttpRequestTerminatedEvent event that we can listen to and log the termination
        // in lists log.
        // Note: we won't be do anything other than logging in this method.
        // logger.error("HTTP Request Termination event: $event")
    }

    override fun supports(event: E): Boolean {
        return event is HttpRequestTerminatedEvent
    }
}
