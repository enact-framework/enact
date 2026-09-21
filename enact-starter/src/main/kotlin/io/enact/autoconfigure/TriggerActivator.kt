package io.enact.autoconfigure

import io.enact.autoconfigure.definition.Definitions
import io.enact.autoconfigure.definition.UseCaseDefinition
import io.enact.autoconfigure.definition.definitionMapper
import io.enact.core.trigger.TriggerHandler
import io.enact.core.trigger.TriggerHandlerRegistry
import io.enact.core.trigger.TriggerRegistration
import io.enact.core.usecase.UseCase
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.context.ApplicationContext
import tools.jackson.databind.JsonNode

/**
 * Hands every use case declaring a trigger to the handler of that trigger type, then starts the handlers.
 *
 * Trigger types are read from the definition itself rather than from
 * [TriggerProperties][io.enact.core.trigger.TriggerProperties], which only names the types Enact ships so that
 * editors can complete them. The block under `trigger.<type>` stays a tree until here, where it is read into
 * the handler's [TriggerHandler.definitionType], so a new trigger type needs no change in this class.
 */
class TriggerActivator(
    private val definitions: Definitions,
    private val registry: TriggerHandlerRegistry,
    private val applicationContext: ApplicationContext,
) : SmartInitializingSingleton,
    DisposableBean {
    private val startedHandlers = mutableListOf<TriggerHandler<*>>()
    private val mapper = definitionMapper()

    override fun afterSingletonsInstantiated() {
        definitions.useCases.forEach { (name, definition) ->
            val types = definition.trigger.keys
            if (types.isEmpty()) return@forEach
            require(types.size == 1) {
                "Use case '$name'${definitions.sourceOf(name)} must declare exactly one trigger, " +
                    "but declares $types. Available trigger types: ${registry.types}"
            }

            val type = types.single()
            val handler = registry.getHandler(type)
            register(handler, definition.trigger.getValue(type), name, definition)
            if (handler !in startedHandlers) startedHandlers.add(handler)
        }

        startedHandlers.forEach { it.start() }
    }

    override fun destroy() = startedHandlers.asReversed().forEach { it.stop() }

    @Suppress("UNCHECKED_CAST")
    private fun register(
        handler: TriggerHandler<*>,
        trigger: JsonNode,
        name: String,
        useCase: UseCaseDefinition,
    ) {
        val group = definitions.groups[useCase.group ?: Definitions.DEFAULT_GROUP]
        // `rest:` without a body is a trigger taking every default of its definition type.
        val declared = trigger.takeUnless { it.isNull } ?: mapper.createObjectNode()
        val registration =
            TriggerRegistration(
                useCaseName = name,
                useCase = applicationContext.getBean(name) as UseCase<Any, Any>,
                definition = mapper.treeToValue(declared, handler.definitionType),
                groupFilters = group?.filters.orEmpty(),
            )

        (handler as TriggerHandler<Any>).register(registration)
    }
}
