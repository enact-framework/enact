package io.enact.core.trigger

class TriggerHandlerRegistry(
    handlers: List<TriggerHandler<*>>,
) {
    private val handlersByType = handlers.associateBy { it.triggerType }

    fun getHandler(type: String): TriggerHandler<*> =
        handlersByType[type] ?: error("No trigger handler for '$type'. Available: ${handlersByType.keys}")

    fun allHandlers(): Collection<TriggerHandler<*>> = handlersByType.values
}
