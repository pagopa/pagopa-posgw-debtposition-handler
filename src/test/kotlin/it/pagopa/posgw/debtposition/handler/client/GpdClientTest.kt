package it.pagopa.posgw.debtposition.handler.client

import com.sun.net.httpserver.HttpServer
import it.pagopa.generated.posgw.gpd.api.DebtPositionActionsApiApi
import it.pagopa.generated.posgw.gpd.api.DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi
import it.pagopa.generated.posgw.gpd.model.PaymentPositionModelV3Dto
import it.pagopa.posgw.debtposition.handler.exception.GpdClientException
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@SpringBootTest
@TestPropertySource(locations = ["classpath:application.test.properties"])
class GpdClientTest {

    val gpdInvalidateClient: DebtPositionActionsApiApi = mock()
    val gpdCreateClient: DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi = mock()

    val gpdClient: GpdClient = GpdClient(gpdInvalidateClient, gpdCreateClient)

    @Value("\${gpd.apiKey}") lateinit var apiKey: String
    @Autowired lateinit var realGpdInvalidateClient: DebtPositionActionsApiApi
    @Autowired
    lateinit var realGpdCreateClient: DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi

    @Test
    fun `Should create a debt position successfully`() {
        val paymentPosition = PaymentPositionModelV3Dto()
        paymentPosition.iupd = "test-iupd"

        given(
                gpdCreateClient.createPosition(
                    "test-fiscalcode", paymentPosition, true, "test-request-id"))
            .willReturn(Mono.just(paymentPosition))

        StepVerifier.create(
                gpdClient.createDebtPosition(
                    "test-fiscalcode", paymentPosition, true, "test-request-id"))
            .expectNext(paymentPosition)
            .verifyComplete()
    }

    @Test
    fun `Should invalidate a debt position successfully`() {
        val paymentPosition = PaymentPositionModelV3Dto()
        paymentPosition.iupd = "test-iupd"

        given(
                gpdInvalidateClient.invalidatePosition(
                    "test-fiscalcode", paymentPosition.iupd, "test-request-id"))
            .willReturn(Mono.just(paymentPosition))

        StepVerifier.create(
                gpdClient.invalidateDebtPosition("test-fiscalcode", "test-iupd", "test-request-id"))
            .expectNext(paymentPosition)
            .verifyComplete()
    }

    private fun webClientError(status: Int) =
        WebClientResponseException.create(
            status, "GPD error", HttpHeaders.EMPTY, byteArrayOf(), null)

    private fun createFailsWith(
        error: Throwable
    ): StepVerifier.FirstStep<PaymentPositionModelV3Dto> {
        given(gpdCreateClient.createPosition(any(), any(), any(), any()))
            .willReturn(Mono.error(error))
        return StepVerifier.create(
            gpdClient.createDebtPosition(
                "test-fiscalcode", PaymentPositionModelV3Dto(), true, "test-request-id"))
    }

    private fun invalidateFailsWith(
        error: Throwable
    ): StepVerifier.FirstStep<PaymentPositionModelV3Dto> {
        given(gpdInvalidateClient.invalidatePosition(any(), any(), any()))
            .willReturn(Mono.error(error))
        return StepVerifier.create(
            gpdClient.invalidateDebtPosition("test-fiscalcode", "test-iupd", "test-request-id"))
    }

    private fun StepVerifier.FirstStep<PaymentPositionModelV3Dto>.expectGpdError(
        status: HttpStatus,
        description: String
    ) {
        expectErrorMatches {
                it is GpdClientException &&
                    it.toRestException().httpStatusCode == status &&
                    it.toRestException().description == description
            }
            .verify()
    }

    @Test
    fun `Should map 400 to bad request when creating a debt position`() {
        createFailsWith(webClientError(400))
            .expectGpdError(HttpStatus.BAD_REQUEST, "Malformed request")
    }

    @Test
    fun `Should map 404 to not found when creating a debt position`() {
        createFailsWith(webClientError(404))
            .expectGpdError(HttpStatus.NOT_FOUND, "Debt position not found")
    }

    @Test
    fun `Should map 409 to duplicate debt position when creating a debt position`() {
        createFailsWith(webClientError(409))
            .expectGpdError(HttpStatus.CONFLICT, "Duplicate debt position found.")
    }

    @Test
    fun `Should map 503 to service unavailable when creating a debt position`() {
        createFailsWith(webClientError(503))
            .expectGpdError(HttpStatus.SERVICE_UNAVAILABLE, "GPD service is unavailable")
    }

    @ParameterizedTest
    @ValueSource(ints = [401, 422, 500, 504])
    fun `Should map other GPD statuses to bad gateway when creating a debt position`(status: Int) {
        createFailsWith(webClientError(status))
            .expectGpdError(
                HttpStatus.BAD_GATEWAY,
                "Unexpected error from GPD service with status code ${HttpStatus.valueOf(status)}")
    }

    @Test
    fun `Should map non HTTP errors to internal server error when creating a debt position`() {
        createFailsWith(RuntimeException("Unexpected error"))
            .expectGpdError(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error while invoking GPD")
    }

    @Test
    fun `Should map 400 to bad request when invalidating a debt position`() {
        invalidateFailsWith(webClientError(400))
            .expectGpdError(HttpStatus.BAD_REQUEST, "Malformed request")
    }

    @Test
    fun `Should map 404 to not found when invalidating a debt position`() {
        invalidateFailsWith(webClientError(404))
            .expectGpdError(HttpStatus.NOT_FOUND, "Debt position not found")
    }

    @Test
    fun `Should map 409 to generic conflict when invalidating a debt position`() {
        invalidateFailsWith(webClientError(409))
            .expectGpdError(HttpStatus.CONFLICT, "Conflict error from GPD service")
    }

    @Test
    fun `Should map 503 to service unavailable when invalidating a debt position`() {
        invalidateFailsWith(webClientError(503))
            .expectGpdError(HttpStatus.SERVICE_UNAVAILABLE, "GPD service is unavailable")
    }

    @ParameterizedTest
    @ValueSource(ints = [401, 422, 500, 504])
    fun `Should map other GPD statuses to bad gateway when invalidating a debt position`(
        status: Int
    ) {
        invalidateFailsWith(webClientError(status))
            .expectGpdError(
                HttpStatus.BAD_GATEWAY,
                "Unexpected error from GPD service with status code ${HttpStatus.valueOf(status)}")
    }

    @Test
    fun `Should map non HTTP errors to internal server error when invalidating a debt position`() {
        invalidateFailsWith(RuntimeException("Unexpected error"))
            .expectGpdError(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error while invoking GPD")
    }

    @Test
    fun `Should inject api key header when making request to GPD service`() {
        val receivedHeader = AtomicReference<String?>()
        val server = HttpServer.create(InetSocketAddress("localhost", 3002), 0)
        server.createContext("/") { exchange ->
            receivedHeader.set(exchange.requestHeaders.getFirst("Ocp-Apim-Subscription-Key"))
            val body = """{"iupd":"test-iupd"}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        try {
            val realClient = GpdClient(realGpdInvalidateClient, realGpdCreateClient)

            StepVerifier.create(
                    realClient.invalidateDebtPosition(
                        "test-fiscalcode", "test-iupd", "test-request-id"))
                .expectNextMatches { it.iupd == "test-iupd" }
                .verifyComplete()

            assertEquals(apiKey, receivedHeader.get())
        } finally {
            server.stop(0)
        }
    }
}
