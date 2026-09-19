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
 * `bind` maps properties to any other source. A property gets its value from exactly one source. Bindings are
 * validated by [of], so misconfiguration fails at startup.
 */
internal sealed class RestInputBinder {
    /** Reads the input for [request]; answers `400 Bad Request` if the request does not fit the input type. */
    fun bind(request: ServerRequest): Any =
        try {
            read(request)
        } catch (e: JacksonException) {
            throw badRequest("Invalid request: ${e.originalMessage}", e)
        }

    protected abstract fun read(request: ServerRequest): Any

    companion object {
        /** Validates the bindings of a use case and returns the binder for its input type. */
        fun of(
            useCaseName: String,
            path: String,
            bind: Map<String, String>,
            inputType: Class<*>,
            jsonMapper: JsonMapper,
        ): RestInputBinder {
            fun invalid(reason: String): Nothing = throw IllegalArgumentException("REST trigger of use case '$useCaseName' $reason")

            val pathVariables = PATH_VARIABLE.findAll(path).map { it.groupValues[1] }.toList()
            val sources =
                bind.mapValues { (property, spec) ->
                    Source.parse(spec)
                        ?: invalid("binds '$property' to invalid source '$spec'. Expected header:, query:, path:, body or body:/pointer")
                }
            sources.forEach { (property, source) ->
                if (source is Source.Path && source.name !in pathVariables) {
                    invalid("binds '$property' to path variable {${source.name}}, which is not in the path")
                }
            }
            if (sources.values.count { it is Source.Body && it.pointer.matches() } > 1) invalid("binds the whole body more than once")

            if (inputType in NO_INPUT_TYPES) {
                if (bind.isNotEmpty()) invalid("declares bind but takes no input")
                return NoInput
            }

            val properties = jsonMapper.beanProperties(inputType)
            if (properties == null) {
                if (bind.isNotEmpty() || pathVariables.size > 1) {
                    invalid("binds several values to input ${inputType.simpleName}; use a data class instead")
                }
                return ScalarInput(pathVariables.singleOrNull(), inputType, jsonMapper)
            }

            val implicitPathVariables =
                pathVariables -
                    sources.values
                        .filterIsInstance<Source.Path>()
                        .map { it.name }
                        .toSet()
            sources.keys.filterNot { it in properties }.forEach {
                invalid("binds unknown property '$it' of ${inputType.simpleName}. Known: ${properties.keys}")
            }
            implicitPathVariables.filter { it !in properties || it in sources }.forEach {
                invalid("has path variable {$it} that matches no property of ${inputType.simpleName}; add one or bind it")
            }

            val bindings = sources + implicitPathVariables.associateWith { Source.Path(it) }
            return BeanInput(
                bindings = bindings,
                queryProperties = properties.keys - bindings.keys,
                multiValued = properties.filterValues { it }.keys,
                bodyAtRoot = sources.values.none { it is Source.Body },
                inputType = inputType,
                jsonMapper = jsonMapper,
            )
        }
    }
}

/** A use case without input: the request is not read. */
private data object NoInput : RestInputBinder() {
    override fun read(request: ServerRequest): Any = Unit
}

/** A single value: the path variable if the path has exactly one, the body otherwise. */
private class ScalarInput(
    private val pathVariable: String?,
    private val inputType: Class<*>,
    private val jsonMapper: JsonMapper,
) : RestInputBinder() {
    override fun read(request: ServerRequest): Any {
        val node =
            pathVariable?.let { jsonMapper.nodeFactory.stringNode(request.pathVariable(it)) }
                ?: request.jsonBody(jsonMapper)
                ?: throw badRequest("Request body is required")
        return jsonMapper.treeToValue(node, inputType) ?: throw badRequest("Request body is required")
    }
}

/** An object whose properties are filled from their sources, and from the body unless it is bound elsewhere. */
private class BeanInput(
    /** Properties owned by a source; the body must not contain them, whether the source is sent or not. */
    private val bindings: Map<String, Source>,
    /** Remaining properties, taking a query parameter of the same name if sent. */
    queryProperties: Set<String>,
    /** Properties accepting several values, e.g. a repeated query parameter. */
    private val multiValued: Set<String>,
    /** Whether the JSON body is the input itself rather than bound to properties. */
    private val bodyAtRoot: Boolean,
    private val inputType: Class<*>,
    private val jsonMapper: JsonMapper,
) : RestInputBinder() {
    private val sources = bindings + queryProperties.associateWith { Source.Query(it) }

    override fun read(request: ServerRequest): Any {
        val body = request.jsonBody(jsonMapper)
        val input: ObjectNode =
            when {
                !bodyAtRoot || body == null -> jsonMapper.createObjectNode()
                body is ObjectNode -> body
                else -> throw badRequest("Request body must be a JSON object")
            }

        bindings.keys.firstOrNull(input::has)?.let { throw conflict(it, bindings.getValue(it)) }
        sources.forEach { (property, source) ->
            val value = source.read(request, body, property) ?: return@forEach
            if (input.has(property)) throw conflict(property, source)
            input.set(property, value)
        }

        return jsonMapper.treeToValue(input, inputType)
    }

    private fun Source.read(
        request: ServerRequest,
        body: JsonNode?,
        property: String,
    ): JsonNode? =
        when (this) {
            is Source.Header -> textValue(property, request.headers().header(name))
            is Source.Query -> textValue(property, request.params()[name].orEmpty())
            is Source.Path -> textValue(property, listOf(request.pathVariable(name)))
            is Source.Body -> body?.at(pointer)?.takeUnless { it.isMissingNode }
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

    private fun conflict(
        property: String,
        source: Source,
    ) = badRequest("'$property' is bound from $source and must not be sent in the request body")
}

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

    companion object {
        /** Parses `header:<name>`, `query:<name>`, `path:<name>`, `body` or `body:<json-pointer>`; `null` if malformed. */
        fun parse(spec: String): Source? {
            val kind = spec.substringBefore(':').trim()
            val name = spec.substringAfter(':', "").trim()
            return when {
                kind == "body" && name.isEmpty() -> Body(JsonPointer.empty())
                kind == "body" -> runCatching { JsonPointer.compile(name) }.getOrNull()?.let(::Body)
                name.isEmpty() -> null
                kind == "header" -> Header(name)
                kind == "query" -> Query(name)
                kind == "path" -> Path(name)
                else -> null
            }
        }
    }
}

private fun ServerRequest.jsonBody(jsonMapper: JsonMapper): JsonNode? =
    body(ByteArray::class.java).takeUnless { it.isEmpty() }?.let(jsonMapper::readTree)

/** Deserializable properties and whether each takes several values; `null` if Jackson does not read [type] as an object. */
private fun JsonMapper.beanProperties(type: Class<*>): Map<String, Boolean>? {
    // Ask Jackson itself, so @JsonProperty names, Kotlin/record creators and custom deserializers apply
    val context = _deserializationContext()
    val javaType = constructType(type)
    if (context.findRootValueDeserializer(javaType) !is BeanDeserializerBase) return null

    return context
        .introspectBeanDescriptionForCreation(javaType)
        .findProperties()
        .associate { it.name to (it.primaryType.isCollectionLikeType || it.primaryType.isArrayType) }
}

private fun badRequest(
    reason: String,
    cause: Throwable? = null,
) = ResponseStatusException(HttpStatus.BAD_REQUEST, reason, cause)

private val NO_INPUT_TYPES = setOf(Unit::class.java, Void.TYPE, Void::class.java)
private val PATH_VARIABLE = Regex("""\{\*?([^}:]+)(?::[^}]*)?}""")
