package it.pagopa.posgw.debtposition.handler.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import it.pagopa.generated.posgw.gpd.api.DebtPositionActionsApiApi
import it.pagopa.generated.posgw.gpd.api.DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi
import it.pagopa.posgw.debtposition.handler.config.properties.GpdClientConfigProperties
import java.util.concurrent.TimeUnit
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import reactor.netty.http.client.HttpClient

@Configuration
class WebClientConfig {

    @Bean(name = ["gpdInvalidatePositionWebClient"])
    fun gpdInvalidatePositionClient(
        config: GpdClientConfigProperties,
    ): DebtPositionActionsApiApi {
        val httpClient =
            HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.connectionTimeout)
                .doOnConnected { connection ->
                    connection.addHandlerLast(
                        ReadTimeoutHandler(config.readTimeout.toLong(), TimeUnit.MILLISECONDS))
                }
                .resolver { it.ndots(1) }

        val webClient =
            it.pagopa.generated.posgw.gpd.client.ApiClient.buildWebClientBuilder()
                .clientConnector(ReactorClientHttpConnector(httpClient))
                .defaultHeader("Ocp-Apim-Subscription-Key", config.apiKey)
                .baseUrl(config.uri)
                .build()

        val apiClient =
            it.pagopa.generated.posgw.gpd.client.ApiClient(webClient).setBasePath(config.uri)
        return DebtPositionActionsApiApi(apiClient)
    }

    @Bean(name = ["gpdCreatePositionWebClient"])
    fun gpdCreatePositionClient(
        config: GpdClientConfigProperties,
    ): DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi {
        val httpClient =
            HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.connectionTimeout)
                .doOnConnected { connection ->
                    connection.addHandlerLast(
                        ReadTimeoutHandler(config.readTimeout.toLong(), TimeUnit.MILLISECONDS))
                }
                .resolver { it.ndots(1) }

        val webClient =
            it.pagopa.generated.posgw.gpd.client.ApiClient.buildWebClientBuilder()
                .clientConnector(ReactorClientHttpConnector(httpClient))
                .defaultHeader("Ocp-Apim-Subscription-Key", config.apiKey)
                .baseUrl(config.uri)
                .build()

        val apiClient =
            it.pagopa.generated.posgw.gpd.client.ApiClient(webClient).setBasePath(config.uri)
        return DebtPositionsApiInstallmentsAndPaymentOptionsManagerApi(apiClient)
    }
}
