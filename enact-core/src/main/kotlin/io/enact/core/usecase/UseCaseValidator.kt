package io.enact.core.usecase

import io.enact.core.step.MethodAdapter
import io.enact.core.step.Step
import org.springframework.core.ResolvableType

/**
 * Checks that every step of the chain can be fed the value the previous one produces.
 *
 * The check is assignability, not equality, so a step declaring a wider input than what reaches it is fine:
 * `ArrayList<Order>` feeds a step taking `List<Order>`, and an `int` feeds a step taking `Integer`. Generics
 * take part in the comparison, so `List<Order>` does not feed a step taking `List<String>`.
 */
fun <Input, Output> UseCase<Input, Output>.validateStepChain() {
    steps.zipWithNext { current, next ->
        val produced = resolveTypes(current).outputType
        val expected = resolveTypes(next).inputType

        require(expected.isAssignableFrom(produced)) {
            """
            Step "${current.name}" output type mismatch with followup step "${next.name}" input.
            Expected: $expected, actual: $produced.
            """.trimIndent()
        }
    }
}

internal fun resolveTypes(instance: Any): InOutTypes {
    if (instance is MethodAdapter) {
        return InOutTypes(instance.inputType, instance.outputType)
    }

    val stepType = ResolvableType.forClass(instance.javaClass).`as`(Step::class.java)
    val types = InOutTypes(stepType.getGeneric(0), stepType.getGeneric(1))

    check(types.inputType.resolve() != null && types.outputType.resolve() != null) {
        "Could not resolve generic types for ${instance.javaClass.simpleName}"
    }
    return types
}

internal data class InOutTypes(
    val inputType: ResolvableType,
    val outputType: ResolvableType,
)
