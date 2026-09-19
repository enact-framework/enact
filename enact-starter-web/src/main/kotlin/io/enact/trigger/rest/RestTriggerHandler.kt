package io.enact.trigger.rest

import io.enact.autoconfigure.properties.EnactProperties
import io.enact.core.trigger.RestTriggerDefinition
import io.enact.core.trigger.TriggerHandler
import io.enact.core.trigger.TriggerProperties
import io.enact.core.usecase.UseCase
import org.springframework.beans.BeansException
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.web.servlet.function.HandlerFilterFunction
import org.springframework.web.servlet.function.HandlerFunction
import org.springframework.web.servlet.function.RequestPredicates
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.RouterFunctions
import org.springframework.web.servlet.function.ServerRequest
import org.springframework.web.servlet.function.ServerResponse
import tools.jackson.databind.json.JsonMapper
import java.util.Optional

class RestTriggerHandler(
    private val jsonMapper: JsonMapper,
    private val properties: EnactProperties,
    private val beanFactory: ListableBeanFactory,
) : TriggerHandler<RestTriggerDefinition> {
    override val triggerType = "rest"

    private val registrations = mutableListOf<Registration>()
    private var routerFunction: RouterFunction<ServerResponse>? = null

    private data class Registration(
        val name: String,
        val definition: RestTriggerDefinition,
        val useCase: UseCase<Any, Any>,
        val inputBinder: RestInputBinder,
        val filters: List<HandlerFilterFunction<ServerResponse, ServerResponse>>,
    )

    override fun extractDefinition(trigger: TriggerProperties): RestTriggerDefinition? = trigger.rest

    override fun register(
        useCaseName: String,
        definition: RestTriggerDefinition,
        useCase: UseCase<Any, Any>,
    ) {
        require(definition.method.uppercase() in SUPPORTED_METHODS) {
            "Unsupported HTTP method '${definition.method}' for use case '$useCaseName'. Supported: $SUPPORTED_METHODS"
        }
        require(definition.path.isNotBlank()) { "Missing path for REST trigger of use case '$useCaseName'" }

        val inputBinder = RestInputBinder.of(useCaseName, definition.path, definition.bind, useCase.inputType, jsonMapper)
        registrations.add(Registration(useCaseName, definition, useCase, inputBinder, filters(useCaseName, definition)))
    }

    /** Filters of the use case's group (or `default`), then those of its trigger. */
    @Suppress("UNCHECKED_CAST")
    private fun filters(
        useCaseName: String,
        definition: RestTriggerDefinition,
    ): List<HandlerFilterFunction<ServerResponse, ServerResponse>> {
        val group = properties.useCases.firstOrNull { it.name == useCaseName }?.group ?: DEFAULT_GROUP
        return (properties.groups[group]?.filters.orEmpty() + definition.filters).map { name ->
            try {
                beanFactory.getBean(name, HandlerFilterFunction::class.java) as HandlerFilterFunction<ServerResponse, ServerResponse>
            } catch (e: BeansException) {
                throw IllegalArgumentException(
                    "REST trigger of use case '$useCaseName' references filter '$name', which is not a HandlerFilterFunction bean",
                    e,
                )
            }
        }
    }

    override fun activate() {
        routerFunction =
            RouterFunctions
                .route()
                .apply {
                    registrations.forEach { reg ->
                        val method = HttpMethod.valueOf(reg.definition.method.uppercase())
                        val handler = HandlerFunction { handle(it, reg) }
                        route(
                            RequestPredicates.method(method).and(RequestPredicates.path(reg.definition.path)),
                            reg.filters.reduceOrNull { outer, inner -> outer.andThen(inner) }?.apply(handler) ?: handler,
                        )
                    }
                }.build()
    }

    /** Routes of all registered use cases; empty until [activate] runs. */
    fun findHandler(request: ServerRequest): Optional<HandlerFunction<ServerResponse>> = routerFunction?.route(request) ?: Optional.empty()

    private fun handle(
        request: ServerRequest,
        reg: Registration,
    ): ServerResponse {
        val output = reg.useCase.execute(reg.inputBinder.bind(request))
        val response = ServerResponse.status(HttpStatusCode.valueOf(reg.definition.status))

        return if (reg.useCase.outputType in NO_CONTENT_TYPES) {
            response.build()
        } else {
            response.contentType(MediaType.parseMediaType(reg.definition.produces)).body(output)
        }
    }

    private companion object {
        const val DEFAULT_GROUP = "default"
        val SUPPORTED_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE")
        val NO_CONTENT_TYPES = setOf(Unit::class.java, Void.TYPE, Void::class.java)
    }
}
