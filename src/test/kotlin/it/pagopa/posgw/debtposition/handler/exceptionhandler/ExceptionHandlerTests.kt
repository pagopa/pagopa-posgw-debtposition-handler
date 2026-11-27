package it.pagopa.posgw.debtposition.handler.exceptionhandler

import it.pagopa.posgw.debtposition.handler.exception.GpdClientException
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.server.ServerWebInputException
import org.springframework.web.server.UnsupportedMediaTypeStatusException

class ExceptionHandlerTests {

    private val exceptionHandler = ExceptionHandler()

    @Test
    fun `test handle a generic Exception`() {
        val exception = Exception("Test exception")
        val responseEntity = exceptionHandler.handleException(exception)
        assert(responseEntity.statusCode.is5xxServerError)
        assert(responseEntity.body?.detail == "Unexpected error while processing the request")
    }

    @Test
    fun `test handle a GpdClientException`() {
        val gpdClientException =
            GpdClientException(
                description = "Test GPD client exception",
                httpStatusCode = HttpStatus.BAD_GATEWAY,
                operation = "TestOperation",
                organizationFiscalCode = "12345678901")
        val responseEntity = exceptionHandler.handleException(gpdClientException)
        assert(responseEntity.statusCode == HttpStatus.BAD_GATEWAY)
        assert(responseEntity.body?.detail == "Test GPD client exception")
        assert(responseEntity.body?.title == "GPD Invocation exception")
    }

    @Test
    fun `test handle a GpdClientException with cause not null`() {
        val gpdClientException =
            GpdClientException(
                description = "Test GPD client exception",
                httpStatusCode = HttpStatus.BAD_GATEWAY,
                operation = "TestOperation",
                organizationFiscalCode = "12345678901",
                cause = Exception("Test cause"))
        val responseEntity = exceptionHandler.handleException(gpdClientException)
        assert(responseEntity.statusCode == HttpStatus.BAD_GATEWAY)
        assert(responseEntity.body?.detail == "Test GPD client exception")
        assert(responseEntity.body?.title == "GPD Invocation exception")
    }

    @Test
    fun `test handle a RestApiException`() {
        val restApiException =
            it.pagopa.posgw.debtposition.handler.exception.RestApiException(
                httpStatusCode = HttpStatus.NOT_FOUND,
                title = "Not Found",
                description = "The requested resource was not found")
        val responseEntity = exceptionHandler.handleException(restApiException)
        assert(responseEntity.statusCode == HttpStatus.NOT_FOUND)
        assert(responseEntity.body?.detail == "The requested resource was not found")
        assert(responseEntity.body?.title == "Not Found")
    }

    @Test
    fun `test handle invalid input as bad request`() {
        val responseEntity =
            exceptionHandler.handleRequestValidationException(
                ServerWebInputException("Invalid X-Correlation-Id"))
        assert(responseEntity.statusCode == HttpStatus.BAD_REQUEST)
        assert(responseEntity.body?.status == 400)
        assert(responseEntity.body?.title == "Bad Request")
        assert(responseEntity.body?.detail == "Input request is not valid")
    }

    @Test
    fun `test handle unsupported media type as bad request`() {
        val responseEntity =
            exceptionHandler.handleRequestValidationException(
                UnsupportedMediaTypeStatusException(
                    MediaType.TEXT_PLAIN, listOf(MediaType.APPLICATION_JSON)))
        assert(responseEntity.statusCode == HttpStatus.BAD_REQUEST)
    }
}
