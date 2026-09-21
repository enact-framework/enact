package io.enact.core.usecase

import io.enact.core.step.MethodAdapter
import io.enact.core.step.Step
import io.enact.core.step.StepSettings
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UseCaseGraphTest {
    @Test
    fun `should feed the use case input to every step that reads it`() {
        val useCase =
            container(
                StepNode("length", length()),
                StepNode("shout", shout(), bindings = mapOf("input" to Binding.Input)),
                StepNode("combine", combine(), bindings = mapOf("text" to Binding.Output("shout"), "count" to Binding.Output("length"))),
            )

        assert(useCase.execute("hi") == "HI!2") { "Got ${useCase.execute("hi")}" }
    }

    @Test
    fun `should let a step read a step other than the one before it`() {
        val useCase =
            container(
                StepNode("shout", shout()),
                StepNode("length", length(), bindings = mapOf("input" to Binding.Input)),
                StepNode("combine", combine(), bindings = mapOf("text" to Binding.Output("shout"), "count" to Binding.Output("length"))),
            )

        assert(useCase.execute("hi") == "HI!2")
    }

    @Test
    fun `should run the same step twice under different ids`() {
        val useCase =
            container(
                StepNode("first", shout()),
                StepNode("second", shout(), bindings = mapOf("input" to Binding.Output("first"))),
            )

        assert(useCase.execute("hi") == "HI!!")
    }

    @Test
    fun `should take the use case input and output types from the graph`() {
        val useCase =
            container(
                StepNode("length", length()),
                StepNode("shout", shout(), bindings = mapOf("input" to Binding.Input)),
                StepNode("combine", combine(), bindings = mapOf("text" to Binding.Output("shout"), "count" to Binding.Output("length"))),
            )

        assert(useCase.inputType == String::class.java) { "Got ${useCase.inputType}" }
        assert(useCase.outputType == String::class.java) { "Got ${useCase.outputType}" }
    }

    @Test
    fun `should reject a step id declared twice`() {
        assertThrows<IllegalArgumentException> {
            container(StepNode("same", shout()), StepNode("same", shout()))
        }.also { assert(it.message!!.contains("declares the step id 'same' twice")) { it.message!! } }
    }

    @Test
    fun `should reject a binding to a step declared after it`() {
        assertThrows<IllegalArgumentException> {
            container(
                StepNode("shout", shout(), bindings = mapOf("input" to Binding.Output("later"))),
                StepNode("later", shout(), bindings = mapOf("input" to Binding.Input)),
            )
        }.also { assert(it.message!!.contains("not a step declared before it")) { it.message!! } }
    }

    @Test
    fun `should reject a binding to an unknown parameter`() {
        assertThrows<IllegalArgumentException> {
            container(StepNode("shout", shout(), bindings = mapOf("nope" to Binding.Input)))
        }.also { assert(it.message!!.contains("which is not one of its parameters")) { it.message!! } }
    }

    @Test
    fun `should reject a step taking several parameters without bindings`() {
        assertThrows<IllegalArgumentException> {
            container(StepNode("shout", shout()), StepNode("combine", combine()))
        }.also { assert(it.message!!.contains("must be bound with 'in'")) { it.message!! } }
    }

    @Test
    fun `should reject a binding whose producer type does not fit the parameter`() {
        assertThrows<IllegalArgumentException> {
            container(
                StepNode("length", length()),
                StepNode("combine", combine(), bindings = mapOf("text" to Binding.Output("length"), "count" to Binding.Output("length"))),
            )
        }.also { assert(it.message!!.contains("but the parameter expects")) { it.message!! } }
    }

    @Test
    fun `should reject two steps whose output nothing reads`() {
        assertThrows<IllegalArgumentException> {
            container(
                StepNode("shout", shout()),
                StepNode("length", length(), bindings = mapOf("input" to Binding.Input)),
            )
        }.also { assert(it.message!!.contains("whose output nothing reads")) { it.message!! } }
    }

    @Test
    fun `should return the named output when several steps are terminal`() {
        val useCase =
            container(
                StepNode("shout", shout()),
                StepNode("length", length(), bindings = mapOf("input" to Binding.Input)),
                output = "shout",
            )

        assert(useCase.execute("hi") == "HI!")
    }

    @Test
    fun `should reject an output naming a step that does not exist`() {
        assertThrows<IllegalArgumentException> {
            container(StepNode("shout", shout()), output = "nope")
        }.also { assert(it.message!!.contains("which is not one of its steps")) { it.message!! } }
    }

    @Test
    fun `should reject steps reading the use case input as different types`() {
        assertThrows<IllegalArgumentException> {
            container(
                StepNode("length", length()),
                StepNode("double", doubleIt(), bindings = mapOf("input" to Binding.Input)),
                StepNode("combine", combine(), bindings = mapOf("text" to Binding.Output("double"), "count" to Binding.Output("length"))),
            )
        }.also { assert(it.message!!.contains("must expect the same type")) { it.message!! } }
    }

    private fun container(
        vararg nodes: StepNode,
        output: String? = null,
    ) = RuntimeUseCaseContainer<String, String>("test", "test", nodes.toList(), output)

    private fun shout() =
        object : Step.InOut<String, String> {
            override val name = "shout"
            override var settings: StepSettings? = null

            override fun execute(input: String): String = "${input.uppercase()}!"
        }

    private fun length() =
        object : Step.InOut<String, Int> {
            override val name = "length"
            override var settings: StepSettings? = null

            override fun execute(input: String): Int = input.length
        }

    private fun doubleIt() =
        object : Step.InOut<Int, String> {
            override val name = "double"
            override var settings: StepSettings? = null

            override fun execute(input: Int): String = "${input * 2}"
        }

    /** Only a step written as an annotated method can take several parameters. */
    private fun combine() =
        MethodAdapter(
            "combine",
            null,
            Combiner(),
            Combiner::class.java.getMethod("combine", String::class.java, Int::class.java),
        )

    class Combiner {
        fun combine(
            text: String,
            count: Int,
        ): String = "$text$count"
    }
}
