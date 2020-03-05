package com.tgt.favorites.api.exception

import com.tgt.lists.micronaut.resilience.ResiliencyException
import io.micronaut.discovery.event.ServiceStartedEvent
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.scheduling.annotation.Async
import io.reactivex.plugins.RxJavaPlugins
import mu.KotlinLogging
import javax.inject.Singleton

@Singleton
open class ListsRxJavaPluginsErrorHandler {

    private val logger = KotlinLogging.logger {}

    /**
     * RxJava2 UndeliverableException handling: see https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling
     * Use the setErrorHandler( ) method to handle the errors that can't be emitted
     * because the downstream's lifecycle already reached its terminal state. This can happen e.g. when we zip two
     * concurrent client calls. When first call fails, the onError signal is emitted abd subscriber exits while the second call
     * is still in flight, and when second call fails there is no subscriber to recieve that error and hence UndeliverableException
     * is thrown by RxJava. Th
     */
    @EventListener
    @Async
    open fun setupRxJavaPluginErrorHandler(event: ServiceStartedEvent) {
        logger.info("Initialized Lists RxJava2 Error Handler")
        RxJavaPlugins.setErrorHandler {
            var throwable: Throwable = it
            try {
                var errMsg = throwable.message ?: throwable.cause?.message
                if (throwable is ResiliencyException) {
                    val errorBody = throwable.httpResponse.getBody(String::class.java)
                    if (errorBody.isPresent) {
                        errMsg += " [ Error Body: " + errorBody.get() + " ] "
                    }
                }
                logger.error("RxJavaPluginErrorHandler: $errMsg", throwable)
            } catch (nestedEx: Throwable) {
                nestedEx.printStackTrace()
                throwable = nestedEx
            }

            // uncaught handling
            val currentThread = Thread.currentThread()
            val handler = currentThread.uncaughtExceptionHandler
            handler.uncaughtException(currentThread, throwable)
        }
    }
}
