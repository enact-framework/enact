package io.enact.autoconfigure.definition

import io.enact.core.step.StepSettings
import tools.jackson.databind.JsonNode

/**
 * Everything the definition files declare, by name.
 *
 * Read from the files listed under `enact.definitions` by [DefinitionReader]. Unlike the rest of `enact.*`,
 * these are not configuration properties: they are parsed with Jackson, which keeps map keys as they are
 * written. A use case name, a `bind` entry and, later, a binding key all name something case-sensitively,
 * which a configuration property name would lower-case.
 */
data class Definitions(
    val useCases: Map<String, UseCaseDefinition> = emptyMap(),
    val groups: Map<String, GroupDefinition> = emptyMap(),
    /** File each use case and group was read from, by name, so that an error can name it. */
    val sources: Map<String, String> = emptyMap(),
) {
    /** Describes where [name] was declared, for an error message. */
    fun sourceOf(name: String): String = sources[name]?.let { " ($it)" } ?: ""

    companion object {
        const val DEFAULT_GROUP = "default"
    }
}

data class GroupDefinition(
    /** Names of the filter beans wrapping the group's use cases, outermost first; the trigger type defines their kind. */
    val filters: List<String> = emptyList(),
    /** Observability for the group's use cases; unset inherits `enact.observability`. */
    val observability: ObservabilitySpec? = null,
)

data class UseCaseDefinition(
    /** Description of the use case. */
    val description: String? = null,
    /** Group the use case belongs to; `default` when not set. */
    val group: String? = null,
    /**
     * Optional trigger exposing the use case (e.g. as an HTTP endpoint), as a single entry keyed by trigger
     * type, e.g. `rest`. Its value stays a tree until the `TriggerHandler` claiming that type reads it into
     * its own definition. Without a trigger, the use case can only be injected.
     */
    val trigger: Map<String, JsonNode> = emptyMap(),
    /** Observability for this use case; unset inherits its group, then `enact.observability`. */
    val observability: ObservabilitySpec? = null,
    /** Ordered list of steps to execute. */
    val steps: List<StepReference> = emptyList(),
)

data class StepReference(
    /** Name of the step bean to execute. */
    val step: String,
    /** Optional step settings (retry, cache). Values set here override those declared on the step. */
    val settings: StepSettings? = null,
)

data class ObservabilitySpec(
    /**
     * Whether use case and step observations are recorded. Unset inherits: a use case falls back to its
     * group, a group to `enact.observability.enabled`, which defaults to `true`.
     */
    val enabled: Boolean? = null,
)
