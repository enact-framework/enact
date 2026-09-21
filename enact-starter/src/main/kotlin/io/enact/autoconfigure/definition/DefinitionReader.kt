package io.enact.autoconfigure.definition

import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.io.support.ResourcePatternResolver
import tools.jackson.core.JacksonException
import tools.jackson.dataformat.yaml.YAMLMapper
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Reads the use cases and groups of the definition files.
 *
 * Files are listed under `enact.definitions` as a file, a folder or an Ant pattern, and default to
 * `optional:classpath:enact/`. A file declares nothing but `use-cases` and `groups`; anything else is
 * reported with the line it is on, as is a key a definition does not know.
 *
 * Each name is declared once across every file, so that one declaration never quietly wins over another, and
 * the file a name comes from is remembered for the errors raised later against it.
 */
class DefinitionReader(
    private val resolver: ResourcePatternResolver = PathMatchingResourcePatternResolver(),
    private val mapper: YAMLMapper = definitionMapper(),
) {
    fun read(locations: List<String>): Definitions {
        val useCases = LinkedHashMap<String, UseCaseDefinition>()
        val groups = LinkedHashMap<String, GroupDefinition>()
        // A use case name is a bean name and a group name is not, so the two are told apart on their own.
        val useCaseSources = LinkedHashMap<String, String>()
        val groupSources = LinkedHashMap<String, String>()

        locations
            .flatMap(::resolve)
            .distinctBy(::identity)
            .forEach { file ->
                val where = file.description
                val document = parse(file)

                document.useCases.forEach { (name, useCase) ->
                    declare(name, "Use case", useCaseSources, where)
                    useCases[name] = useCase
                }
                document.groups.forEach { (name, group) ->
                    declare(name, "Group", groupSources, where)
                    groups[name] = group
                }
            }

        return Definitions(useCases, groups, useCaseSources)
    }

    private fun declare(
        name: String,
        kind: String,
        sources: MutableMap<String, String>,
        where: String,
    ) {
        val owner = sources[name]
        require(owner == null) { "$kind '$name' is declared twice: $owner and $where." }
        sources[name] = where
    }

    private fun parse(file: Resource): DefinitionDocument =
        try {
            file.inputStream.use { mapper.readValue(it, DefinitionDocument::class.java) }
        } catch (exception: JacksonException) {
            val at = exception.location?.let { " at line ${it.lineNr}, column ${it.columnNr}" }.orEmpty()
            // Only a failure on the document itself is a stray root key; a nested one already names its class.
            val hint =
                if (DefinitionDocument::class.java.simpleName in exception.originalMessage) {
                    " A definition file declares 'use-cases' and 'groups' at its root, without the 'enact' prefix."
                } else {
                    ""
                }
            throw IllegalArgumentException(
                "Cannot read use case definitions from ${file.description}: ${exception.originalMessage}$at.$hint",
                exception,
            )
        }

    private fun resolve(location: String): List<Resource> {
        val optional = location.startsWith(OPTIONAL_PREFIX)
        val path = location.removePrefix(OPTIONAL_PREFIX)
        val patterns = if (path.endsWith("/")) YAML_EXTENSIONS.map { "$path*.$it" } else listOf(path)
        val files = patterns.flatMap(::resources).sortedBy(::identity)

        require(optional || files.isNotEmpty()) {
            "No use case definition file found at '$location'. A folder ends with '/', " +
                "and 'optional:$location' allows the location to be missing."
        }
        files.forEach { file ->
            require(file.filename?.substringAfterLast('.', "") in YAML_EXTENSIONS) {
                "Use case definitions must be YAML files, but '$location' matched '${file.filename}'."
            }
        }
        return files
    }

    private fun resources(pattern: String): List<Resource> =
        try {
            resolver.getResources(pattern).filter { it.exists() && it.isReadable }
        } catch (_: FileNotFoundException) {
            emptyList() // the folder of the pattern does not exist, which `resolve` reports with the location as written
        } catch (exception: IOException) {
            throw IllegalStateException("Cannot read use case definitions from '$pattern'.", exception)
        }

    private fun identity(file: Resource): String = runCatching { file.uri.toString() }.getOrDefault(file.description)

    /** Root of a definition file. Only these two keys, so that a stray one is reported instead of ignored. */
    private data class DefinitionDocument(
        val useCases: Map<String, UseCaseDefinition> = emptyMap(),
        val groups: Map<String, GroupDefinition> = emptyMap(),
    )

    private companion object {
        const val OPTIONAL_PREFIX = "optional:"
        val YAML_EXTENSIONS = setOf("yaml", "yml")
    }
}
