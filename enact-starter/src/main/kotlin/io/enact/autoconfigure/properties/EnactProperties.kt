package io.enact.autoconfigure.properties

import io.enact.core.step.StepSettings
import io.enact.core.trigger.TriggerProperties
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for Enact.
 */
@ConfigurationProperties(prefix = "enact")
data class EnactProperties(
    /** Whether Enact auto-configuration is enabled. */
    val enabled: Boolean = true,
    /** List of use case definitions. */
    val useCases: List<UseCaseDefinition> = emptyList(),
    /** Groups of use cases sharing REST filters, by name. Group `default` applies to use cases without a group. */
    val groups: Map<String, GroupDefinition> = emptyMap(),
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

    data class GroupDefinition(
        /** Names of the filter beans wrapping the group's use cases, outermost first; the trigger type defines their kind. */
        val filters: List<String> = emptyList(),
        /** Observability for the group's use cases; unset inherits `enact.observability`. */
        val observability: ObservabilitySpec? = null,
    )

    data class UseCaseDefinition(
        /** Unique name of the use case, used as the Spring bean name. */
        val name: String,
        /** Description of the use case. */
        val description: String?,
        /** Group the use case belongs to; `default` when not set. */
        val group: String? = null,
        /**
         * Optional trigger exposing the use case (e.g. as an HTTP endpoint), as a single entry keyed by trigger
         * type, e.g. `rest`. Its value is bound by the `TriggerHandler` of that type, which may be one this class
         * does not name. Without a trigger, the use case can only be injected.
         */
        val trigger: TriggerProperties? = null,
        /** Observability for this use case; unset inherits its group, then `enact.observability`. */
        val observability: ObservabilitySpec? = null,
        /** Ordered list of steps to execute. */
        val steps: List<StepReference>,
    ) {
        data class StepReference(
            /** Name of the step bean to execute. */
            val step: String,
            /** Optional step settings (retry, cache). Values set here override those declared on the step. */
            val settings: StepSettings? = null,
        )
    }
}
