package it.pagopa.posgw.debtposition.handler.mapper

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

/**
 * Builds the GPD payment-position payload from a debt-position API request. It maps the request's
 * identifier and organization details, creates a payment option with its debtor and installment,
 * and maps the installment's transfers and metadata. The supplied organization's fiscal code is
 * assigned to every generated transfer.
 */
fun DebtPositionRequestDto.toPaymentPosition(
    organizationFiscalCode: String,
    nav: String
): PaymentPositionModelV3Dto {
    val installment = this.paymentOption.installment

    val paymentOption =
        PaymentOptionModelV3Dto()
            .description(installment.description)
            // switchToExpired always true
            // (https://pagopa.atlassian.net/wiki/spaces/I/pages/3316645948/DR+PFM-1+-+Specifiche+integrazione+POS+Gateway+-+GPD#Creazione-one-shot)
            .switchToExpired(this.switchToExpired)
            .debtor(debtor.toGpdDebtor())
            .installments(listOf(installment.toGpdInstallment(organizationFiscalCode, nav)))

    return PaymentPositionModelV3Dto()
        .iupd(this.iupd)
        .companyName(this.companyName)
        .officeName(this.officeName)
        .paymentOption(listOf(paymentOption))
}

private fun DebtorDto.toGpdDebtor(): DebtorModelDto =
    DebtorModelDto()
        .type(DebtorModelDto.TypeEnum.fromValue(this.type.value))
        .fiscalCode(this.fiscalCode)
        .fullName(this.fullName)

private fun InstallmentDetailDto.toGpdInstallment(
    organizationFiscalCode: String,
    nav: String
): InstallmentModelDto {
    val totalAmount = this.transferList.sumOf { it.amount }

    return InstallmentModelDto()
        .iuv(this.iuv.ifBlank { nav.substring(1) })
        .nav(nav)
        .amount(totalAmount)
        .description(this.description)
        .dueDate(this.dueDate)
        .transfer(
            this.transferList.mapIndexed { index, transfer ->
                transfer.toGpdTransfer(organizationFiscalCode, index + 1)
            })
        .installmentMetadata(toMetadata(this.installmentMetadata))
}

// GPD transfer ids are the enum values 1..5, the same upper bound as the request transferList
private fun TransferItemDto.toGpdTransfer(
    organizationFiscalCode: String,
    transferId: Int
): TransferModelDto =
    TransferModelDto()
        .idTransfer(TransferModelDto.IdTransferEnum.fromValue(transferId.toString()))
        .amount(this.amount)
        .organizationFiscalCode(organizationFiscalCode)
        .remittanceInformation(this.remittanceInformation)
        .category(this.category)
        .iban(this.iban)

// Metadata is optional: anything that is not a flat map of scalar values is ignored.
// Returns java.util.LinkedHashSet on purpose: Kotlin's EmptySet breaks Jackson's Kotlin module in
// the native image.
private fun toMetadata(metadata: Any?): Set<InstallmentMetadataModelDto> =
    (metadata as? Map<*, *>)
        ?.mapNotNull { (key, value) ->
            if (key is String && (value is String || value is Number || value is Boolean)) {
                InstallmentMetadataModelDto().key(key).value(value.toString())
            } else {
                null
            }
        }
        ?.toCollection(LinkedHashSet()) ?: LinkedHashSet()
