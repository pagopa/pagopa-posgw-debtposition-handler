package it.pagopa.posgw.debtposition.handler

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(
    it.pagopa.posgw.debtposition.handler.config.properties.GpdClientConfigProperties::class)
class PagopaPosgwDebtpositionHandlerApplication

fun main(args: Array<String>) {
    runApplication<PagopaPosgwDebtpositionHandlerApplication>(*args)
}
