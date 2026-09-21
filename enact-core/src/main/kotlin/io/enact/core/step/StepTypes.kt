package io.enact.core.step

import org.springframework.core.ResolvableType

/**
 * Inputs of the step, in declaration order.
 *
 * A step whose only input is `Unit` takes no argument, so there is nothing to bind to it. Only a step written
 * as an annotated method can take several.
 */
val Step<*, *>.parameters: List<StepParameter>
    get() {
        if (this is MethodAdapter) return parameters

        val input = stepType().getGeneric(0)
        return if (input.toClass() == Unit::class.java) emptyList() else listOf(StepParameter("input", input))
    }

/** Type the step produces, generics included. */
val Step<*, *>.outputType: ResolvableType
    get() = if (this is MethodAdapter) outputType else stepType().getGeneric(1)

private fun Step<*, *>.stepType(): ResolvableType {
    val type = ResolvableType.forClass(javaClass).`as`(Step::class.java)
    check(type.getGeneric(0).resolve() != null && type.getGeneric(1).resolve() != null) {
        "Could not resolve generic types for ${javaClass.simpleName}"
    }
    return type
}
