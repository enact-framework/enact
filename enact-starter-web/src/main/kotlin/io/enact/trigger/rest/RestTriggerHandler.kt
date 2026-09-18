package io.enact.trigger.rest

import io.enact.core.trigger.RestTriggerDefinition
import io.enact.core.trigger.TriggerHandler
import io.enact.core.trigger.TriggerProperties
import io.enact.core.usecase.UseCase
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.function.HandlerFunction
import org.springframework.web.servlet.function.RequestPredicates
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.RouterFunctions
import org.springframework.web.servlet.function.ServerRequest
import org.springframework.web.servlet.function.ServerResponse
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.util.Optional

class RestTriggerHandler(
    private val jsonMapper: JsonMapper,
) : TriggerHandler<RestTriggerDefinition> {
    override val triggerType = "rest"

    private val registrations = mutableListOf<Registration>()
    private var routerFunction: RouterFunction<ServerResponse>? = null

    private data class Registration(
        val name: String,
        val definition: RestTriggerDefinition,
        val useCase: UseCase<Any, Any>,
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

        registrations.add(Registration(useCaseName, definition, useCase))
    }

    override fun activate() {
        routerFunction =
            RouterFunctions
                .route()
                .apply {
                    registrations.forEach { reg ->
                        val method = HttpMethod.valueOf(reg.definition.method.uppercase())
                        route(RequestPredicates.method(method).and(RequestPredicates.path(reg.definition.path))) {
                            handle(it, reg)
                        }
                    }
                }.build()
    }

    /** Routes of all registered use cases; empty until [activate] runs. */
    fun findHandler(request: ServerRequest): Optional<HandlerFunction<ServerResponse>> = routerFunction?.route(request) ?: Optional.empty()

    private fun handle(
        request: ServerRequest,
        reg: Registration,
    ): ServerResponse {
        val output = reg.useCase.execute(readInput(request, reg.useCase.inputType))
        val response = ServerResponse.status(HttpStatusCode.valueOf(reg.definition.status))

        return if (reg.useCase.outputType in NO_CONTENT_TYPES) {
            response.build()
        } else {
            response.contentType(MediaType.parseMediaType(reg.definition.produces)).body(output)
        }
    }

    /**
     * Builds the use case input from the JSON body, overlaid with query parameters and then path variables.
     */
    private fun readInput(
        request: ServerRequest,
        inputType: Class<*>,
    ): Any {
        if (inputType in NO_CONTENT_TYPES) return Unit

        try {
            val body = request.body(ByteArray::class.java)
            val node: JsonNode = if (body.isEmpty()) jsonMapper.createObjectNode() else jsonMapper.readTree(body)
            val params = request.params().filterValues { it.isNotEmpty() } + request.pathVariables().mapValues { listOf(it.value) }

            if (params.isNotEmpty()) {
                if (node !is ObjectNode) throw badRequest("Request body must be a JSON object when parameters are present")
                params.forEach { (name, values) ->
                    if (values.size == 1) node.put(name, values[0]) else node.putPOJO(name, values)
                }
            }

            return jsonMapper.treeToValue(node, inputType)
        } catch (e: JacksonException) {
            throw badRequest("Invalid request: ${e.originalMessage}", e)
        }
    }

    private fun badRequest(
        reason: String,
        cause: Throwable? = null,
    ) = ResponseStatusException(HttpStatus.BAD_REQUEST, reason, cause)

    private companion object {
        val SUPPORTED_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE")
        val NO_CONTENT_TYPES = setOf(Unit::class.java, Void.TYPE, Void::class.java)
    }
}
