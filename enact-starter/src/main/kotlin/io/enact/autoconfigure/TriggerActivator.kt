package io.enact.autoconfigure

import io.enact.autoconfigure.properties.EnactProperties
import io.enact.core.trigger.TriggerHandler
import io.enact.core.trigger.TriggerHandlerRegistry
import io.enact.core.trigger.TriggerProperties
import io.enact.core.trigger.TriggerRegistration
import io.enact.core.usecase.UseCase
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.boot.context.properties.bind.BindHandler
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertyName
import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment

/**
 * Hands every use case declaring a trigger to the handler of that trigger type, then starts the handlers.
 *
 * Trigger types are read from the configuration itself rather than from
 * [TriggerProperties][io.enact.core.trigger.TriggerProperties], which only names the types Enact ships so that
 * editors can complete them. The definition is then bound from `enact.use-cases.<name>.trigger.<type>` to the
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
        properties.useCases.forEach { (name, definition) ->
            val trigger = triggerOf(name)
            val types = triggerTypes(trigger)
            if (types.isEmpty()) return@forEach
            require(types.size == 1) {
                "Use case '$name' must declare exactly one trigger, but declares $types. " +
                    "Available trigger types: ${registry.types}"
            }

            val type = types.single()
            val handler = registry.getHandler(type)
            register(handler, ConfigurationPropertyName.adapt("$trigger.$type", '.'), name, definition)
            if (handler !in startedHandlers) startedHandlers.add(handler)
        }

        startedHandlers.forEach { it.start() }
    }

    /**
     * The trigger of the use case, as a property name. It is adapted rather than parsed, because a use case
     * name is a bean name such as `createOrder`, which a configuration property name holds in lower case.
     */
    private fun triggerOf(useCase: String): ConfigurationPropertyName =
        ConfigurationPropertyName.adapt("enact.use-cases.$useCase.trigger", '.')

    /** Trigger types declared by the use case, whether or not [TriggerProperties] names them. */
    private fun triggerTypes(trigger: ConfigurationPropertyName): Set<String> {
        val types: Map<String, Any>? =
            Binder
                .get(environment)
                .bind(trigger, Bindable.mapOf(String::class.java, Any::class.java))
                .orElse(null)

        return types?.keys.orEmpty()
    }

    override fun destroy() = startedHandlers.asReversed().forEach { it.stop() }

    @Suppress("UNCHECKED_CAST")
    private fun register(
        handler: TriggerHandler<*>,
        definition: ConfigurationPropertyName,
        name: String,
        useCase: EnactProperties.UseCaseDefinition,
    ) {
        val group = properties.groups[useCase.group ?: DEFAULT_GROUP]
        val registration =
            TriggerRegistration(
                useCaseName = name,
                useCase = applicationContext.getBean(name) as UseCase<Any, Any>,
                definition =
                    Binder
                        .get(environment)
                        .bindOrCreate(definition, Bindable.of(handler.definitionType), BindHandler.DEFAULT),
                groupFilters = group?.filters.orEmpty(),
            )

        (handler as TriggerHandler<Any>).register(registration)
    }

    private companion object {
        const val DEFAULT_GROUP = "default"
    }
}
