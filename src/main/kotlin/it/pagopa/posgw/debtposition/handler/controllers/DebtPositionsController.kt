package it.pagopa.posgw.debtposition.handler.controllers

import it.pagopa.generated.posgw.debtposition.handler.api.DebtPositionsApi
import it.pagopa.generated.posgw.debtposition.handler.model.DebtPositionRequestDto
import it.pagopa.generated.posgw.debtposition.handler.model.DebtPositionResponseDto
import it.pagopa.posgw.common.utils.LogTracingUtils
import it.pagopa.posgw.debtposition.handler.services.DebtPositionsService
import java.util.UUID
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class DebtPositionsController(private val debtPositionService: DebtPositionsService) :
    DebtPositionsApi {

    override suspend fun createDebtPosition(
        xCorrelationId: UUID,
        orgFiscalCode: String,
        debtPositionRequestDto: DebtPositionRequestDto,
        toPublish: Boolean
    ): ResponseEntity<DebtPositionResponseDto> {
        val debtPositionResponse =
            debtPositionService
                .createDebtPosition(
                    orgFiscalCode, debtPositionRequestDto, toPublish, xCorrelationId.toString())
                .contextWrite {
                    LogTracingUtils.enrichContextMDC(
                        mapOf(
                            LogTracingUtils.Companion.AttributeKeys.CORRELATION_ID to
                                xCorrelationId.toString()),
                        it)
                }
                .awaitSingle()
        return ResponseEntity.ok(debtPositionResponse)
    }

    override suspend fun invalidateDebtPosition(
        xCorrelationId: UUID,
        orgFiscalCode: String,
        iupd: String
    ): ResponseEntity<Unit> {
        TODO("Not yet implemented")
    }
}
