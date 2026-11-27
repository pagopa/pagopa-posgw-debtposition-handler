package it.pagopa.posgw.debtposition.handler.client

import it.pagopa.generated.posgw.gpd.api.DebtPositionActionsApiApi
import it.pagopa.generated.posgw.gpd.api.DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi
import it.pagopa.generated.posgw.gpd.model.PaymentPositionModelV3Dto
import it.pagopa.posgw.common.utils.LogTracingUtils
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
    val logger: Logger = LoggerFactory.getLogger(javaClass.name)

    companion object {
        enum class Operation() {
            CREATE_DEBT_POSITION,
            INVALIDATE_DEBT_POSITION
        }
    }

    fun createDebtPosition(
        organizationFiscalCode: String,
        paymentPositionModelV3Dto: PaymentPositionModelV3Dto,
        toPublish: Boolean?,
        xRequestId: String?
    ): Mono<PaymentPositionModelV3Dto> {

        return gpdCreateClient
            .createPosition(
                organizationFiscalCode, paymentPositionModelV3Dto, toPublish, xRequestId)
            .onErrorMap {
                mapGpdError(it, Operation.CREATE_DEBT_POSITION, organizationFiscalCode)
            }
            .doOnSuccess {
                LogTracingUtils.loggerTracingUtils()
                    .dependency(LogTracingUtils.GPD_DEPENDENCY)
                    .details(
                        mapOf(
                            "organization_fiscal_code" to organizationFiscalCode,
                            "to_publish" to toPublish.toString(),
                            "operation" to Operation.CREATE_DEBT_POSITION.name,
                        ))
                    .success()
                    .logInfo(logger, "Debt position created successfully")
            }
    }

    fun invalidateDebtPosition(
        organizationFiscalCode: String,
        iupd: String,
        xRequestId: String?
    ): Mono<PaymentPositionModelV3Dto> {

        return gpdInvalidateClient
            .invalidatePosition(organizationFiscalCode, iupd, xRequestId)
            .onErrorMap {
                mapGpdError(it, Operation.INVALIDATE_DEBT_POSITION, organizationFiscalCode)
            }
            .doOnSuccess {
                LogTracingUtils.loggerTracingUtils()
                    .dependency(LogTracingUtils.GPD_DEPENDENCY)
                    .details(
                        mapOf(
                            "organization_fiscal_code" to organizationFiscalCode,
                            "operation" to Operation.INVALIDATE_DEBT_POSITION.name,
                        ))
                    .success()
                    .logInfo(logger, "Debt position invalidated successfully")
            }
    }

    /**
     * Maps errors from GPD calls to the status and description exposed by this service.
     *
     * HTTP 4xx responses are treated as client errors and mapped to 409 Conflict. HTTP 401 and 403
     * indicate an authentication or authorization configuration problem and are mapped to 500
     * Internal Server Error. Other HTTP responses, including 5xx, are mapped to 502 Bad Gateway.
     *
     * Errors that are not [WebClientResponseException]s have no HTTP response status and are mapped
     * to 502 Bad Gateway. The [operation] is included in the error description for context.
     */
    fun mapGpdError(
        t: Throwable,
        operation: Operation,
        organizationFiscalCode: String
    ): GpdClientException {
        return when (t) {
            is WebClientResponseException -> {
                when {
                    t.statusCode == HttpStatus.UNAUTHORIZED ||
                        t.statusCode == HttpStatus.FORBIDDEN -> {
                        // misconfiguration of api key, or the GPD service is not available for the
                        // client
                        GpdClientException(
                            description =
                                "Error communicating with GPD for operation $operation with status code from GPD ${t.statusCode}",
                            httpStatusCode = HttpStatus.INTERNAL_SERVER_ERROR,
                            operation = operation.name,
                            organizationFiscalCode = organizationFiscalCode)
                    }
                    t.statusCode == HttpStatus.NOT_FOUND -> {
                        if (operation == Operation.INVALIDATE_DEBT_POSITION)
                            GpdClientException(
                                description =
                                    "Debt position not found in GPD for operation $operation with status code from GPD ${t.statusCode}",
                                httpStatusCode = HttpStatus.NOT_FOUND,
                                operation = operation.name,
                                organizationFiscalCode = organizationFiscalCode)
                        else
                            GpdClientException(
                                description =
                                    "Client error while invoking GPD for operation $operation with status code from GPD ${t.statusCode}",
                                httpStatusCode = HttpStatus.CONFLICT,
                                operation = operation.name,
                                organizationFiscalCode = organizationFiscalCode)
                    }
                    t.statusCode.is4xxClientError -> {
                        GpdClientException(
                            description =
                                "Client error while invoking GPD for operation $operation with status code from GPD ${t.statusCode}",
                            httpStatusCode = HttpStatus.CONFLICT,
                            operation = operation.name,
                            organizationFiscalCode = organizationFiscalCode)
                    }
                    // 5xx and other errors
                    else -> {
                        GpdClientException(
                            description =
                                "Unexpected error while invoking GPD for operation $operation with status code from GPD ${t.statusCode}",
                            httpStatusCode = HttpStatus.BAD_GATEWAY,
                            operation = operation.name,
                            organizationFiscalCode = organizationFiscalCode)
                    }
                }
            }
            else -> {
                GpdClientException(
                    description = "Unexpected error while invoking GPD for operation $operation",
                    httpStatusCode = HttpStatus.BAD_GATEWAY,
                    operation = operation.name,
                    organizationFiscalCode = organizationFiscalCode,
                    cause = t)
            }
        }
    }
}
