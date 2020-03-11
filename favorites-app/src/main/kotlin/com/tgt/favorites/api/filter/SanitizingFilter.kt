package com.tgt.favorites.api.filter

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.ValueNode
import com.tgt.favorites.api.util.FavoriteConstants
import com.tgt.lists.lib.api.exception.BadRequestException
import com.tgt.lists.lib.api.util.AppErrorCodes.INPUT_SANITIZATION_ERROR_CODE
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.OncePerRequestHttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import mu.KotlinLogging
import org.owasp.esapi.ESAPI
import org.owasp.esapi.Encoder
import org.owasp.esapi.errors.IntrusionException
import org.owasp.esapi.reference.DefaultSecurityConfiguration
import org.reactivestreams.Publisher
import java.util.regex.Pattern

/**
 * Filter to sanitize  POST, PUT and PATCH text contents within request body
 */

@Filter(FavoriteConstants.BASEPATH + "/**")
class SanitizingFilter : OncePerRequestHttpServerFilter() {

    private val logger = KotlinLogging.logger {}

    /*
    Order=0 is reserved for micronaut's own servers filters e.g. ServerRequestContextFilter, EndpointsFilter, ServerRequestMeterRegistryFilter
    Server filters follow a filter order (opposite of client filters) with Integer.MIN_VALUE (-ve) as HIGHEST PRECEDENCE to Integer.MAX_VALUE (+ve) as LOWEST PRECEDENCE
    Always use an order value greater than 0 to follow micronaut's filters
    Smaller order value has precedence over higher value
    */
    @Value("\${filter.server.order.sanitizing-filter}")
    private var filterOrder: Int = 0 // don't provide a default order value to enforce application provided order

    val esapiEncoder: Encoder
    val patternList: MutableList<Pattern> = mutableListOf()
    init {
        // Set DISCARD_LOGSPECIAL property before instantiating encoder
        // to suppress ESAPI messages coming out directly on System.out without going thru logger.
        System.setProperty(DefaultSecurityConfiguration.DISCARD_LOGSPECIAL, "true")
        esapiEncoder = ESAPI.encoder()

        // Avoid anything between script tags
        patternList.add(Pattern.compile("<script>(.*?)</script>", Pattern.CASE_INSENSITIVE))
        // Remove any lonesome </script> tag
        patternList.add(Pattern.compile("</script>", Pattern.CASE_INSENSITIVE))
        // Remove any lonesome <script ...> tag
        patternList.add(Pattern.compile("<script(.*?)>", Pattern.CASE_INSENSITIVE or Pattern.MULTILINE or Pattern.DOTALL))
        // Avoid eval(...) expressions
        patternList.add(Pattern.compile("eval\\((.*?)\\)", Pattern.CASE_INSENSITIVE or Pattern.MULTILINE or Pattern.DOTALL))

        // avoid iframes
        patternList.add(Pattern.compile("<iframe(.*?)>(.*?)</iframe>", Pattern.CASE_INSENSITIVE))
        // Avoid anything in a src='...' type of expression
        patternList.add(Pattern.compile("src[\r\n]*=[\r\n]*\\\'(.*?)\\\'", Pattern.CASE_INSENSITIVE or Pattern.MULTILINE or Pattern.DOTALL))
        patternList.add(Pattern.compile("src[\r\n]*=[\r\n]*\\\"(.*?)\\\"", Pattern.CASE_INSENSITIVE or Pattern.MULTILINE or Pattern.DOTALL))
        patternList.add(Pattern.compile("src[\r\n]*=[\r\n]*([^>]+)", Pattern.CASE_INSENSITIVE or Pattern.MULTILINE or Pattern.DOTALL))

        // Avoid expression(...) expressions
        patternList.add(Pattern.compile("expression\\((.*?)\\)", Pattern.CASE_INSENSITIVE or Pattern.MULTILINE or Pattern.DOTALL))

        // Avoid javascript:... expressions
        patternList.add(Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE))

        // Avoid vbscript:... expressions
        patternList.add(Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE))

        // Avoid onload= expressions
        patternList.add(Pattern.compile("onload(.*?)=", Pattern.CASE_INSENSITIVE or Pattern.MULTILINE or Pattern.DOTALL))

        patternList.add(Pattern.compile("onfocus(.*?)=", Pattern.CASE_INSENSITIVE or Pattern.MULTILINE or Pattern.DOTALL))

        patternList.add(Pattern.compile("\\((.*?)\\)", Pattern.CASE_INSENSITIVE or Pattern.MULTILINE or Pattern.DOTALL))
    }

    override fun doFilterOnce(request: HttpRequest<*>, chain: ServerFilterChain): Publisher<MutableHttpResponse<*>> {
        logger.debug("Entering Sanitizing Server Filter [order: $filterOrder]: ${request.method} ${request.path}")

        if (request.method == HttpMethod.POST || request.method == HttpMethod.PUT || request.method == HttpMethod.PATCH) {
            request.body.ifPresent {
                val jsonRootNode = it as JsonNode
                sanitizeJson(jsonRootNode, "", jsonRootNode)
            }
        }

        return chain.proceed(request)
    }

    /**
     * Method iterates over all json nodes within request body's root json node, and sanitize all string values
     * using sanitizeText.
     */
    private fun sanitizeJson(parentNode: JsonNode, parentKey: String, currentNode: JsonNode) {
        if (currentNode.isArray) {
            val arrayNode: ArrayNode = currentNode as ArrayNode
            val node: Iterator<JsonNode> = arrayNode.elements()
            while (node.hasNext()) {
                sanitizeJson(currentNode, "", node.next())
            }
        } else if (currentNode.isObject) {
            currentNode.fields().forEachRemaining { entry: Map.Entry<String, JsonNode> -> sanitizeJson(currentNode, entry.key, entry.value) }
        } else if (currentNode.isValueNode) {
            val objectNode = parentNode as ObjectNode
            val value = (currentNode as ValueNode).asText()
            // let's not convert null values to string "null"
            if (value != "null") {
                val sanitizedValue = sanitizeText(value)
                objectNode.put(parentKey, sanitizedValue)
            }
        } else {
            throw RuntimeException("Invalid Json Node Type: $currentNode")
        }
    }

    private fun sanitizeText(text: String): String {
        var value = ""
        try {
            value = esapiEncoder.canonicalize(text)
        } catch (e: IntrusionException) {
            throw BadRequestException(INPUT_SANITIZATION_ERROR_CODE.copy(message = "${INPUT_SANITIZATION_ERROR_CODE.message} [$text]"), e)
        }
        patternList.forEach {
            value = it.matcher(value).replaceAll("")
        }

        // sanitize sensitive HTML metacharacters to html entities
        value = value.replace("&", "&amp;")
        value = value.replace("<", "&lt;")
        value = value.replace(">", "&gt;")
        value = value.replace("\"", "&quot;")
        value = value.replace("'", "&apos;")

        return value
    }
}
