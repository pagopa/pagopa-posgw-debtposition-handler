package it.pagopa.posgw.debtposition.handler.controllers

import it.pagopa.generated.posgw.debtposition.handler.api.DebtPositionsApi
import it.pagopa.generated.posgw.debtposition.handler.model.DebtPositionRequestDto
import it.pagopa.generated.posgw.debtposition.handler.model.DebtPositionResponseDto
import java.util.UUID
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class DebtPositionsController : DebtPositionsApi {
    override suspend fun createDebtPosition(
        xCorrelationId: UUID,
        debtPositionRequestDto: DebtPositionRequestDto
    ): ResponseEntity<DebtPositionResponseDto> {
        TODO("Not yet implemented")
    }
}
