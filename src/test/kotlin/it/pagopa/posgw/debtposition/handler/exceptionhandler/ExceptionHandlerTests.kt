package it.pagopa.posgw.debtposition.handler.exceptionhandler

import it.pagopa.posgw.debtposition.handler.exception.GpdClientException
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class ExceptionHandlerTests {

    private val exceptionHander = ExceptionHandler()

    @Test
    fun `test handle a generic Exception`() {
        val exception = Exception("Test exception")
        val responseEntity = exceptionHander.handleException(exception)
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
        val responseEntity = exceptionHander.handleException(gpdClientException)
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
        val responseEntity = exceptionHander.handleException(restApiException)
        assert(responseEntity.statusCode == HttpStatus.NOT_FOUND)
        assert(responseEntity.body?.detail == "The requested resource was not found")
        assert(responseEntity.body?.title == "Not Found")
    }
}
