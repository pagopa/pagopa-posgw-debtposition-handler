package it.pagopa.posgw.debtposition.handler.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gpd")
data class GpdClientConfigProperties(
    val uri: String,
    val readTimeout: Int,
    val connectionTimeout: Int,
    val apiKey: String
)
