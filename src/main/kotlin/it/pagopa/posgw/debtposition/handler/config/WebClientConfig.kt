package it.pagopa.posgw.debtposition.handler.config

import io.netty.channel.ChannelOption
import it.pagopa.generated.posgw.gpd.api.DebtPositionActionsApiApi
import it.pagopa.generated.posgw.gpd.api.DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi
import it.pagopa.generated.posgw.gpd.client.ApiClient
import it.pagopa.generated.posgw.gpd.model.DebtorModelDto
import it.pagopa.generated.posgw.gpd.model.InstallmentMetadataModelDto
import it.pagopa.generated.posgw.gpd.model.InstallmentModelDto
import it.pagopa.generated.posgw.gpd.model.PaymentOptionModelV3Dto
import it.pagopa.generated.posgw.gpd.model.PaymentPositionModelV3Dto
import it.pagopa.generated.posgw.gpd.model.ProblemJsonDto
import it.pagopa.generated.posgw.gpd.model.StampDto
import it.pagopa.generated.posgw.gpd.model.TransferMetadataModelDto
import it.pagopa.generated.posgw.gpd.model.TransferModelDto
import it.pagopa.posgw.debtposition.handler.config.properties.GpdClientConfigProperties
import java.time.Duration
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportRuntimeHints
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient

@Configuration
@RegisterReflectionForBinding(
    PaymentPositionModelV3Dto::class,
    PaymentOptionModelV3Dto::class,
    InstallmentModelDto::class,
    InstallmentMetadataModelDto::class,
    TransferModelDto::class,
    TransferMetadataModelDto::class,
    DebtorModelDto::class,
    StampDto::class,
    ProblemJsonDto::class,
)
@ImportRuntimeHints(WebClientConfig.GpdModelsHints::class)
class WebClientConfig {

    class GpdModelsHints : RuntimeHintsRegistrar {
        override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
            listOf(
                    PaymentPositionModelV3Dto::class,
                    PaymentOptionModelV3Dto::class,
                    InstallmentModelDto::class,
                    InstallmentMetadataModelDto::class,
                    TransferModelDto::class,
                    TransferMetadataModelDto::class,
                    DebtorModelDto::class,
                    StampDto::class,
                    ProblemJsonDto::class,
                )
                .forEach {
                    hints.reflection().registerType(it.java, *MemberCategory.entries.toTypedArray())
                }
        }
    }

    @Bean(name = ["gpdInvalidatePositionWebClient"])
    fun gpdInvalidatePositionClient(
        apiClient: ApiClient,
    ) = DebtPositionActionsApiApi(apiClient)

    @Bean(name = ["gpdCreatePositionWebClient"])
    fun gpdCreatePositionClient(
        apiClient: ApiClient,
    ) = DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi(apiClient)

    @Bean
    fun gpdApiClient(builder: WebClient.Builder, config: GpdClientConfigProperties): ApiClient {
        val httpClient =
            HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.connectionTimeout)
                .responseTimeout(Duration.ofMillis(config.readTimeout.toLong()))
                .resolver { it.ndots(1) }

        val webClient =
            builder
                .clientConnector(ReactorClientHttpConnector(httpClient))
                .defaultHeader("Ocp-Apim-Subscription-Key", config.apiKey)
                .build()

        return ApiClient(webClient).setBasePath(config.uri)
    }
}
