package io.enact.autoconfigure

import io.enact.autoconfigure.properties.EnactProperties
import io.enact.core.trigger.TriggerHandler
import io.enact.core.trigger.TriggerHandlerRegistry
import io.enact.core.trigger.TriggerProperties
import io.enact.core.trigger.TriggerRegistration
import io.enact.core.usecase.UseCase
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment

/**
 * Hands every use case declaring a trigger to the handler of that trigger type, then starts the handlers.
 *
 * Trigger types are read from the configuration itself rather than from
 * [TriggerProperties][io.enact.core.trigger.TriggerProperties], which only names the types Enact ships so that
 * editors can complete them. The definition is then bound from `enact.use-cases[i].trigger.<type>` to the
 * handler's [TriggerHandler.definitionType], so a new trigger type needs no change here.
 */
class TriggerActivator(
    private val properties: EnactProperties,
    private val registry: TriggerHandlerRegistry,
    private val applicationContext: ApplicationContext,
    private val environment: Environment,
) : SmartInitializingSingleton,
    DisposableBean {
    private val startedHandlers = mutableListOf<TriggerHandler<*>>()

    override fun afterSingletonsInstantiated() {
        properties.useCases.forEachIndexed { index, definition ->
            val types = triggerTypes(index)
            if (types.isEmpty()) return@forEachIndexed
            require(types.size == 1) {
                "Use case '${definition.name}' must declare exactly one trigger, but declares $types. " +
                    "Available trigger types: ${registry.types}"
            }

            val type = types.single()
            val handler = registry.getHandler(type)
            register(handler, type, index, definition)
            if (handler !in startedHandlers) startedHandlers.add(handler)
        }

        startedHandlers.forEach { it.start() }
    }

    /** Trigger types declared by the use case at [index], whether or not [TriggerProperties] names them. */
    private fun triggerTypes(index: Int): Set<String> {
        val trigger: Map<String, Any>? =
            Binder
                .get(environment)
                .bind("enact.use-cases[$index].trigger", Bindable.mapOf(String::class.java, Any::class.java))
                .orElse(null)

        return trigger?.keys.orEmpty()
    }

    override fun destroy() = startedHandlers.asReversed().forEach { it.stop() }

    @Suppress("UNCHECKED_CAST")
    private fun register(
        handler: TriggerHandler<*>,
        type: String,
        index: Int,
        definition: EnactProperties.UseCaseDefinition,
    ) {
        val group = properties.groups[definition.group ?: DEFAULT_GROUP]
        val registration =
            TriggerRegistration(
                useCaseName = definition.name,
                useCase = applicationContext.getBean(definition.name) as UseCase<Any, Any>,
                definition = Binder.get(environment).bindOrCreate("enact.use-cases[$index].trigger.$type", handler.definitionType),
                groupFilters = group?.filters.orEmpty(),
            )

        (handler as TriggerHandler<Any>).register(registration)
    }

    private companion object {
        const val DEFAULT_GROUP = "default"
    }
}
