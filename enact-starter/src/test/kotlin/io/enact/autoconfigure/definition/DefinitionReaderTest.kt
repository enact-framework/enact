package io.enact.autoconfigure.definition

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class DefinitionReaderTest {
    @TempDir
    lateinit var folder: Path

    private val reader = DefinitionReader()

    @Test
    fun `should read use cases and groups from every file of a folder`() {
        write(
            "orders.yaml",
            """
            use-cases:
              createOrder:
                description: Creates an order
                steps:
                  - step: saveOrder
            """,
        )
        write(
            "groups.yaml",
            """
            groups:
              admin:
                filters: [bearerAuth, requireAdmin]
            """,
        )

        val definitions = reader.read(listOf(folder()))

        assertThat(definitions.useCases).containsOnlyKeys("createOrder")
        assertThat(
            definitions.useCases
                .getValue("createOrder")
                .steps
                .map { it.step },
        ).containsExactly("saveOrder")
        assertThat(definitions.groups.getValue("admin").filters).containsExactly("bearerAuth", "requireAdmin")
    }

    @Test
    fun `should keep a use case name as it is written`() {
        write("orders.yaml", "use-cases:\n  createOrder:\n    steps:\n      - step: saveOrder")

        assertThat(reader.read(listOf(folder())).useCases).containsOnlyKeys("createOrder")
    }

    @Test
    fun `should name the file a use case comes from`() {
        write("orders.yaml", "use-cases:\n  createOrder:\n    steps:\n      - step: saveOrder")

        assertThat(reader.read(listOf(folder())).sourceOf("createOrder")).contains("orders.yaml")
    }

    @Test
    fun `should report a use case declared in two files`() {
        write("a.yaml", "use-cases:\n  createOrder:\n    steps:\n      - step: saveOrder")
        write("b.yaml", "use-cases:\n  createOrder:\n    steps:\n      - step: other")

        assertThatThrownBy { reader.read(listOf(folder())) }
            .hasMessageContaining("Use case 'createOrder' is declared twice")
            .hasMessageContaining("a.yaml")
            .hasMessageContaining("b.yaml")
    }

    @Test
    fun `should allow a use case and a group to share a name`() {
        write(
            "orders.yaml",
            """
            use-cases:
              traced:
                group: traced
                steps:
                  - step: traceOrder
            groups:
              traced:
                filters: [traceA]
            """,
        )

        val definitions = reader.read(listOf(folder()))

        assertThat(definitions.useCases).containsOnlyKeys("traced")
        assertThat(definitions.groups).containsOnlyKeys("traced")
    }

    @Test
    fun `should report a group declared in two files`() {
        write("a.yaml", "groups:\n  admin:\n    filters: [one]")
        write("b.yaml", "groups:\n  admin:\n    filters: [two]")

        assertThatThrownBy { reader.read(listOf(folder())) }
            .hasMessageContaining("Group 'admin' is declared twice")
            .hasMessageContaining("a.yaml")
            .hasMessageContaining("b.yaml")
    }

    @Test
    fun `should read every document of a file`() {
        write(
            "orders.yaml",
            """
            use-cases:
              first:
                steps:
                  - step: one
            ---
            use-cases:
              second:
                steps:
                  - step: two
            """,
        )

        assertThat(reader.read(listOf(folder())).useCases).containsOnlyKeys("first", "second")
    }

    @Test
    fun `should accept a section written without entries`() {
        write("orders.yaml", "use-cases:\ngroups:")

        val definitions = reader.read(listOf(folder()))

        assertThat(definitions.useCases).isEmpty()
        assertThat(definitions.groups).isEmpty()
    }

    @Test
    fun `should reject a root key that is not a definition`() {
        write("orders.yaml", "server:\n  port: 8080")

        assertThatThrownBy { reader.read(listOf(folder())) }
            .hasMessageContaining("orders.yaml")
            .hasMessageContaining("declares 'use-cases' and 'groups' at its root")
    }

    @Test
    fun `should reject an unknown key inside a use case and name the line`() {
        write(
            "orders.yaml",
            """
            use-cases:
              createOrder:
                stepz:
                  - step: saveOrder
            """,
        )

        assertThatThrownBy { reader.read(listOf(folder())) }
            .hasMessageContaining("stepz")
            .hasMessageContaining("line 4")
    }

    @Test
    fun `should fail when a location matches no file`() {
        assertThatThrownBy { reader.read(listOf("classpath:no-such-folder/")) }
            .hasMessageContaining("No use case definition file found")
    }

    @Test
    fun `should allow an optional location to be missing`() {
        assertThat(reader.read(listOf("optional:classpath:no-such-folder/")).useCases).isEmpty()
    }

    @Test
    fun `should reject a location matching a file that is not YAML`() {
        write("orders.txt", "use-cases: {}")

        assertThatThrownBy { reader.read(listOf("file:${folder.resolve("orders.txt")}")) }
            .hasMessageContaining("must be YAML files")
    }

    @Test
    fun `should read the same file once when several locations match it`() {
        write("orders.yaml", "use-cases:\n  createOrder:\n    steps:\n      - step: saveOrder")

        val definitions = reader.read(listOf(folder(), "file:${folder.resolve("orders.yaml")}"))

        assertThat(definitions.useCases).containsOnlyKeys("createOrder")
    }

    private fun folder() = "file:${folder.toAbsolutePath()}/"

    private fun write(
        name: String,
        content: String,
    ) = folder.resolve(name).writeText(content.trimIndent())
}
