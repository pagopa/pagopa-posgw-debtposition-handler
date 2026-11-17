package it.pagopa.posgw.debtposition.handler.exception

import org.springframework.http.HttpStatus

class GpdClientException(private val description: String, private val httpStatus: HttpStatus) :
    ApiError(description) {
    override fun toRestException() =
        RestApiException(
            httpStatus = httpStatus, description = description, title = "GPD Invocation exception")
}
