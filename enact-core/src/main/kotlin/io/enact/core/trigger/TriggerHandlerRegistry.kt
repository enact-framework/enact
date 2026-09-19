package io.enact.core.trigger

class TriggerHandlerRegistry(
    handlers: List<TriggerHandler<*>>,
) {
    private val handlersByType = handlers.associateBy { it.triggerType.lowercase() }

    init {
        require(handlers.size == handlersByType.size) {
            "Several trigger handlers declare the same type: ${handlers.map { it.triggerType }}"
        }
    }

    fun getHandler(type: String): TriggerHandler<*> =
        handlersByType[type.lowercase()] ?: error("No trigger handler for '$type'. Available: $types")

    val types: Set<String> get() = handlersByType.keys
}
