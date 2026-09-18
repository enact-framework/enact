package io.enact.core.trigger

/** Trigger configuration for a use case. */
data class TriggerProperties(
    /** REST HTTP endpoint trigger. */
    val rest: RestTriggerDefinition? = null,
)
