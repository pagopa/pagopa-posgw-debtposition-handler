package it.pagopa.posgw.debtposition.handler.services

import it.gov.pagopa.noticenumber.config.NoticeNumberProperties
import it.gov.pagopa.noticenumber.service.NoticeNumberGeneratorService
import it.pagopa.generated.posgw.debtposition.handler.model.DebtPositionRequestDto
import it.pagopa.generated.posgw.debtposition.handler.model.DebtPositionResponseDto
import it.pagopa.generated.posgw.gpd.model.PaymentPositionModelV3Dto
import it.pagopa.posgw.common.utils.LogTracingUtils
import it.pagopa.posgw.debtposition.handler.client.GpdClient
import it.pagopa.posgw.debtposition.handler.mapper.toPaymentPosition
import java.util.UUID
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@Service
class DebtPositionsService(
    private val gpdClient: GpdClient,
    private val noticeNumberGeneratorService: NoticeNumberGeneratorService,
    private val noticeNumberProperties: NoticeNumberProperties
) {

    val logger: Logger = LoggerFactory.getLogger(javaClass.name)

    fun createDebtPosition(
        organizationFiscalCode: String,
        debtPositionRequestDto: DebtPositionRequestDto,
        toPublish: Boolean?,
        xRequestId: String?
    ): Mono<DebtPositionResponseDto> =
        Mono.just(debtPositionRequestDto.paymentOption.installment.iuv)
            .flatMap { iuv ->
                // Check if the IUV is provided in the request
                if (iuv.isBlank()) {
                    Mono.fromCallable {
                            noticeNumberGeneratorService
                                .generateNoticeNumber(organizationFiscalCode)
                                .noticeNumber
                        }
                        .subscribeOn(Schedulers.boundedElastic())
                } else {
                    // Should we check if the provided IUV is unique?
                    Mono.just(noticeNumberProperties.auxDigit.toString() + iuv)
                }
            }
            .map { nav ->
                Pair(nav, debtPositionRequestDto.toPaymentPosition(organizationFiscalCode, nav))
            }
            .flatMap { (nav, paymentPositionModelV3Dto) ->
                gpdClient
                    .createDebtPosition(
                        organizationFiscalCode, paymentPositionModelV3Dto, toPublish, xRequestId)
                    .map {
                        Pair(nav, it)
                    }
            }
            .map { (nav, paymentPositionModelV3Dto) ->
                if (paymentPositionModelV3Dto.status != null)
                    DebtPositionResponseDto(
                        organizationFiscalCode,
                        nav,
                        nav.substring(1),
                        debtPositionRequestDto.iupd,
                        DebtPositionResponseDto.Status.forValue(
                            paymentPositionModelV3Dto.status.value))
                else
                    DebtPositionResponseDto(
                        organizationFiscalCode, nav, nav.substring(1), debtPositionRequestDto.iupd)
            }
            .doOnSuccess { _ ->
                LogTracingUtils.loggerTracingUtils()
                    .dependency(LogTracingUtils.GPD_DEPENDENCY)
                    .details(
                        mapOf(
                            "organization_fiscal_code" to organizationFiscalCode,
                        ))
                    .success()
                    .logInfo(logger, "Debt position created successfully")
            }
            .doOnError { _ ->
                LogTracingUtils.loggerTracingUtils()
                    .dependency(LogTracingUtils.GPD_DEPENDENCY)
                    .details(
                        mapOf(
                            "organization_fiscal_code" to organizationFiscalCode,
                        ))
                    .failure()
                    .logError(logger, "Error creating debt position")
            }

    fun invalidateDebtPosition(
        xCorrelationId: UUID,
        orgFiscalCode: String,
        iupd: String,
    ): Mono<PaymentPositionModelV3Dto> {
        return gpdClient.invalidateDebtPosition(orgFiscalCode, iupd, xCorrelationId.toString())
    }
}
