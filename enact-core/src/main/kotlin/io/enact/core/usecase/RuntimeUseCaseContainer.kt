package io.enact.core.usecase

import io.enact.core.step.Step
import io.enact.core.step.StepSettings
import org.springframework.cache.CacheManager

class RuntimeUseCaseContainer<Input, Output>(
    override val name: String,
    override val description: String,
    override val steps: List<Step<*, *>>,
    /** Settings applied to each step in this use case, by position. Defaults to the steps' own settings. */
    stepSettings: List<StepSettings?> = steps.map { it.settings },
    cacheManager: CacheManager? = null,
) : UseCase<Input, Output> {
    override val inputType: Class<*>
    override val outputType: Class<*>

    private val invokers: List<StepInvoker>

    init {
        require(steps.isNotEmpty()) {
            "Must specify at least one step."
        }
        require(stepSettings.size == steps.size) {
            "Expected ${steps.size} step settings, got ${stepSettings.size}."
        }

        validateStepChain()

        inputType = resolveTypes(steps.first(), Step::class).inputType
        outputType = resolveTypes(steps.last(), Step::class).outputType

        @Suppress("UNCHECKED_CAST")
        invokers =
            steps.zip(stepSettings) { step, settings ->
                StepInvoker(step as Step<Any?, Any?>, settings, cacheManager)
            }
    }

    override fun execute(input: Input): Output {
        var current: Any? = input
        for (invoker in invokers) {
            current = invoker.invoke(current)
        }
        @Suppress("UNCHECKED_CAST")
        return current as Output
    }
}
