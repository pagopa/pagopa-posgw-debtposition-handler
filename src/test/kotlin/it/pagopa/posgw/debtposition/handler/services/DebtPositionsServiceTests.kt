package it.pagopa.posgw.debtposition.handler.services

import it.gov.pagopa.noticenumber.config.NoticeNumberProperties
import it.gov.pagopa.noticenumber.exception.AppErrorCodeMessageEnum
import it.gov.pagopa.noticenumber.exception.AppException
import it.gov.pagopa.noticenumber.model.NoticeNumberGenerationResponse
import it.gov.pagopa.noticenumber.service.NoticeNumberGeneratorService
import it.pagopa.generated.posgw.debtposition.handler.model.DebtPositionRequestDto
import it.pagopa.generated.posgw.debtposition.handler.model.DebtPositionResponseDto
import it.pagopa.generated.posgw.debtposition.handler.model.DebtorDto
import it.pagopa.generated.posgw.debtposition.handler.model.InstallmentDetailDto
import it.pagopa.generated.posgw.debtposition.handler.model.PaymentOptionDto
import it.pagopa.generated.posgw.debtposition.handler.model.TransferItemDto
import it.pagopa.generated.posgw.gpd.model.PaymentPositionModelV3Dto
import it.pagopa.posgw.debtposition.handler.client.GpdClient
import it.pagopa.posgw.debtposition.handler.exception.GpdClientException
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.http.HttpStatus
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class DebtPositionsServiceTests {

    private val gpdClient: GpdClient = mock()
    private val noticeNumberGeneratorService: NoticeNumberGeneratorService = mock()
    private val noticeNumberProperties =
        NoticeNumberProperties().apply {
            auxDigit = 3
            segregationCode = 3
        }

    private lateinit var service: DebtPositionsService

    private val organizationFiscalCode = "12345678901"
    private val requestId = "test-request-id"

    @BeforeEach
    fun setUp() {
        service =
            DebtPositionsService(gpdClient, noticeNumberGeneratorService, noticeNumberProperties)
    }

    private fun request(iuv: String) =
        DebtPositionRequestDto(
            companyName = "Comune di Fantasia",
            iupd = "IUPD-2024-XYZ-001",
            officeName = "Ufficio Anagrafe",
            switchToExpired = false,
            debtor =
                DebtorDto(
                    type = DebtorDto.Type.F,
                    fiscalCode = "ROSMRI87A04H501K",
                    fullName = "Mario Rossi"),
            paymentOption =
                PaymentOptionDto(
                    installment =
                        InstallmentDetailDto(
                            transferList =
                                listOf(
                                    TransferItemDto(
                                        amount = 1000L,
                                        remittanceInformation = "/RFB/0200/TXT/Causale",
                                        iban = "IT0000000000000000000000000",
                                        category = "9/0101100SP/"),
                                    TransferItemDto(
                                        amount = 500L,
                                        remittanceInformation = "/RFB/0200/TXT/Causale 2",
                                        iban = "IT0000000000000000000001",
                                        category = "9/0101100SP/")),
                            iuv = iuv,
                            description = "Test Payment Description",
                            dueDate = OffsetDateTime.now())))

    // Mockito matchers return null, which Kotlin rejects for non-null parameters
    private fun anyPosition(): PaymentPositionModelV3Dto {
        any(PaymentPositionModelV3Dto::class.java)
        return PaymentPositionModelV3Dto()
    }

    private fun eqNonNull(value: String): String {
        eq(value)
        return value
    }

    private fun gpdReturns(status: PaymentPositionModelV3Dto.StatusEnum?) {
        val created = PaymentPositionModelV3Dto().iupd("IUPD-2024-XYZ-001")
        // status is read-only in the generated model, since GPD is the one that assigns it
        PaymentPositionModelV3Dto::class.java.getDeclaredField("status").apply {
            isAccessible = true
            set(created, status)
        }
        given(gpdClient.createDebtPosition(anyString(), anyPosition(), any(), any()))
            .willReturn(Mono.just(created))
    }

    private fun capturePositionSentToGpd(toPublish: Boolean?): PaymentPositionModelV3Dto {
        val captor = ArgumentCaptor.forClass(PaymentPositionModelV3Dto::class.java)
        verify(gpdClient)
            .createDebtPosition(
                eqNonNull(organizationFiscalCode),
                captor.capture() ?: PaymentPositionModelV3Dto(),
                eq(toPublish),
                eq(requestId))
        return captor.value
    }

    @Test
    fun `Should build the notice number from the provided IUV without generating one`() {
        gpdReturns(PaymentPositionModelV3Dto.StatusEnum.VALID)

        StepVerifier.create(
                service.createDebtPosition(
                    organizationFiscalCode, request("02000100000012345"), true, requestId))
            .expectNext(
                DebtPositionResponseDto(
                    creditorInstitutionFiscalCode = organizationFiscalCode,
                    noticeNumber = "302000100000012345",
                    iuv = "02000100000012345",
                    iupd = "IUPD-2024-XYZ-001",
                    status = DebtPositionResponseDto.Status.VALID))
            .verifyComplete()

        verify(noticeNumberGeneratorService, never()).generateNoticeNumber(anyString())
        val installment = capturePositionSentToGpd(true).paymentOption[0].installments[0]
        assertEquals("302000100000012345", installment.nav)
        assertEquals("02000100000012345", installment.iuv)
    }

    @Test
    fun `Should generate the notice number when the IUV is blank`() {
        given(noticeNumberGeneratorService.generateNoticeNumber(organizationFiscalCode))
            .willReturn(NoticeNumberGenerationResponse("399999999999999999"))
        gpdReturns(PaymentPositionModelV3Dto.StatusEnum.VALID)

        StepVerifier.create(
                service.createDebtPosition(organizationFiscalCode, request(""), true, requestId))
            .expectNext(
                DebtPositionResponseDto(
                    creditorInstitutionFiscalCode = organizationFiscalCode,
                    noticeNumber = "399999999999999999",
                    iuv = "99999999999999999",
                    iupd = "IUPD-2024-XYZ-001",
                    status = DebtPositionResponseDto.Status.VALID))
            .verifyComplete()

        verify(noticeNumberGeneratorService).generateNoticeNumber(organizationFiscalCode)
        val installment = capturePositionSentToGpd(true).paymentOption[0].installments[0]
        assertEquals("399999999999999999", installment.nav)
        assertEquals("99999999999999999", installment.iuv)
    }

    @Test
    fun `Should map the request to the GPD payload`() {
        gpdReturns(PaymentPositionModelV3Dto.StatusEnum.VALID)

        service
            .createDebtPosition(
                organizationFiscalCode, request("02000100000012345"), false, requestId)
            .block()

        val position = capturePositionSentToGpd(false)
        val installment = position.paymentOption[0].installments[0]
        assertEquals("IUPD-2024-XYZ-001", position.iupd)
        assertEquals(1500L, installment.amount)
        assertEquals(2, installment.transfer.size)
        assertEquals(organizationFiscalCode, installment.transfer[0].organizationFiscalCode)
    }

    @Test
    fun `Should return a response without status when GPD does not return one`() {
        gpdReturns(null)

        StepVerifier.create(
                service.createDebtPosition(
                    organizationFiscalCode, request("02000100000012345"), true, requestId))
            .expectNextMatches { it.status == null && it.noticeNumber == "302000100000012345" }
            .verifyComplete()
    }

    @Test
    fun `Should propagate the notice number generation error without calling GPD`() {
        given(noticeNumberGeneratorService.generateNoticeNumber(organizationFiscalCode))
            .willThrow(
                AppException(
                    AppErrorCodeMessageEnum.GENERATION_MAX_RETRIES_REACHED,
                    3,
                    organizationFiscalCode))

        StepVerifier.create(
                service.createDebtPosition(organizationFiscalCode, request(""), true, requestId))
            .expectError(AppException::class.java)
            .verify()

        verify(gpdClient, never()).createDebtPosition(anyString(), anyPosition(), any(), any())
    }

    @Test
    fun `Should propagate the GPD client error`() {
        val gpdError =
            GpdClientException(
                description = "GPD failed",
                httpStatusCode = HttpStatus.BAD_GATEWAY,
                operation = "CREATE_DEBT_POSITION",
                organizationFiscalCode = organizationFiscalCode)
        given(gpdClient.createDebtPosition(anyString(), anyPosition(), any(), any()))
            .willReturn(Mono.error(gpdError))

        StepVerifier.create(
                service.createDebtPosition(
                    organizationFiscalCode, request("02000100000012345"), true, requestId))
            .expectErrorMatches { it === gpdError }
            .verify()
    }
}
