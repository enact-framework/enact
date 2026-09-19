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
) {
    data class GroupDefinition(
        /** Names of `HandlerFilterFunction` beans wrapping the REST routes of the group's use cases, outermost first. */
        val filters: List<String> = emptyList(),
    )

    data class UseCaseDefinition(
        /** Unique name of the use case, used as the Spring bean name. */
        val name: String,
        /** Description of the use case. */
        val description: String?,
        /** Group the use case belongs to; `default` when not set. */
        val group: String? = null,
        /** Optional trigger exposing the use case (e.g. as an HTTP endpoint). Without it, the use case can only be injected. */
        val trigger: TriggerProperties? = null,
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
