package io.enact.autoconfigure.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for Enact.
 *
 * Use cases and groups are not here: they live in definition files, read by
 * [io.enact.autoconfigure.definition.DefinitionReader].
 */
@ConfigurationProperties(prefix = "enact")
data class EnactProperties(
    /** Whether Enact auto-configuration is enabled. */
    val enabled: Boolean = true,
    /**
     * Where the definition files are, as a file, a directory or an Ant pattern, for example
     * `classpath:enact/orders.yaml`. A location may start with `optional:` when it may be missing.
     */
    val definitions: List<String> = listOf("optional:classpath:enact/"),
    /** Metrics and traces recorded for every use case, unless its group or the use case itself opts out. */
    val observability: ObservabilitySpec = ObservabilitySpec(),
) {
    data class ObservabilitySpec(
        /**
         * Whether use case and step observations are recorded. Unset inherits: a use case falls back to its
         * group, a group to `enact.observability.enabled`, which defaults to `true`.
         */
        val enabled: Boolean? = null,
    )
}
