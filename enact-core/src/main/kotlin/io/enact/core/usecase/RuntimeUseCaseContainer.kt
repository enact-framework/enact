package io.enact.core.usecase

import io.enact.core.observation.DefaultUseCaseObservationConvention
import io.enact.core.observation.EnactObservationDocumentation
import io.enact.core.observation.EnactObservations
import io.enact.core.observation.UseCaseObservationContext
import io.enact.core.step.Step
import io.enact.core.step.StepSettings
import org.springframework.cache.CacheManager
import java.util.function.Supplier

class RuntimeUseCaseContainer<Input, Output>(
    override val name: String,
    override val description: String,
    override val steps: List<Step<*, *>>,
    /** Settings applied to each step in this use case, by position. Defaults to the steps' own settings. */
    stepSettings: List<StepSettings?> = steps.map { it.settings },
    cacheManager: CacheManager? = null,
    /** Group of the use case, recorded as a tag. `default` when not set. */
    group: String? = null,
    private val observations: EnactObservations = EnactObservations.NONE,
) : UseCase<Input, Output> {
    override val inputType: Class<*>
    override val outputType: Class<*>

    private val groupName = group ?: DEFAULT_GROUP
    private val invokers: List<StepInvoker>

    init {
        require(steps.isNotEmpty()) {
            "Must specify at least one step."
        }
        require(stepSettings.size == steps.size) {
            "Expected ${steps.size} step settings, got ${stepSettings.size}."
        }

        validateStepChain()

        inputType = resolveTypes(steps.first()).inputType.toClass()
        outputType = resolveTypes(steps.last()).outputType.toClass()

        @Suppress("UNCHECKED_CAST")
        invokers =
            steps.zip(stepSettings) { step, settings ->
                StepInvoker(step as Step<Any?, Any?>, settings, cacheManager, name, groupName, observations)
            }
    }

    override fun execute(input: Input): Output {
        val observation =
            EnactObservationDocumentation.USE_CASE.observation(
                observations.useCaseConvention,
                DefaultUseCaseObservationConvention,
                { UseCaseObservationContext(name, groupName) },
                observations.registry,
            )

        @Suppress("UNCHECKED_CAST")
        return observation.observe(Supplier { runSteps(input) }) as Output
    }

    /** Steps observe themselves within the scope opened above, so their spans nest under the use case's. */
    private fun runSteps(input: Input): Any? {
        var current: Any? = input
        for (invoker in invokers) {
            current = invoker.invoke(current)
        }
        return current
    }

    private companion object {
        const val DEFAULT_GROUP = "default"
    }
}
