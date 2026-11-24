package it.pagopa.posgw.debtposition.handler.services

import it.pagopa.generated.posgw.debtposition.handler.model.DebtPositionRequestDto
import it.pagopa.generated.posgw.gpd.model.PaymentPositionModelV3Dto
import it.pagopa.posgw.debtposition.handler.client.GpdClient
import it.pagopa.posgw.debtposition.handler.mapper.toPaymentPosition
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class DebtPositionsService(@Autowired val gpdClient: GpdClient) {

    fun createDebtPosition(
        organizationFiscalCode: String,
        debtPositionRequestDto: DebtPositionRequestDto,
        toPublish: Boolean?,
        xRequestId: String?
    ): Mono<PaymentPositionModelV3Dto> {

        val paymentPositionModelV3Dto =
            debtPositionRequestDto.toPaymentPosition(organizationFiscalCode)

        return gpdClient.createDebtPosition(
            organizationFiscalCode, paymentPositionModelV3Dto, toPublish, xRequestId)
    }
}
