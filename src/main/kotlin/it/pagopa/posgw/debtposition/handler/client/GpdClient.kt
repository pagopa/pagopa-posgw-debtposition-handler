package it.pagopa.posgw.debtposition.handler.client

import it.pagopa.generated.posgw.gpd.api.DebtPositionActionsApiApi
import it.pagopa.generated.posgw.gpd.api.DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi
import it.pagopa.generated.posgw.gpd.model.PaymentPositionModelV3Dto
import it.pagopa.posgw.debtposition.handler.exception.GpdClientException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

@Component
class GpdClient(
    @Autowired
    @Qualifier("gpdInvalidatePositionWebClient")
    val gpdInvalidateClient: DebtPositionActionsApiApi,
    @Autowired
    @Qualifier("gpdCreatePositionWebClient")
    val gpdCreateClient: DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi
) {
    // TODO use logging OER
    val logger: Logger = LoggerFactory.getLogger(javaClass.name)

    companion object {
        enum class Operation {
            CREATE_DEBT_POSITION,
            INVALIDATE_DEBT_POSITION
        }
    }

    fun createDebtPosition(
        organizationFiscalCode: String,
        paymentPositionModelV3Dto: PaymentPositionModelV3Dto,
        toPublish: Boolean?,
        xRequestId: String?
    ): Mono<PaymentPositionModelV3Dto> {

        return gpdCreateClient
            .createPosition(
                organizationFiscalCode, paymentPositionModelV3Dto, toPublish, xRequestId)
            .doOnError { error ->
                logger.error(
                    "Error creating debt position for organizationFiscalCode: $organizationFiscalCode, error: ${error.message}",
                    error)
            }
            .onErrorMap {
                mapGpdError(it, Operation.CREATE_DEBT_POSITION)
            }
    }

    fun invalidateDebtPosition(
        organizationFiscalCode: String,
        iupd: String,
        xRequestId: String?
    ): Mono<PaymentPositionModelV3Dto> {

        return gpdInvalidateClient
            .invalidatePosition(organizationFiscalCode, iupd, xRequestId)
            .doOnError { error ->
                logger.error(
                    "Error invalidating debt position for organizationFiscalCode: $organizationFiscalCode, iupd: $iupd, error: ${error.message}",
                    error)
            }
            .onErrorMap {
                mapGpdError(it, Operation.INVALIDATE_DEBT_POSITION)
            }
    }

    /**
     * Maps an error raised while calling GPD to the [GpdClientException] returned to our caller.
     *
     * For errors with an HTTP status from GPD ([WebClientResponseException]):
     * - 400: "Malformed request" (400) on create; on invalidate it is unexpected, so 502
     * - 404: "Debt position not found" (404) on invalidate; on create it is unexpected, so 502
     * - 409: "Duplicate debt position found." (409) on create, "Unexpected conflict error from GPD
     *   service" (409) on invalidate
     * - 503: "GPD service is unavailable" (503)
     * - any other status: "Unexpected error from GPD service with status code ..." (502 Bad
     *   Gateway)
     *
     * For errors where no HTTP response was received from GPD ([WebClientRequestException], for
     * example a response or connect timeout, or a refused connection): "No response from GPD
     * service" (504 Gateway Timeout).
     *
     * Any other error is mapped to a generic 500.
     */
    fun mapGpdError(t: Throwable, operation: Operation): GpdClientException {
        return when (t) {
            is WebClientResponseException -> {
                when (t.statusCode) {
                    HttpStatus.BAD_REQUEST -> {
                        if (operation == Operation.CREATE_DEBT_POSITION) {
                            GpdClientException(
                                description = "Malformed request", httpStatusCode = t.statusCode)
                        } else {
                            GpdClientException(
                                description = "Unexpected error from GPD service",
                                httpStatusCode = HttpStatus.BAD_GATEWAY)
                        }
                    }
                    HttpStatus.NOT_FOUND -> {
                        if (operation == Operation.INVALIDATE_DEBT_POSITION) {
                            GpdClientException(
                                description = "Debt position not found",
                                httpStatusCode = t.statusCode)
                        } else {
                            GpdClientException(
                                description = "Unexpected error from GPD service",
                                httpStatusCode = HttpStatus.BAD_GATEWAY)
                        }
                    }
                    HttpStatus.CONFLICT -> {
                        if (operation == Operation.CREATE_DEBT_POSITION) {
                            GpdClientException(
                                description = "Duplicate debt position found.",
                                httpStatusCode = t.statusCode)
                        } else {
                            GpdClientException(
                                description = "Unexpected conflict error from GPD service",
                                httpStatusCode = t.statusCode)
                        }
                    }
                    HttpStatus.SERVICE_UNAVAILABLE -> {
                        GpdClientException(
                            description = "GPD service is unavailable",
                            httpStatusCode = t.statusCode)
                    }
                    else -> {
                        GpdClientException(
                            description =
                                "Unexpected error from GPD service with status code ${t.statusCode}",
                            httpStatusCode = HttpStatus.BAD_GATEWAY,
                        )
                    }
                }
            }
            is WebClientRequestException -> {
                GpdClientException(
                    description = "No response from GPD service",
                    httpStatusCode = HttpStatus.GATEWAY_TIMEOUT,
                )
            }
            else -> {
                GpdClientException(
                    description = "Unexpected error while invoking GPD",
                    httpStatusCode = HttpStatus.INTERNAL_SERVER_ERROR,
                )
            }
        }
    }
}
