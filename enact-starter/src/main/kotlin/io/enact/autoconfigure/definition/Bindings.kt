package io.enact.autoconfigure.definition

import io.enact.core.step.Step
import io.enact.core.step.parameters
import io.enact.core.usecase.Binding

/**
 * Reads the `in` block of a step reference into a binding per parameter name.
 *
 * A reference starts with `$`: `$input` is the use case's own input, anything else names another step of the
 * same use case. Without `$` the value would read as a literal, which is what keeps `else` unambiguous.
 */
internal fun StepReference.bindings(
    useCase: String,
    step: Step<*, *>,
): Map<String, Binding> {
    val where = "Step '${id ?: this.step}' of use case '$useCase'"
    val declared = inputs ?: return emptyMap()

    if (declared.isString) {
        val parameter =
            requireNotNull(step.parameters.singleOrNull()) {
                "$where takes ${step.parameters.size} parameters, so 'in' must bind each of them by name: " +
                    "${step.parameters.map { it.name }}."
            }
        return mapOf(parameter.name to binding(where, parameter.name, declared.stringValue()))
    }

    require(declared.isObject) {
        "$where declares 'in' as ${declared.nodeType}. It is either one reference, or one per parameter by name."
    }
    return declared.properties().associate { (name, value) ->
        require(value.isString) { "$where binds '$name' to ${value.nodeType}, but a binding is a reference." }
        name to binding(where, name, value.stringValue())
    }
}

private fun binding(
    where: String,
    parameter: String,
    value: String,
): Binding =
    when {
        value == INPUT -> Binding.Input
        value.startsWith(REFERENCE) -> Binding.Output(value.removePrefix(REFERENCE))
        else ->
            throw IllegalArgumentException(
                "$where binds '$parameter' to '$value', which is not a reference. Name another step with " +
                    "'${REFERENCE}stepId', or the use case's input with '$INPUT'.",
            )
    }

private const val REFERENCE = "$"
private const val INPUT = "\$input"
