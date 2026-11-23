package it.pagopa.posgw.debtposition.handler.config

import io.netty.channel.ChannelOption
import it.pagopa.generated.posgw.gpd.api.DebtPositionActionsApiApi
import it.pagopa.generated.posgw.gpd.api.DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi
import it.pagopa.generated.posgw.gpd.client.ApiClient
import it.pagopa.posgw.debtposition.handler.config.properties.GpdClientConfigProperties
import java.time.Duration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient

@Configuration
class WebClientConfig {

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
