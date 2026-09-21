package io.enact.autoconfigure.definition

import io.enact.autoconfigure.properties.EnactProperties
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertyName
import org.springframework.boot.context.properties.source.ConfigurationPropertyName.Form
import org.springframework.boot.context.properties.source.ConfigurationPropertySource
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource
import org.springframework.boot.env.OriginTrackedMapPropertySource
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.origin.Origin
import org.springframework.boot.origin.OriginLookup
import org.springframework.boot.origin.OriginTrackedValue
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.env.PropertySource
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.io.support.ResourcePatternResolver
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Reads the use cases and groups of the definition files into a single property source.
 *
 * Files are listed under `enact.definitions` as a file, a folder or an Ant pattern, and default to
 * `optional:classpath:enact/`. A file declares `use-cases` and `groups`, which this loader moves under
 * `enact` so that Spring reads them like any other configuration. Both are maps keyed by name, and Spring
 * merges a map across property sources, so the files and `application.yaml` add up on their own.
 *
 * What is left is telling a reader where a definition comes from: values keep their origin, so an error names
 * the file and the line, and a name declared twice is reported instead of one declaration quietly winning.
 */
class DefinitionFileLoader(
    private val resolver: ResourcePatternResolver = PathMatchingResourcePatternResolver(),
) {
    /** Returns the definitions found in the configured files, or `null` when there is no file to read. */
    fun load(environment: ConfigurableEnvironment): PropertySource<*>? {
        val binder = Binder.get(environment)
        if (binder.bind(ENABLED, Boolean::class.javaObjectType).orElse(true) == false) return null

        val bound = binder.bind(DEFINITIONS, Bindable.listOf(String::class.java))
        val locations = if (bound.isBound) bound.get() else EnactProperties().definitions
        val files = locations.flatMap(::resolve).distinctBy(::identity)
        if (files.isEmpty()) return null

        val definitions = LinkedHashMap<String, Any>()
        val declarations = Declarations(ConfigurationPropertySources.get(environment))
        files.forEach { file ->
            YamlPropertySourceLoader().load(file.description, file).forEach { document ->
                read(document as EnumerablePropertySource<*>, definitions, declarations)
            }
        }
        return OriginTrackedMapPropertySource(PROPERTY_SOURCE_NAME, definitions, true)
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

    /**
     * Moves one YAML document under `enact`, keeping each key as it was written: a configuration property name
     * holds map keys such as a use case name or a `bind` entry in lower case, which would rename them.
     */
    private fun read(
        document: EnumerablePropertySource<*>,
        definitions: MutableMap<String, Any>,
        declarations: Declarations,
    ) {
        document.propertyNames.forEach { key ->
            val value = document.getProperty(key) ?: return@forEach
            val origin = OriginLookup.getOrigin(document, key)
            val name = ConfigurationPropertyName.adapt(key, '.')
            val where = origin?.toString() ?: document.name

            if (name == USE_CASES || name == GROUPS) {
                require(value.toString().isEmpty()) { "'$key' in ${document.name} must hold entries by name, see $where." }
                return@forEach
            }
            require(USE_CASES.isAncestorOf(name) || GROUPS.isAncestorOf(name)) {
                "'$key' in ${document.name} is not a use case definition, see $where. A definition file " +
                    "declares 'use-cases' and 'groups' at its root, without the 'enact' prefix."
            }

            declarations.declare(name, document, where)
            definitions["$ENACT.$key"] = OriginTrackedValue.of(value, origin)!!
        }
    }

    /**
     * Remembers which document declares a use case or a group, so that a name used twice is reported. Spring
     * would otherwise merge the two declarations, property by property, in property source order.
     */
    private class Declarations(
        private val sources: Iterable<ConfigurationPropertySource>,
    ) {
        private val documents = mutableMapOf<ConfigurationPropertyName, Pair<PropertySource<*>, String>>()

        fun declare(
            name: ConfigurationPropertyName,
            document: PropertySource<*>,
            where: String,
        ) {
            val declared = name.chop(2)
            val owner = documents[declared]
            if (owner?.first === document) return

            require(owner == null) { "${kindOf(declared)} is declared twice: ${owner?.second} and $where." }
            val inApplication = originOf(declared)
            require(inApplication == null) { "${kindOf(declared)} is declared twice: $inApplication and $where." }
            documents[declared] = document to where
        }

        /** The origin of the declaration in the application configuration, when there is one. */
        private fun originOf(declared: ConfigurationPropertyName): Origin? {
            val prefix = ENACT_PREFIX.append(declared)
            return sources
                .filterIsInstance<IterableConfigurationPropertySource>()
                .firstNotNullOfOrNull { source -> source.firstOrNull(prefix::isAncestorOf)?.let(source::getConfigurationProperty) }
                ?.origin
        }

        private fun kindOf(declared: ConfigurationPropertyName): String {
            val kind = if (USE_CASES.isAncestorOf(declared)) "Use case" else "Group"
            return "$kind '${declared.getElement(1, Form.ORIGINAL)}'"
        }
    }

    private companion object {
        const val PROPERTY_SOURCE_NAME = "enactDefinitions"
        const val OPTIONAL_PREFIX = "optional:"
        const val ENACT = "enact"
        const val ENABLED = "enact.enabled"
        const val DEFINITIONS = "enact.definitions"
        val YAML_EXTENSIONS = listOf("yaml", "yml")
        val ENACT_PREFIX: ConfigurationPropertyName = ConfigurationPropertyName.of(ENACT)
        val USE_CASES: ConfigurationPropertyName = ConfigurationPropertyName.of("use-cases")
        val GROUPS: ConfigurationPropertyName = ConfigurationPropertyName.of("groups")
    }
}
