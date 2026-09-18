package io.enact.autoconfigure

import io.enact.autoconfigure.properties.EnactProperties
import io.enact.core.trigger.TriggerDefinition
import io.enact.core.trigger.TriggerHandler
import io.enact.core.trigger.TriggerHandlerRegistry
import io.enact.core.usecase.UseCase
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.context.ApplicationContext

class TriggerActivator(
    private val properties: EnactProperties,
    private val registry: TriggerHandlerRegistry,
    private val applicationContext: ApplicationContext,
) : SmartInitializingSingleton {
    @Suppress("UNCHECKED_CAST")
    override fun afterSingletonsInstantiated() {
        val activeHandlers = mutableSetOf<TriggerHandler<*>>()

        properties.useCases.forEach { definition ->
            val trigger = definition.trigger ?: return@forEach
            val useCase = applicationContext.getBean(definition.name) as UseCase<Any, Any>
            var matched = false

            for (handler in registry.allHandlers()) {
                val triggerDef = handler.extractDefinition(trigger) ?: continue
                val typedHandler = handler as TriggerHandler<TriggerDefinition>
                typedHandler.register(definition.name, triggerDef, useCase)
                activeHandlers.add(handler)
                matched = true
                break
            }

            require(matched) {
                "Use case '${definition.name}' has no matching trigger. " +
                    "Available trigger types: ${registry.allHandlers().map { it.triggerType }}"
            }
        }

        activeHandlers.forEach { it.activate() }
    }
}
