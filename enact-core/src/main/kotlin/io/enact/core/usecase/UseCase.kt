package io.enact.core.usecase

import io.enact.core.step.Step

sealed interface UseCase<Input, Output> {
    val name: String
    val description: String
    val steps: List<Step<*, *>>

    fun execute(input: Input): Output

    interface InOutUseCase<Input, Output> : UseCase<Input, Output>

    interface InUseCase<Input> : UseCase<Input, Unit>

    interface OutUseCase<Output> : UseCase<Unit, Output>
}
