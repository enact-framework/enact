package io.enact.core.trigger

import io.enact.core.usecase.UseCase

interface TriggerHandler<T : TriggerDefinition> {
    val triggerType: String

    fun extractDefinition(trigger: TriggerProperties): T?

    fun register(
        useCaseName: String,
        definition: T,
        useCase: UseCase<Any, Any>,
    )

    fun activate() {}
}
