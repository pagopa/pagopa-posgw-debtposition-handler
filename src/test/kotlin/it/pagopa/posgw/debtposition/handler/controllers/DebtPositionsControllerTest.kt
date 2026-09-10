package it.pagopa.posgw.debtposition.handler.controllers

import it.pagopa.generated.posgw.debtposition.handler.model.DebtPositionRequestDto
import it.pagopa.generated.posgw.debtposition.handler.model.DebtorDto
import it.pagopa.generated.posgw.debtposition.handler.model.InstallmentDetailDto
import it.pagopa.generated.posgw.debtposition.handler.model.PaymentOptionDto
import it.pagopa.generated.posgw.debtposition.handler.model.TransferItemDto
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.OffsetDateTime
import java.util.UUID

class DebtPositionsControllerTest {

    private lateinit var controller: DebtPositionsController
    private lateinit var webTestClient: WebTestClient

    @BeforeEach
    fun setUp() {
        controller = DebtPositionsController()
        webTestClient = WebTestClient.bindToController(controller).build()
    }

    private fun buildSampleDebtPositionRequestDto(): DebtPositionRequestDto {
        val transferItem = TransferItemDto(
            amount = 1000L,
            remittanceInformation = "/RFB/02000100000012345/CNR/ROSMRI87A04H501K/TXT/Causale",
            iban = "IT0000000000000000000000000",
            category = "9/0101100SP/"
        )

        val debtor = DebtorDto(
            type = DebtorDto.Type.F,
            fiscalCode = "ROSMRI87A04H501K",
            fullName = "Mario Rossi"
        )

        val installment = InstallmentDetailDto(
            transferList = listOf(transferItem),
            iuv = "020001000000123456",
            description = "Test Payment Description",
            dueDate = OffsetDateTime.now()
        )

        val paymentOption = PaymentOptionDto(
            installment = installment
        )

        return DebtPositionRequestDto(
            companyName = "Comune di Fantasia",
            iupd = "IUPD-2024-XYZ-001",
            officeName = "Ufficio Anagrafe",
            switchToExpired = false,
            debtor = debtor,
            paymentOption = paymentOption
        )
    }

    @Test
    fun `createDebtPosition direct call throws NotImplementedError`() = runTest {
        val correlationId = UUID.randomUUID()
        val requestBody = buildSampleDebtPositionRequestDto()

        assertThrows<NotImplementedError> {
            controller.createDebtPosition(correlationId, requestBody)
        }
    }

    @Test
    fun `POST createDebtPosition endpoint returns 500 when TODO is executed`() {
        val correlationId = UUID.randomUUID()
        val requestDto = buildSampleDebtPositionRequestDto()

        webTestClient.post()
            .uri("/debt-positions")
            .header("x-correlation-id", correlationId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestDto)
            .exchange()
            .expectStatus().is5xxServerError
    }
}