package com.tgt.shoppinglist.api.exception

import com.tgt.lists.lib.api.exception.BadRequestException
import com.tgt.lists.lib.api.exception.ErrorCode
import com.tgt.lists.lib.api.exception.NotAuthorizedException
import com.tgt.lists.lib.api.exception.ResourceNotFoundException
import com.tgt.lists.micronaut.resilience.ResiliencyException
import io.micronaut.context.annotation.Requires
import io.micronaut.core.convert.exceptions.ConversionErrorException
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpResponse.*
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.exceptions.ExceptionHandler
import mu.KotlinLogging
import javax.inject.Singleton
import javax.validation.ConstraintViolationException
import com.tgt.lists.lib.api.util.AppErrorCodes.ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE

@Produces
@Singleton
@Requires(classes = [ Throwable::class ])
class ListExceptionHandler : ExceptionHandler<Throwable, HttpResponse<ErrorCode>> {
    private val logger = KotlinLogging.logger {}

    override fun handle(request: HttpRequest<Any>, throwable: Throwable): HttpResponse<ErrorCode> {
        var errMsg = throwable.message ?: throwable.cause?.message
        if (throwable is ResiliencyException) {
            val errorBody = throwable.httpResponse.getBody(String::class.java)
            if (errorBody.isPresent) {
                errMsg += " [ Error Body: " + errorBody.get() + " ] "
            }
        }

        // TODO: Remove following temporary logger.info(...)
        // temporarily logging errMsg as info to troubleshoot a TAP issue where
        // it sometimes doesn't output log with throwable to kibana.
        logger.info("ListError: $errMsg")

        logger.error(errMsg, throwable)

        return when (throwable) {
            is BadRequestException -> badRequest(throwable.errorCode)
            is NotAuthorizedException -> errStatus(HttpStatus.FORBIDDEN, throwable.errorCode.toString())
            is ResourceNotFoundException -> errStatus(HttpStatus.NOT_FOUND, throwable.errorCode.toString())
            is ConversionErrorException -> errStatus(HttpStatus.BAD_REQUEST, throwable.message)
            is ConstraintViolationException -> badRequest(ITEM_TYPE_REQUEST_BODY_VIOLATION_ERROR_CODE(listOf(throwable.toString())))
            else -> errStatus(HttpStatus.INTERNAL_SERVER_ERROR, errMsg)
        }
    }

    private fun errStatus(httpStatus: HttpStatus, errMsg: String?): HttpResponse<ErrorCode> {
        try {
            // sometimes status throws exception itself, hence run it within try block
            return status(httpStatus, errMsg)
        } catch (ex: Throwable) {
            return serverError()
        }
    }
}
