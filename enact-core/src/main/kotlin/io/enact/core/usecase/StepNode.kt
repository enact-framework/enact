package io.enact.core.usecase

import io.enact.core.step.Step
import io.enact.core.step.StepSettings

/** Where one parameter of a step takes its value. */
sealed interface Binding {
    /** The use case's own input. */
    data object Input : Binding

    /** The output of another node of the same use case, which must be declared before it. */
    data class Output(
        val node: String,
    ) : Binding
}

/**
 * One step of a use case, and where each of its inputs comes from.
 *
 * [bindings] may be left out: a step taking a single parameter then reads the output of the node declared
 * before it, or the use case's input when it is the first. A step taking several parameters binds each of
 * them by name, so that declaration order never decides which value goes where.
 */
data class StepNode(
    /** Identifies the node within the use case. Defaults to the step's name, so a step used twice needs one. */
    val id: String,
    val step: Step<*, *>,
    /** Source of each parameter, by parameter name. */
    val bindings: Map<String, Binding> = emptyMap(),
    /** Overrides the settings declared on the step itself. */
    val settings: StepSettings? = step.settings,
)
