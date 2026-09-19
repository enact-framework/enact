package io.enact.trigger.rest

import io.enact.core.trigger.RestTriggerDefinition
import io.enact.core.trigger.TriggerHandler
import io.enact.core.trigger.TriggerRegistration
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
    private val beanFactory: ListableBeanFactory,
) : TriggerHandler<RestTriggerDefinition> {
    override val triggerType = "rest"
    override val definitionType = RestTriggerDefinition::class.java

    private val registrations = mutableListOf<Registration>()
    private var routerFunction: RouterFunction<ServerResponse>? = null

    private data class Registration(
        val name: String,
        val definition: RestTriggerDefinition,
        val useCase: UseCase<Any, Any>,
        val inputBinder: RestInputBinder,
        val filters: List<HandlerFilterFunction<ServerResponse, ServerResponse>>,
    )

    override fun register(registration: TriggerRegistration<RestTriggerDefinition>) {
        val useCaseName = registration.useCaseName
        val definition = registration.definition
        require(definition.method.uppercase() in SUPPORTED_METHODS) {
            "Unsupported HTTP method '${definition.method}' for use case '$useCaseName'. Supported: $SUPPORTED_METHODS"
        }
        require(definition.path.isNotBlank()) { "Missing path for REST trigger of use case '$useCaseName'" }

        val useCase = registration.useCase
        val inputBinder = RestInputBinder.of(useCaseName, definition.path, definition.bind, useCase.inputType, jsonMapper)
        val filters = filters(useCaseName, registration.groupFilters + definition.filters)
        registrations.add(Registration(useCaseName, definition, useCase, inputBinder, filters))
    }

    /** Resolves filter bean names, those of the use case's group first, then those of its trigger. */
    @Suppress("UNCHECKED_CAST")
    private fun filters(
        useCaseName: String,
        filterNames: List<String>,
    ): List<HandlerFilterFunction<ServerResponse, ServerResponse>> =
        filterNames.map { name ->
            try {
                beanFactory.getBean(name, HandlerFilterFunction::class.java) as HandlerFilterFunction<ServerResponse, ServerResponse>
            } catch (e: BeansException) {
                throw IllegalArgumentException(
                    "REST trigger of use case '$useCaseName' references filter '$name', which is not a HandlerFilterFunction bean",
                    e,
                )
            }
        }

    override fun start() {
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

    /** Routes of all registered use cases; empty until [start] runs. */
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
        val SUPPORTED_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE")
        val NO_CONTENT_TYPES = setOf(Unit::class.java, Void.TYPE, Void::class.java)
    }
}
