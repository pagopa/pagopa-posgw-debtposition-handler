package it.pagopa.posgw.debtposition.handler.exception

import org.springframework.http.HttpStatusCode

class GpdClientException(
    private val description: String,
    private val httpStatusCode: HttpStatusCode
) : ApiError(description) {
    override fun toRestException() =
        RestApiException(
            httpStatusCode = httpStatusCode,
            description = description,
            title = "GPD Invocation exception")
}
