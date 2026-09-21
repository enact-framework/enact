package io.enact.autoconfigure.definition

import io.enact.autoconfigure.properties.EnactProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

class DefinitionFileLoaderTest {
    @Test
    fun `should read use cases from a file`() {
        val enact = load("enact.definitions[0]=classpath:definitions/domain/users.yaml")

        assertThat(enact.useCases.keys).containsExactly("createUser", "findUser")
        assertThat(enact.useCases.getValue("createUser").steps).extracting<String> { it.step }.containsExactly("saveUser")
    }

    @Test
    fun `should read every file of a folder in file name order`() {
        val enact = load("enact.definitions[0]=classpath:definitions/domain/")

        assertThat(enact.useCases.keys).containsExactly("createOrder", "createUser", "findUser")
    }

    @Test
    fun `should read the default location without configuration`() {
        val enact = load()

        assertThat(enact.useCases.keys).containsExactly("greet")
        assertThat(enact.useCases.getValue("greet").description).isEqualTo("Greets someone by name")
    }

    @Test
    fun `should read the files of every location in order`() {
        val enact =
            load(
                "enact.definitions[0]=classpath:definitions/domain/users.yaml",
                "enact.definitions[1]=classpath:definitions/domain/orders.yaml",
            )

        assertThat(enact.useCases.keys).containsExactly("createUser", "findUser", "createOrder")
    }

    @Test
    fun `should read the use cases of the application configuration next to those of the files`() {
        val enact =
            load(
                "enact.definitions[0]=classpath:definitions/domain/users.yaml",
                "enact.use-cases.generateId.steps[0].step=generateId",
            )

        assertThat(enact.useCases.keys).containsExactlyInAnyOrder("generateId", "createUser", "findUser")
        assertThat(enact.useCases.getValue("generateId").steps).extracting<String> { it.step }.containsExactly("generateId")
    }

    @Test
    fun `should read every document of a file`() {
        val enact = load("enact.definitions[0]=classpath:definitions/multi-document.yaml")

        assertThat(enact.useCases.keys).containsExactly("first", "second")
    }

    @Test
    fun `should read groups declared next to the use cases that need them`() {
        val enact = load("enact.definitions[0]=classpath:definitions/domain/orders.yaml")

        assertThat(enact.groups).containsOnlyKeys("orderAdmin")
        assertThat(enact.groups.getValue("orderAdmin").filters).containsExactly("audit")
        assertThat(enact.useCases.getValue("createOrder").group).isEqualTo("orderAdmin")
    }

    @Test
    fun `should keep the case of the names and keys written in a file`() {
        val enact = load("enact.definitions[0]=classpath:definitions/domain/orders.yaml")

        assertThat(enact.useCases).containsOnlyKeys("createOrder")
        assertThat(
            enact.useCases
                .getValue("createOrder")
                .trigger
                ?.rest
                ?.bind,
        ).containsExactly(entry("createdBy", "header:X-Created-By"))
    }

    @Test
    fun `should keep the groups of the application configuration`() {
        val enact =
            load(
                "enact.definitions[0]=classpath:definitions/domain/orders.yaml",
                "enact.groups.default.filters[0]=requestLog",
            )

        assertThat(enact.groups).containsOnlyKeys("default", "orderAdmin")
    }

    @Test
    fun `should accept a file without use cases`() {
        val enact = load("enact.definitions[0]=classpath:definitions/empty.yaml")

        assertThat(enact.useCases).isEmpty()
    }

    @Test
    fun `should ignore a missing optional location`() {
        val enact = load("enact.definitions[0]=optional:classpath:definitions/nowhere/")

        assertThat(enact.useCases).isEmpty()
    }

    @Test
    fun `should fail when a location holds no file`() {
        assertThatThrownBy { load("enact.definitions[0]=classpath:definitions/nowhere/") }
            .hasMessageContaining("No use case definition file found at 'classpath:definitions/nowhere/'")
            .hasMessageContaining("optional:")
    }

    @Test
    fun `should fail when a location holds a file that is not yaml`() {
        assertThatThrownBy { load("enact.definitions[0]=classpath:definitions/invalid/not-yaml.txt") }
            .hasMessageContaining("must be YAML files")
            .hasMessageContaining("not-yaml.txt")
    }

    @Test
    fun `should fail when the same use case is declared in two files`() {
        assertThatThrownBy {
            load(
                "enact.definitions[0]=classpath:definitions/domain/users.yaml",
                "enact.definitions[1]=classpath:definitions/invalid/duplicate.yaml",
            )
        }.hasMessageContaining("Use case 'createUser' is declared twice")
            .hasMessageContaining("definitions/domain/users.yaml")
            .hasMessageContaining("definitions/invalid/duplicate.yaml")
    }

    @Test
    fun `should fail when a use case is declared both in a file and in the application configuration`() {
        assertThatThrownBy {
            load(
                "enact.definitions[0]=classpath:definitions/domain/users.yaml",
                "enact.use-cases.createUser.steps[0].step=saveUser",
            )
        }.hasMessageContaining("Use case 'createUser' is declared twice")
            .hasMessageContaining("definitions/domain/users.yaml")
    }

    @Test
    fun `should fail when a group is declared both in a file and in the application configuration`() {
        assertThatThrownBy {
            load(
                "enact.definitions[0]=classpath:definitions/invalid/duplicate-group.yaml",
                "enact.groups.admin.filters[0]=bearerAuth",
            )
        }.hasMessageContaining("Group 'admin' is declared twice")
            .hasMessageContaining("definitions/invalid/duplicate-group.yaml")
    }

    @Test
    fun `should fail when a file repeats the enact prefix`() {
        assertThatThrownBy { load("enact.definitions[0]=classpath:definitions/invalid/prefixed.yaml") }
            .hasMessageContaining("is not a use case definition")
            .hasMessageContaining("prefixed.yaml")
            .hasMessageContaining("without the 'enact' prefix")
    }

    @Test
    fun `should read nothing when enact is disabled`() {
        val environment = environment("enact.enabled=false")

        assertThat(DefinitionFileLoader().load(environment)).isNull()
    }

    private fun load(vararg properties: String): EnactProperties {
        val environment = environment(*properties)
        DefinitionFileLoader().load(environment)?.let { environment.propertySources.addLast(it) }
        return Binder.get(environment).bind("enact", EnactProperties::class.java).orElseGet(::EnactProperties)
    }

    private fun environment(vararg properties: String): ConfigurableEnvironment =
        StandardEnvironment().apply {
            val values = properties.associate { it.substringBefore('=') to it.substringAfter('=') }
            propertySources.addFirst(MapPropertySource("test", values))
        }
}
