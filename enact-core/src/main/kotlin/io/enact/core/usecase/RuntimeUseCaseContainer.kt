package io.enact.core.usecase

import io.enact.core.step.Step

class RuntimeUseCaseContainer<Input, Output>(
    override val name: String,
    override val description: String,
    override val steps: List<Step<*, *>>,
) : UseCase<Input, Output> {
    init {
        require(steps.isNotEmpty()) {
            "Must specify at least one step."
        }

        validateStepChain()
    }

    override fun execute(input: Input): Output {
        var current: Any? = input
        for (step in steps) {
            current = (step as Step<Any?, Any?>).execute(current)
        }
        return current as Output
    }
}
