package it.pagopa.posgw.debtposition.handler.config.properties

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "gpd")
data class GpdClientConfigProperties(
    @NotBlank val uri: String,
    @Positive val readTimeout: Int,
    @Positive val connectionTimeout: Int,
    @NotBlank val apiKey: String
)
