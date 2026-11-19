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

    fun mapGpdError(t: Throwable, operation: Operation): GpdClientException {
        return when (t) {
            is WebClientResponseException -> {
                when (t.statusCode) {
                    HttpStatus.BAD_REQUEST -> {
                        GpdClientException(
                            description = "Malformed request", httpStatusCode = t.statusCode)
                    }
                    HttpStatus.NOT_FOUND -> {
                        GpdClientException(
                            description = "Debt position not found", httpStatusCode = t.statusCode)
                    }
                    HttpStatus.CONFLICT -> {
                        if (operation == Operation.CREATE_DEBT_POSITION) {
                            GpdClientException(
                                description = "Duplicate debt position found.",
                                httpStatusCode = t.statusCode)
                        } else {
                            GpdClientException(
                                description = "Conflict error from GPD service",
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
            else -> {
                GpdClientException(
                    description = "Unexpected error while invoking GPD",
                    httpStatusCode = HttpStatus.INTERNAL_SERVER_ERROR,
                )
            }
        }
    }
}
