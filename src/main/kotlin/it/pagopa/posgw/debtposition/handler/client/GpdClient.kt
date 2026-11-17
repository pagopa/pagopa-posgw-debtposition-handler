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
            .onErrorMap(WebClientResponseException::class.java) { error ->
                GpdClientException(
                    description = error.message,
                    httpStatus = HttpStatus.valueOf(error.statusCode.value()),
                )
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
            .onErrorMap(WebClientResponseException::class.java) { error ->
                GpdClientException(
                    description = error.message,
                    httpStatus = HttpStatus.valueOf(error.statusCode.value()),
                )
            }
    }
}
