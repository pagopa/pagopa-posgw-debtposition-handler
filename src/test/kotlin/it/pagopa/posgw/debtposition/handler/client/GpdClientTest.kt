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

    @Test
    fun `Should handle error when creating a debt position`() {
        given(gpdCreateClient.createPosition(any(), any(), any(), any()))
            .willReturn(
                Mono.error {
                    WebClientResponseException.create(
                        400, "Malformed request.", HttpHeaders.EMPTY, byteArrayOf(), null)
                })

        StepVerifier.create(
                gpdClient.createDebtPosition(
                    "test-iupd", PaymentPositionModelV3Dto(), true, "test-request-id"))
            .expectErrorMatches {
                it is GpdClientException &&
                    it.toRestException().httpStatusCode == HttpStatus.BAD_REQUEST
            }
            .verify()

        // Verify not handled exception case
        given(gpdCreateClient.createPosition(any(), any(), any(), any()))
            .willReturn(
                Mono.error {
                    RuntimeException("Unexpected error")
                })

        StepVerifier.create(
                gpdClient.createDebtPosition(
                    "test-iupd", PaymentPositionModelV3Dto(), true, "test-request-id"))
            .expectErrorMatches {
                it is GpdClientException &&
                    it.toRestException().httpStatusCode == HttpStatus.INTERNAL_SERVER_ERROR
            }
            .verify()
    }

    @Test
    fun `Should handle error when invalidating a debt position`() {
        given(gpdInvalidateClient.invalidatePosition(any(), any(), any()))
            .willReturn(
                Mono.error {
                    WebClientResponseException.create(
                        400, "Malformed request.", HttpHeaders.EMPTY, byteArrayOf(), null)
                })

        StepVerifier.create(
                gpdClient.invalidateDebtPosition("test-fiscalcode", "test-iupd", "test-request-id"))
            .expectErrorMatches {
                it is GpdClientException &&
                    it.toRestException().httpStatusCode == HttpStatus.BAD_REQUEST
            }
            .verify()

        // Verify not handled exception case
        given(gpdInvalidateClient.invalidatePosition(any(), any(), any()))
            .willReturn(
                Mono.error {
                    RuntimeException("Unexpected error")
                })

        StepVerifier.create(
                gpdClient.invalidateDebtPosition("test-fiscalcode", "test-iupd", "test-request-id"))
            .expectErrorMatches {
                it is GpdClientException &&
                    it.toRestException().httpStatusCode == HttpStatus.INTERNAL_SERVER_ERROR
            }
            .verify()
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
