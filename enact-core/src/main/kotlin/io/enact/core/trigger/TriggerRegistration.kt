package io.enact.core.trigger

import io.enact.core.usecase.UseCase

/** A use case to expose, with the trigger configuration declared for it. */
data class TriggerRegistration<D : Any>(
    /** Name of the use case, which is also its bean name. */
    val useCaseName: String,
    /** The use case to execute. */
    val useCase: UseCase<Any, Any>,
    /** Trigger definition bound from `trigger.<triggerType>`. */
    val definition: D,
    /**
     * Filter bean names declared by the use case's group, outermost first. Each trigger type resolves them to
     * its own filter type, e.g. `HandlerFilterFunction` for REST.
     */
    val groupFilters: List<String> = emptyList(),
)
