package it.pagopa.posgw.debtposition.handler.exceptionhandler

import it.pagopa.generated.posgw.debtposition.handler.model.ProblemJsonDto
import it.pagopa.posgw.common.utils.LogTracingUtils
import it.pagopa.posgw.debtposition.handler.exception.ApiError
import it.pagopa.posgw.debtposition.handler.exception.GpdClientException
import it.pagopa.posgw.debtposition.handler.exception.RestApiException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebInputException
import org.springframework.web.server.UnsupportedMediaTypeStatusException

/**
 * Exception handler used to output a custom message in case an incoming request is invalid or an
 * api encounter an error and throw an RestApiException
 */
@RestControllerAdvice
class ExceptionHandler {

    val logger: Logger = LoggerFactory.getLogger(javaClass.name)

    /** RestApiException exception handler */
    @ExceptionHandler(RestApiException::class)
    fun handleException(e: RestApiException): ResponseEntity<ProblemJsonDto> {
        logFailure(e)
        return toResponse(e)
    }

    /** ApiError exception handler */
    @ExceptionHandler(ApiError::class)
    fun handleException(e: ApiError): ResponseEntity<ProblemJsonDto> {
        logFailure(e)
        return toResponse(e.toRestException())
    }

    /** GpdClientException exception handler */
    @ExceptionHandler(GpdClientException::class)
    fun handleException(e: GpdClientException): ResponseEntity<ProblemJsonDto> {
        LogTracingUtils.loggerTracingUtils()
            .dependency(LogTracingUtils.GPD_DEPENDENCY)
            .details(
                mapOf(
                    "http_status_code" to e.httpStatusCode.value().toString(),
                    "organization_fiscal_code" to e.organizationFiscalCode,
                ))
            .attributes(mapOf(LogTracingUtils.Companion.AttributeKeys.EVENT_ACTION to e.operation))
            .failure()
            .logWithStackTrace(
                logger,
                e,
                "Error performing operation ${e.operation}",
            )
        return toResponse(e.toRestException())
    }

    /** Fallback for unexpected errors: the exception details are logged but never exposed */
    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ProblemJsonDto> {
        logFailure(e)
        return problem(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase,
            "Unexpected error while processing the request")
    }

    /**
     * Formally invalid input (malformed body, failed validation, missing or mistyped parameters or
     * headers, unsupported content type). The API specification declares 400 for all of them.
     */
    @ExceptionHandler(ServerWebInputException::class, UnsupportedMediaTypeStatusException::class)
    fun handleRequestValidationException(
        e: ResponseStatusException,
    ): ResponseEntity<ProblemJsonDto> {
        LogTracingUtils.loggerTracingUtils()
            .failure()
            .logWithStackTrace(
                logger,
                e,
                "Input request is not valid",
            )
        return problem(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.reasonPhrase,
            "Input request is not valid")
    }

    private fun logFailure(e: Exception) {
        LogTracingUtils.loggerTracingUtils()
            .failure()
            .logWithStackTrace(
                logger,
                e,
                "Exception processing request",
            )
    }

    private fun toResponse(e: RestApiException): ResponseEntity<ProblemJsonDto> =
        problem(e.httpStatusCode.value(), e.title, e.description)

    private fun problem(
        status: Int,
        title: String,
        detail: String
    ): ResponseEntity<ProblemJsonDto> =
        ResponseEntity.status(status)
            .body(ProblemJsonDto(title = title, status = status, detail = detail))
}
