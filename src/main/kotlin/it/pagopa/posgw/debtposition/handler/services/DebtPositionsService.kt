package it.pagopa.posgw.debtposition.handler.services

import it.pagopa.generated.posgw.debtposition.handler.model.DebtPositionRequestDto
import it.pagopa.generated.posgw.debtposition.handler.model.DebtorDto
import it.pagopa.generated.posgw.debtposition.handler.model.InstallmentDetailDto
import it.pagopa.generated.posgw.debtposition.handler.model.TransferItemDto
import it.pagopa.generated.posgw.gpd.model.DebtorModelDto
import it.pagopa.generated.posgw.gpd.model.InstallmentMetadataModelDto
import it.pagopa.generated.posgw.gpd.model.InstallmentModelDto
import it.pagopa.generated.posgw.gpd.model.PaymentOptionModelV3Dto
import it.pagopa.generated.posgw.gpd.model.PaymentPositionModelV3Dto
import it.pagopa.generated.posgw.gpd.model.TransferModelDto
import it.pagopa.posgw.debtposition.handler.client.GpdClient
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
            toPaymentPosition(organizationFiscalCode, debtPositionRequestDto)

        return gpdClient.createDebtPosition(
            organizationFiscalCode, paymentPositionModelV3Dto, toPublish, xRequestId)
    }

    fun toPaymentPosition(
        organizationFiscalCode: String,
        request: DebtPositionRequestDto
    ): PaymentPositionModelV3Dto {
        val installment = request.paymentOption.installment

        val paymentOption =
            PaymentOptionModelV3Dto()
                .description(installment.description)
                .switchToExpired(request.switchToExpired)
                .debtor(toDebtor(request.debtor))
                .installments(listOf(toInstallment(organizationFiscalCode, installment)))

        return PaymentPositionModelV3Dto()
            .iupd(request.iupd)
            .companyName(request.companyName)
            .officeName(request.officeName)
            .paymentOption(listOf(paymentOption))
    }

    private fun toDebtor(debtor: DebtorDto): DebtorModelDto =
        DebtorModelDto()
            .type(DebtorModelDto.TypeEnum.fromValue(debtor.type.value))
            .fiscalCode(debtor.fiscalCode)
            .fullName(debtor.fullName)

    private fun toInstallment(
        organizationFiscalCode: String,
        installment: InstallmentDetailDto
    ): InstallmentModelDto {
        val totalAmount = installment.transferList.sumOf { it.amount }

        return InstallmentModelDto()
            .iuv(installment.iuv)
            .amount(totalAmount)
            .description(installment.description)
            .dueDate(installment.dueDate)
            .transfer(
                installment.transferList.mapIndexed { index, transfer ->
                    toTransfer(organizationFiscalCode, index + 1, transfer)
                })
            .installmentMetadata(toMetadata(installment.installmentMetadata))
    }

    // GPD transfer ids are the enum values 1..5, the same upper bound as the request transferList
    private fun toTransfer(
        organizationFiscalCode: String,
        transferId: Int,
        transfer: TransferItemDto
    ): TransferModelDto =
        TransferModelDto()
            .idTransfer(TransferModelDto.IdTransferEnum.fromValue(transferId.toString()))
            .amount(transfer.amount)
            .organizationFiscalCode(organizationFiscalCode)
            .remittanceInformation(transfer.remittanceInformation)
            .category(transfer.category)
            .iban(transfer.iban)

    // Metadata is optional: anything that is not a flat map of scalar values is ignored
    private fun toMetadata(metadata: Any?): Set<InstallmentMetadataModelDto> =
        (metadata as? Map<*, *>)
            ?.mapNotNull { (key, value) ->
                if (key is String && (value is String || value is Number || value is Boolean)) {
                    InstallmentMetadataModelDto().key(key).value(value.toString())
                } else {
                    null
                }
            }
            ?.toSet() ?: emptySet()
}
