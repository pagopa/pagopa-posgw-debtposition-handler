package it.pagopa.posgw.debtposition.handler.config

import io.netty.channel.ChannelOption
import io.netty.channel.epoll.EpollChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import it.pagopa.generated.posgw.gpd.api.DebtPositionActionsApiApi
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import reactor.netty.http.client.HttpClient

@Configuration
class WebClientConfig {

    @Bean(name = ["gpdInvalidatePositionWebClient"])
    fun gpdInvalidatePositionClient(
        @Value("\${gpdService.uri}") baseUrl: String,
        @Value("\${gpdService.readTimeout}") readTimeout: Int,
        @Value("\${gpdService.connectionTimeout}") connectionTimeout: Int,
        @Value("\${gpdService.tcp.keepAlive.enabled}") tcpKeepAliveEnabled: Boolean,
        @Value("\${gpdService.tcp.keepAlive.idle}") tcpKeepAliveIdle: Int,
        @Value("\${gpdService.tcp.keepAlive.intvl}") tcpKeepAliveIntvl: Int,
        @Value("\${gpdService.tcp.keepAlive.cnt}") tcpKeepAliveCnt: Int
    ): DebtPositionActionsApiApi {
        val httpClient =
            HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeout)
                .option(ChannelOption.SO_KEEPALIVE, tcpKeepAliveEnabled)
                .option(EpollChannelOption.TCP_KEEPIDLE, tcpKeepAliveIdle)
                .option(EpollChannelOption.TCP_KEEPINTVL, tcpKeepAliveIntvl)
                .option(EpollChannelOption.TCP_KEEPCNT, tcpKeepAliveCnt)
                .doOnConnected { connection ->
                    connection.addHandlerLast(
                        ReadTimeoutHandler(readTimeout.toLong(), TimeUnit.MILLISECONDS))
                }
                .resolver { it.ndots(1) }

        val webClient =
            it.pagopa.generated.posgw.gpd.client.ApiClient.buildWebClientBuilder()
                .clientConnector(ReactorClientHttpConnector(httpClient))
                .baseUrl(baseUrl)
                .build()

        val apiClient =
            it.pagopa.generated.posgw.gpd.client.ApiClient(webClient).setBasePath(baseUrl)
        return DebtPositionActionsApiApi(apiClient)
    }

    @Bean(name = ["gpdCreatePositionWebClient"])
    fun gpdCreatePositionClient(
        @Value("\${gpdService.uri}") baseUrl: String,
        @Value("\${gpdService.readTimeout}") readTimeout: Int,
        @Value("\${gpdService.connectionTimeout}") connectionTimeout: Int,
        @Value("\${gpdService.tcp.keepAlive.enabled}") tcpKeepAliveEnabled: Boolean,
        @Value("\${gpdService.tcp.keepAlive.idle}") tcpKeepAliveIdle: Int,
        @Value("\${gpdService.tcp.keepAlive.intvl}") tcpKeepAliveIntvl: Int,
        @Value("\${gpdService.tcp.keepAlive.cnt}") tcpKeepAliveCnt: Int
    ): DebtPositionActionsApiApi {
        val httpClient =
            HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeout)
                .option(ChannelOption.SO_KEEPALIVE, tcpKeepAliveEnabled)
                .option(EpollChannelOption.TCP_KEEPIDLE, tcpKeepAliveIdle)
                .option(EpollChannelOption.TCP_KEEPINTVL, tcpKeepAliveIntvl)
                .option(EpollChannelOption.TCP_KEEPCNT, tcpKeepAliveCnt)
                .doOnConnected { connection ->
                    connection.addHandlerLast(
                        ReadTimeoutHandler(readTimeout.toLong(), TimeUnit.MILLISECONDS))
                }
                .resolver { it.ndots(1) }

        val webClient =
            it.pagopa.generated.posgw.gpd.client.ApiClient.buildWebClientBuilder()
                .clientConnector(ReactorClientHttpConnector(httpClient))
                .baseUrl(baseUrl)
                .build()

        val apiClient =
            it.pagopa.generated.posgw.gpd.client.ApiClient(webClient).setBasePath(baseUrl)
        return DebtPositionActionsApiApi(apiClient)
    }
}
