package io.enact.trigger.rest

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.function.ServerRequest
import tools.jackson.core.JacksonException
import tools.jackson.core.JsonPointer
import tools.jackson.databind.JsonNode
import tools.jackson.databind.deser.bean.BeanDeserializerBase
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

/**
 * Builds a use case input from an HTTP request.
 *
 * Path variables and query parameters bind to input properties of the same name, the JSON body to the input itself;
 * [bind] maps properties to any other source. A property gets its value from exactly one source. Bindings are
 * validated on creation, so misconfiguration fails at startup.
 */
internal class RestInputBinder(
    private val useCaseName: String,
    path: String,
    bind: Map<String, String>,
    private val inputType: Class<*>,
    private val jsonMapper: JsonMapper,
) {
    private sealed interface Source {
        data class Header(
            val name: String,
        ) : Source {
            override fun toString() = "header $name"
        }

        data class Query(
            val name: String,
        ) : Source {
            override fun toString() = "query parameter $name"
        }

        data class Path(
            val name: String,
        ) : Source {
            override fun toString() = "path variable {$name}"
        }

        data class Body(
            val pointer: JsonPointer,
        ) : Source {
            override fun toString() = if (pointer.matches()) "body" else "body $pointer"
        }
    }

    private val pathVariables = PATH_VARIABLE.findAll(path).map { it.groupValues[1] }.toList()
    private val sources = bind.mapValues { (property, source) -> parseSource(property, source) }

    /** Properties bound from a single source, explicitly or as a path variable of the same name. */
    private val bindings: Map<String, Source>

    /** Unbound properties that take a query parameter of the same name, if present. */
    private val queryProperties: Set<String>

    /** Properties accepting several values, e.g. a repeated query parameter. */
    private val multiValued: Set<String>

    /** Whether the JSON body is the input itself rather than bound to properties. */
    private val bodyAtRoot = sources.values.none { it is Source.Body }

    private val kind: InputKind

    init {
        val implicitPathVariables =
            pathVariables -
                sources.values
                    .filterIsInstance<Source.Path>()
                    .map { it.name }
                    .toSet()
        val properties = inputProperties()

        invalidIf(sources.values.count { it is Source.Body && it.pointer.matches() } > 1) { "binds the whole body more than once" }
        kind =
            when {
                inputType in NO_INPUT_TYPES -> {
                    invalidIf(bind.isNotEmpty()) { "declares bind but takes no input" }
                    InputKind.NONE
                }
                properties == null -> {
                    invalidIf(bind.isNotEmpty() || pathVariables.size > 1) {
                        "binds several values to input ${inputType.simpleName}; use a data class instead"
                    }
                    InputKind.VALUE
                }
                else -> {
                    sources.keys.filterNot { it in properties }.forEach {
                        invalid("binds unknown property '$it' of ${inputType.simpleName}. Known: ${properties.keys}")
                    }
                    implicitPathVariables.filterNot { it in properties && it !in sources }.forEach {
                        invalid("has path variable {$it} that matches no property of ${inputType.simpleName}; add one or bind it")
                    }
                    InputKind.OBJECT
                }
            }

        bindings = sources + implicitPathVariables.associateWith { Source.Path(it) }
        queryProperties = properties.orEmpty().keys - bindings.keys
        multiValued = properties.orEmpty().filterValues { it }.keys
    }

    /** Reads the input for [request]; answers `400 Bad Request` if the request does not fit the input type. */
    fun bind(request: ServerRequest): Any =
        try {
            when (kind) {
                InputKind.NONE -> Unit
                InputKind.VALUE -> readValue(request)
                InputKind.OBJECT -> readObject(request)
            }
        } catch (e: JacksonException) {
            throw badRequest("Invalid request: ${e.originalMessage}", e)
        }

    private fun readValue(request: ServerRequest): Any {
        val node =
            pathVariables.singleOrNull()?.let { jsonMapper.nodeFactory.stringNode(request.pathVariable(it)) }
                ?: readBody(request)
                ?: throw badRequest("Request body is required")
        return jsonMapper.treeToValue(node, inputType) ?: throw badRequest("Request body is required")
    }

    private fun readObject(request: ServerRequest): Any {
        val body = readBody(request)
        val input: ObjectNode =
            when {
                !bodyAtRoot || body == null -> jsonMapper.createObjectNode()
                body is ObjectNode -> body
                else -> throw badRequest("Request body must be a JSON object")
            }

        bindings.forEach { (property, source) ->
            if (input.has(property)) throw conflict(property, source)
            val value =
                when (source) {
                    is Source.Header -> textValue(property, request.headers().header(source.name))
                    is Source.Query -> textValue(property, request.params()[source.name].orEmpty())
                    is Source.Path -> textValue(property, listOf(request.pathVariable(source.name)))
                    is Source.Body -> body?.at(source.pointer)?.takeUnless { it.isMissingNode }
                }
            value?.let { input.set(property, it) }
        }
        queryProperties.forEach { property ->
            val value = textValue(property, request.params()[property].orEmpty()) ?: return@forEach
            if (input.has(property)) throw conflict(property, Source.Query(property))
            input.set(property, value)
        }

        return jsonMapper.treeToValue(input, inputType)
    }

    private fun readBody(request: ServerRequest): JsonNode? {
        val body = request.body(ByteArray::class.java)
        return if (body.isEmpty()) null else jsonMapper.readTree(body)
    }

    /** Request strings as a JSON value: `null` if absent, an array for multi-valued properties. */
    private fun textValue(
        property: String,
        values: List<String>,
    ): JsonNode? =
        when {
            values.isEmpty() -> null
            property in multiValued -> jsonMapper.valueToTree<JsonNode>(values)
            values.size > 1 -> throw badRequest("'$property' expects a single value but got ${values.size}")
            else -> jsonMapper.nodeFactory.stringNode(values.single())
        }

    /** Deserializable properties of the input type and whether each takes several values; `null` for non-objects. */
    private fun inputProperties(): Map<String, Boolean>? {
        if (inputType in NO_INPUT_TYPES) return null
        // Ask Jackson itself, so @JsonProperty names, Kotlin/record creators and custom deserializers apply
        val context = jsonMapper._deserializationContext()
        val type = jsonMapper.constructType(inputType)
        if (context.findRootValueDeserializer(type) !is BeanDeserializerBase) return null

        return context
            .introspectBeanDescriptionForCreation(type)
            .findProperties()
            .associate { it.name to (it.primaryType.isCollectionLikeType || it.primaryType.isArrayType) }
    }

    private fun parseSource(
        property: String,
        source: String,
    ): Source {
        val kind = source.substringBefore(':').trim()
        val name = source.substringAfter(':', "").trim()
        return when {
            kind == "body" && name.isEmpty() -> Source.Body(JsonPointer.empty())
            kind == "body" && name.startsWith('/') -> Source.Body(JsonPointer.compile(name))
            name.isEmpty() -> invalid("binds '$property' to invalid source '$source'")
            kind == "header" -> Source.Header(name)
            kind == "query" -> Source.Query(name)
            kind == "path" && name in pathVariables -> Source.Path(name)
            kind == "path" -> invalid("binds '$property' to path variable {$name}, which is not in the path")
            else -> invalid("binds '$property' to invalid source '$source'. Expected header:, query:, path:, body or body:/pointer")
        }
    }

    private fun conflict(
        property: String,
        source: Source,
    ) = badRequest("'$property' is bound from $source and must not be sent in the request body")

    private fun invalidIf(
        condition: Boolean,
        reason: () -> String,
    ) {
        if (condition) invalid(reason())
    }

    private fun invalid(reason: String): Nothing = throw IllegalArgumentException("REST trigger of use case '$useCaseName' $reason")

    private fun badRequest(
        reason: String,
        cause: Throwable? = null,
    ) = ResponseStatusException(HttpStatus.BAD_REQUEST, reason, cause)

    private enum class InputKind { NONE, VALUE, OBJECT }

    private companion object {
        val NO_INPUT_TYPES = setOf(Unit::class.java, Void.TYPE, Void::class.java)
        val PATH_VARIABLE = Regex("""\{\*?([^}:]+)(?::[^}]*)?}""")
    }
}
