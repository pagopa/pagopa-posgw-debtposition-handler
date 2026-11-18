package it.pagopa.posgw.debtposition.handler.exception

import org.springframework.http.HttpStatusCode

/** Rest api exception, used to return an error specific HttpStatus and reason code to the caller */
class RestApiException(
    val httpStatusCode: HttpStatusCode,
    val title: String,
    val description: String
) : RuntimeException("[$httpStatusCode] $title - $description") {}
