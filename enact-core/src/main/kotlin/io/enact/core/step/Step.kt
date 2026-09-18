package io.enact.core.step

interface Step<Input, Output> {
    val name: String
    val settings: StepSettings?
        get() = null

    fun execute(input: Input): Output

    interface InOut<Input, Output> : Step<Input, Output>

    interface In<Input> : Step<Input, Unit>

    interface Out<Output> : Step<Unit, Output>
}
