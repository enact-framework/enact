package io.enact.autoconfigure.definition

import com.fasterxml.jackson.annotation.JsonProperty
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
    /** Step whose output the use case returns; inferred when not set as the only one nothing reads. */
    val output: String? = null,
    /**
     * Whether steps that do not read each other run at once. Off by default: a step moved off the caller's
     * thread leaves any surrounding transaction, `SecurityContext` and MDC behind.
     */
    val concurrent: Boolean = false,
    /** Steps in declaration order; each may only bind to one declared before it. */
    val steps: List<StepReference> = emptyList(),
)

data class StepReference(
    /** Name of the step bean to execute. */
    val step: String,
    /** Identifies the step within the use case; defaults to [step], so a step used twice needs one. */
    val id: String? = null,
    /**
     * Where the step's inputs come from: `$input` for the use case's own input, `$<step id>` for another
     * step's output. Either one reference for a step taking a single parameter, or one per parameter by
     * name. Left out, a single parameter reads the step declared before it.
     */
    @param:JsonProperty("in")
    val inputs: JsonNode? = null,
    /**
     * SpEL deciding whether the step runs, e.g. `"#coupon != null"`. Each of the step's inputs is readable by
     * parameter name, and `#input` names the only one of a step taking a single parameter.
     */
    @param:JsonProperty("when")
    val condition: String? = null,
    /**
     * What the step yields when `when` does not hold: `$<parameter>` to pass one of its own inputs through,
     * or a value of its output type. Inferred when not set, as the only input that can stand in for the
     * output. Needed only when another step reads this one.
     */
    @param:JsonProperty("else")
    val fallback: JsonNode? = null,
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
