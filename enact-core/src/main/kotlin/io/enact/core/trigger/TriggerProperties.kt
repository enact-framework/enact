package io.enact.core.trigger

/**
 * Trigger configuration of a use case: exactly one entry, keyed by trigger type.
 *
 * The trigger types Enact ships are declared here so that editors complete and validate them. Nothing reads this
 * class at runtime, where a trigger is bound to the [definitionType][TriggerHandler.definitionType] of the handler
 * claiming its type. A trigger type from another starter works without an entry here, but an editor cannot
 * complete it.
 */
data class TriggerProperties(
    /** REST HTTP endpoint trigger, handled by `enact-starter-web`. */
    val rest: RestTriggerDefinition? = null,
)
