package it.pagopa.posgw.debtposition.handler.exception

import org.springframework.http.HttpStatusCode

class GpdClientException(
    val description: String,
    val httpStatusCode: HttpStatusCode,
    val operation: String,
    val organizationFiscalCode: String
) : ApiError(description) {
    override fun toRestException() =
        RestApiException(
            httpStatusCode = httpStatusCode,
            description = description,
            title = "GPD Invocation exception")
}
