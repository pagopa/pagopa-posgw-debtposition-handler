package it.pagopa.posgw.debtposition.handler.controllers

import it.gov.pagopa.noticenumber.exception.AppErrorCodeMessageEnum
import it.gov.pagopa.noticenumber.exception.AppException
import it.pagopa.generated.posgw.debtposition.handler.model.DebtPositionRequestDto
import it.pagopa.generated.posgw.debtposition.handler.model.DebtPositionResponseDto
import it.pagopa.generated.posgw.debtposition.handler.model.DebtorDto
import it.pagopa.generated.posgw.debtposition.handler.model.InstallmentDetailDto
import it.pagopa.generated.posgw.debtposition.handler.model.PaymentOptionDto
import it.pagopa.generated.posgw.debtposition.handler.model.TransferItemDto
import it.pagopa.posgw.debtposition.handler.exception.GpdClientException
import it.pagopa.posgw.debtposition.handler.exceptionhandler.ExceptionHandler
import it.pagopa.posgw.debtposition.handler.services.DebtPositionsService
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono

class DebtPositionsControllerTest {

    private val serviceMock: DebtPositionsService = mock(DebtPositionsService::class.java)
    private lateinit var controller: DebtPositionsController
    private lateinit var webTestClient: WebTestClient

    private val orgFiscalCode = "12345678901"
    private val correlationId: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        controller = DebtPositionsController(serviceMock)
        webTestClient =
            WebTestClient.bindToController(controller).controllerAdvice(ExceptionHandler()).build()
    }

    private fun buildSampleDebtPositionRequestDto(): DebtPositionRequestDto {
        val transferItem =
            TransferItemDto(
                amount = 1000L,
                remittanceInformation = "/RFB/02000100000012345/CNR/ROSMRI87A04H501K/TXT/Causale",
                iban = "IT0000000000000000000000000",
                category = "9/0101100SP/")

        val debtor =
            DebtorDto(
                type = DebtorDto.Type.F, fiscalCode = "ROSMRI87A04H501K", fullName = "Mario Rossi")

        val installment =
            InstallmentDetailDto(
                transferList = listOf(transferItem),
                iuv = "02000100000012345",
                description = "Test Payment Description",
                dueDate = OffsetDateTime.now())

        return DebtPositionRequestDto(
            companyName = "Comune di Fantasia",
            iupd = "IUPD-2024-XYZ-001",
            officeName = "Ufficio Anagrafe",
            switchToExpired = false,
            debtor = debtor,
            paymentOption = PaymentOptionDto(installment = installment))
    }

    private val expectedResponse =
        DebtPositionResponseDto(
            creditorInstitutionFiscalCode = orgFiscalCode,
            noticeNumber = "302000100000012345",
            iuv = "02000100000012345",
            iupd = "IUPD-2024-XYZ-001",
            status = DebtPositionResponseDto.Status.VALID)

    // Mockito matchers return null, which Kotlin rejects for non-null parameters
    private fun anyRequest(): DebtPositionRequestDto {
        any(DebtPositionRequestDto::class.java)
        return buildSampleDebtPositionRequestDto()
    }

    private fun eqNonNull(value: String): String {
        eq(value)
        return value
    }

    private fun serviceReturns(result: Mono<DebtPositionResponseDto>) {
        given(serviceMock.createDebtPosition(anyString(), anyRequest(), any(), any()))
            .willReturn(result)
    }

    private fun postCreate(
        correlationHeader: String? = correlationId.toString(),
        body: Any = buildSampleDebtPositionRequestDto()
    ): WebTestClient.ResponseSpec {
        val request = webTestClient.post().uri("/debt-positions/{orgFiscalCode}", orgFiscalCode)
        if (correlationHeader != null) request.header("x-correlation-id", correlationHeader)
        return request.contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange()
    }

    @Test
    fun `createDebtPosition direct call returns 200 with the service response`() = runTest {
        serviceReturns(Mono.just(expectedResponse))

        val response =
            controller.createDebtPosition(
                correlationId, orgFiscalCode, buildSampleDebtPositionRequestDto())

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(expectedResponse, response.body)
    }

    @Test
    fun `createDebtPosition direct call propagates the service error`() = runTest {
        val error =
            GpdClientException(
                description = "GPD failed",
                httpStatusCode = HttpStatus.BAD_GATEWAY,
                operation = "CREATE_DEBT_POSITION",
                organizationFiscalCode = orgFiscalCode)
        serviceReturns(Mono.error(error))

        val thrown =
            assertThrows<GpdClientException> {
                controller.createDebtPosition(
                    correlationId, orgFiscalCode, buildSampleDebtPositionRequestDto())
            }
        assertEquals(error, thrown)
    }

    @Test
    fun `POST createDebtPosition returns 200 and the debt position data`() {
        serviceReturns(Mono.just(expectedResponse))

        postCreate()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.creditorInstitutionFiscalCode")
            .isEqualTo(orgFiscalCode)
            .jsonPath("$.noticeNumber")
            .isEqualTo("302000100000012345")
            .jsonPath("$.iuv")
            .isEqualTo("02000100000012345")
            .jsonPath("$.iupd")
            .isEqualTo("IUPD-2024-XYZ-001")
            .jsonPath("$.status")
            .isEqualTo("VALID")
    }

    @Test
    fun `POST createDebtPosition calls the service with path, correlation id and publication`() {
        serviceReturns(Mono.just(expectedResponse))

        postCreate().expectStatus().isOk

        val requestCaptor = ArgumentCaptor.forClass(DebtPositionRequestDto::class.java)
        verify(serviceMock)
            .createDebtPosition(
                eqNonNull(orgFiscalCode),
                requestCaptor.capture() ?: buildSampleDebtPositionRequestDto(),
                eq(true),
                eq(correlationId.toString()))
        assertEquals("IUPD-2024-XYZ-001", requestCaptor.value.iupd)
    }

    @Test
    fun `POST createDebtPosition returns the GPD error status and problem json`() {
        serviceReturns(
            Mono.error(
                GpdClientException(
                    description = "GPD failed",
                    httpStatusCode = HttpStatus.CONFLICT,
                    operation = "CREATE_DEBT_POSITION",
                    organizationFiscalCode = orgFiscalCode)))

        postCreate()
            .expectStatus()
            .isEqualTo(HttpStatus.CONFLICT)
            .expectBody()
            .jsonPath("$.title")
            .isEqualTo("GPD Invocation exception")
            .jsonPath("$.status")
            .isEqualTo(409)
            .jsonPath("$.detail")
            .isEqualTo("GPD failed")
    }

    @Test
    fun `POST createDebtPosition returns 503 when the notice number cannot be generated`() {
        serviceReturns(
            Mono.error(
                AppException(
                    AppErrorCodeMessageEnum.GENERATION_MAX_RETRIES_REACHED, 3, orgFiscalCode)))

        postCreate()
            .expectStatus()
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo(503)
    }

    @Test
    fun `POST createDebtPosition returns 500 on unexpected errors`() {
        serviceReturns(Mono.error(IllegalStateException("boom")))

        postCreate()
            .expectStatus()
            .is5xxServerError
            .expectBody()
            .jsonPath("$.detail")
            .isEqualTo("Unexpected error while processing the request")
    }

    @Test
    fun `POST createDebtPosition returns 400 when the correlation id header is missing`() {
        postCreate(correlationHeader = null).expectStatus().isBadRequest

        verify(serviceMock, never()).createDebtPosition(anyString(), anyRequest(), any(), any())
    }

    @Test
    fun `POST createDebtPosition returns 400 when the correlation id is not a UUID`() {
        postCreate(correlationHeader = "not-a-uuid").expectStatus().isBadRequest
    }

    @Test
    fun `POST createDebtPosition returns 400 when the body is malformed`() {
        postCreate(body = """{"iupd": "missing required fields"}""").expectStatus().isBadRequest
    }

    @Test
    fun `invalidateDebtPosition is not implemented yet`() = runTest {
        assertThrows<NotImplementedError> {
            controller.invalidateDebtPosition(correlationId, orgFiscalCode, "IUPD-2024-XYZ-001")
        }
    }
}
