package io.enact.core.usecase

import io.enact.core.step.MethodAdapter
import io.enact.core.step.Step
import org.springframework.core.GenericTypeResolver
import kotlin.reflect.KClass

fun <Input, Output> UseCase<Input, Output>.validateFirstStepInput() {
    val (useCaseInput) = resolveTypes(this, UseCase::class)
    val firstStep = steps.first()
    val (firstStepInput) = resolveTypes(firstStep::class, Step::class)

    require(useCaseInput == firstStepInput) {
        """
        Use-case "$name" input type mismatch for the first step "${firstStep.name}".
        Expected: ${useCaseInput.name}, actual: ${firstStepInput.name}.
        """.trimIndent()
    }
}

fun <Input, Output> UseCase<Input, Output>.validateLastStepOutput() {
    val (_, useCaseOutput) = resolveTypes(this, UseCase::class)
    val lastStep = steps.last()
    val (_, lastStepOutput) = resolveTypes(lastStep, Step::class)

    require(useCaseOutput == lastStepOutput) {
        """
        Use-case "$name" output type mismatch for the last step "${lastStep.name}".
        Expected: ${useCaseOutput.name}, actual: ${lastStepOutput.name}.
        """.trimIndent()
    }
}

fun <Input, Output> UseCase<Input, Output>.validateStepChain() {
    steps.zipWithNext { current, next ->
        val (_, currentOutput) = resolveTypes(current, Step::class)
        val (nextInput) = resolveTypes(next, Step::class)

        require(currentOutput == nextInput) {
            """
            Step "${current.name}" output type mismatch with followup step "${next.name}" input.
            Expected: ${currentOutput.name}, actual: ${nextInput.name}.
            """.trimIndent()
        }
    }
}

private fun resolveTypes(
    instance: Any,
    genericType: KClass<*>,
): InOutTypes {
    if (instance is MethodAdapter) {
        return InOutTypes(instance.inputClass, instance.outputClass)
    }

    val typeArgs =
        GenericTypeResolver.resolveTypeArguments(instance.javaClass, genericType.java)
            ?: error("Could not resolve generic types for ${instance.javaClass.simpleName}")

    return InOutTypes(typeArgs[0], typeArgs[1])
}

private data class InOutTypes(
    val inputType: Class<*>,
    val outputType: Class<*>,
)
