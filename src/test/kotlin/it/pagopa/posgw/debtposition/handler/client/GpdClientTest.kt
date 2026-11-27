package it.pagopa.posgw.debtposition.handler.client

import com.sun.net.httpserver.HttpServer
import io.netty.handler.timeout.ReadTimeoutException
import it.pagopa.generated.posgw.gpd.api.DebtPositionActionsApiApi
import it.pagopa.generated.posgw.gpd.api.DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi
import it.pagopa.generated.posgw.gpd.model.PaymentPositionModelV3Dto
import it.pagopa.posgw.debtposition.handler.client.GpdClient.Companion.Operation
import it.pagopa.posgw.debtposition.handler.exception.GpdClientException
import java.net.InetSocketAddress
import java.net.URI
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
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import org.springframework.web.reactive.function.client.WebClientRequestException
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

    @ParameterizedTest
    @ValueSource(ints = [400, 404, 409, 422])
    fun `Should map 4xx to conflict when creating a debt position`(status: Int) {
        createFailsWith(webClientError(status))
            .expectGpdError(
                HttpStatus.CONFLICT,
                "Client error while invoking GPD for operation ${Operation.CREATE_DEBT_POSITION} with status code from GPD ${HttpStatus.valueOf(status)}")
    }

    @ParameterizedTest
    @ValueSource(ints = [401, 403])
    fun `Should map 401 and 403 to internal server error when creating a debt position`(
        status: Int
    ) {
        createFailsWith(webClientError(status))
            .expectGpdError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error communicating with GPD for operation ${Operation.CREATE_DEBT_POSITION} with status code from GPD ${HttpStatus.valueOf(status)}")
    }

    @ParameterizedTest
    @ValueSource(ints = [500, 502, 503, 504])
    fun `Should map 5xx to bad gateway when creating a debt position`(status: Int) {
        createFailsWith(webClientError(status))
            .expectGpdError(
                HttpStatus.BAD_GATEWAY,
                "Unexpected error while invoking GPD for operation ${Operation.CREATE_DEBT_POSITION} with status code from GPD ${HttpStatus.valueOf(status)}")
    }

    @Test
    fun `Should map non HTTP errors to bad gateway when creating a debt position`() {
        createFailsWith(RuntimeException("Unexpected error"))
            .expectGpdError(
                HttpStatus.BAD_GATEWAY,
                "Unexpected error while invoking GPD for operation ${Operation.CREATE_DEBT_POSITION}")
    }

    @Test
    fun `Should map request errors without response to bad gateway when creating a debt position`() {
        createFailsWith(
                WebClientRequestException(
                    ReadTimeoutException.INSTANCE,
                    HttpMethod.POST,
                    URI.create("http://localhost:3002"),
                    HttpHeaders.EMPTY))
            .expectGpdError(
                HttpStatus.BAD_GATEWAY,
                "Unexpected error while invoking GPD for operation ${Operation.CREATE_DEBT_POSITION}")
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 409, 422])
    fun `Should map 4xx to conflict when invalidating a debt position`(status: Int) {
        invalidateFailsWith(webClientError(status))
            .expectGpdError(
                HttpStatus.CONFLICT,
                "Client error while invoking GPD for operation ${Operation.INVALIDATE_DEBT_POSITION} with status code from GPD ${HttpStatus.valueOf(status)}")
    }

    @ParameterizedTest
    @ValueSource(ints = [404])
    fun `Should map 404 to not found error when invalidating a debt position`(status: Int) {
        invalidateFailsWith(webClientError(status))
            .expectGpdError(
                HttpStatus.NOT_FOUND,
                "Debt position not found in GPD for operation ${Operation.INVALIDATE_DEBT_POSITION} with status code from GPD ${HttpStatus.valueOf(status)}")
    }

    @ParameterizedTest
    @ValueSource(ints = [401, 403])
    fun `Should map 401 and 403 to internal server error when invalidating a debt position`(
        status: Int
    ) {
        invalidateFailsWith(webClientError(status))
            .expectGpdError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error communicating with GPD for operation ${Operation.INVALIDATE_DEBT_POSITION} with status code from GPD ${HttpStatus.valueOf(status)}")
    }

    @ParameterizedTest
    @ValueSource(ints = [500, 502, 503, 504])
    fun `Should map 5xx to bad gateway when invalidating a debt position`(status: Int) {
        invalidateFailsWith(webClientError(status))
            .expectGpdError(
                HttpStatus.BAD_GATEWAY,
                "Unexpected error while invoking GPD for operation ${Operation.INVALIDATE_DEBT_POSITION} with status code from GPD ${HttpStatus.valueOf(status)}")
    }

    @Test
    fun `Should map non HTTP errors to bad gateway when invalidating a debt position`() {
        invalidateFailsWith(RuntimeException("Unexpected error"))
            .expectGpdError(
                HttpStatus.BAD_GATEWAY,
                "Unexpected error while invoking GPD for operation ${Operation.INVALIDATE_DEBT_POSITION}")
    }

    @Test
    fun `Should map request errors without response to bad gateway when invalidating a debt position`() {
        invalidateFailsWith(
                WebClientRequestException(
                    ReadTimeoutException.INSTANCE,
                    HttpMethod.POST,
                    URI.create("http://localhost:3002"),
                    HttpHeaders.EMPTY))
            .expectGpdError(
                HttpStatus.BAD_GATEWAY,
                "Unexpected error while invoking GPD for operation ${Operation.INVALIDATE_DEBT_POSITION}")
    }

    @Test
    fun `Should map a refused connection to bad gateway`() {
        // no server is listening on the configured GPD port
        val realClient = GpdClient(realGpdInvalidateClient, realGpdCreateClient)

        StepVerifier.create(
                realClient.invalidateDebtPosition("test-fiscalcode", "test-iupd", "request-id"))
            .expectErrorMatches {
                it is GpdClientException &&
                    it.toRestException().httpStatusCode == HttpStatus.BAD_GATEWAY
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
