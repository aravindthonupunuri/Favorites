package com.tgt.shoppinglist.api.filter

import com.tgt.lists.lib.api.domain.ListPermissionManager
import com.tgt.lists.lib.api.exception.NotAuthorizedException
import com.tgt.lists.lib.api.util.AppErrorCodes
import com.tgt.lists.lib.api.util.Constants
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.OncePerRequestHttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import mu.KotlinLogging
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import java.util.*

@Filter(Constants.LISTS_BASEPATH + "/**")
class AuthorizationFilter(private val listPermissionManager: ListPermissionManager) : OncePerRequestHttpServerFilter() {
    private val logger = KotlinLogging.logger(AuthorizationFilter::class.java.name)

    /*
      Order=0 is reserved for micronaut's own servers filters e.g. ServerRequestContextFilter, EndpointsFilter, ServerRequestMeterRegistryFilter
      Server filters follow a filter order (opposite of client filters) with Integer.MIN_VALUE (-ve) as HIGHEST PRECEDENCE to Integer.MAX_VALUE (+ve) as LOWEST PRECEDENCE
      Always use an order value greater than 0 to follow micronaut's filters
      Smaller order value has precedence over higher value
    */
    @Value("\${filter.server.order.authorization-filter}")
    private var filterOrder: Int = 0 // don't provide a default order value to enforce application provided order

    override fun doFilterOnce(request: HttpRequest<*>, chain: ServerFilterChain): Publisher<MutableHttpResponse<*>> {
        logger.debug("Entering Lists Authorization Server Filter [order: $filterOrder]: ${request.method } ${request.path}")
        val profileId = request.headers.get("profile_id")
        if (profileId.isNullOrEmpty()) {
            throw NotAuthorizedException(AppErrorCodes.NOT_AUTHORIZED_ERROR_CODE)
        }

        val listId = getUUID(request.path) ?: return chain.proceed(request)
        return listPermissionManager.authorize(profileId, listId)
            .then(
                Mono.defer {
                    logger.debug("Lists Authorization successful: ${request.method} ${request.path}")
                    Mono.from(chain.proceed(request))
                }
            )
    }

    override fun getOrder(): Int {
        return filterOrder
    }

    private fun getUUID(path: String): UUID? {
        val index = path.indexOf("/v4/")
        if (index <= 0) {
            return null
        }

        val paths = path.substring(index).split("/")
        if (paths.isNullOrEmpty() || paths.size < 3) {
            return null
        }

        return try { UUID.fromString(paths[2]) } catch (e: Exception) { return null }
    }
}
