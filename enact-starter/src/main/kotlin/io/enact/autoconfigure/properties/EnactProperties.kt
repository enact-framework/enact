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
) {
    data class UseCaseDefinition(
        /** Unique name of the use case, used as the Spring bean name. */
        val name: String,
        /** Description of the use case. */
        val description: String?,
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
