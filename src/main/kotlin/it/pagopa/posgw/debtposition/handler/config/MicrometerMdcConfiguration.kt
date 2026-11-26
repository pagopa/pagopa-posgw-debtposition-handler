package it.pagopa.posgw.debtposition.handler.config

import io.micrometer.context.ContextRegistry
import it.pagopa.posgw.common.utils.LogTracingUtils.Companion.AttributeKeys
import jakarta.annotation.PostConstruct
import org.slf4j.MDC
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Hooks

@Configuration
class MicrometerMdcConfiguration {
    private val contextBound =
        setOf(
            AttributeKeys.EVENT_ACTION.key,
            AttributeKeys.CTX_REQUEST_ID.key,
        )

    @PostConstruct
    fun initMdcMicrometerRegistry() {
        Hooks.enableAutomaticContextPropagation()
        AttributeKeys.entries
            .filter { contextBound.contains(it.key) }
            .forEach { entry ->
                ContextRegistry.getInstance()
                    .registerThreadLocalAccessor(
                        entry.key,
                        { MDC.get(entry.key) },
                        { value -> MDC.put(entry.key, value) },
                        { MDC.remove(entry.key) })
            }
    }
}
