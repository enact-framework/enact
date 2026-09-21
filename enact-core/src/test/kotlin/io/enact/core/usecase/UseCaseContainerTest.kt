package io.enact.core.usecase

import io.enact.core.step.Step
import io.enact.core.step.StepSettings
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UseCaseContainerTest {
    @Test
    fun `should fail when no steps are provided`() {
        assertThrows<IllegalArgumentException> {
            RuntimeUseCaseContainer<String, Int>(
                name = "test",
                description = "test",
                nodes = emptyList(),
            )
        }.also {
            assert(it.message == "Must specify at least one step.")
        }
    }

    @Test
    fun `should succeed with a single step`() {
        RuntimeUseCaseContainer<String, Int>(
            name = "test",
            description = "test",
            nodes = nodes(stringToInt()),
        )
    }

    @Test
    fun `should succeed with two compatible chained steps`() {
        RuntimeUseCaseContainer<String, Int>(
            name = "test",
            description = "test",
            nodes = nodes(stringToLong(), longToInt()),
        )
    }

    @Test
    fun `should fail when consecutive steps have incompatible types`() {
        assertThrows<IllegalArgumentException> {
            RuntimeUseCaseContainer<String, String>(
                name = "test",
                description = "test",
                nodes = nodes(stringToLong(), intToString()),
            )
        }
    }

    @Test
    fun `should fail on first type mismatch in chain`() {
        assertThrows<IllegalArgumentException> {
            RuntimeUseCaseContainer<String, Int>(
                name = "test",
                description = "test",
                nodes = nodes(stringToLong(), stringToInt()),
            )
        }
    }

    @Test
    fun `should succeed with InOutStep followed by InStep`() {
        RuntimeUseCaseContainer<String, Unit>(
            name = "test",
            description = "test",
            nodes = nodes(stringToLong(), longInStep()),
        )
    }

    @Test
    fun `should succeed with OutStep followed by InOutStep`() {
        RuntimeUseCaseContainer<Unit, Int>(
            name = "test",
            description = "test",
            nodes = nodes(longOutStep(), longToInt()),
        )
    }

    @Test
    fun `should succeed with OutStep into InOutStep into InStep`() {
        RuntimeUseCaseContainer<Unit, Unit>(
            name = "test",
            description = "test",
            nodes = nodes(stringOutStep(), stringToLong(), longInStep()),
        )
    }

    @Test
    fun `should fail when OutStep output does not match next step input`() {
        assertThrows<IllegalArgumentException> {
            RuntimeUseCaseContainer<Unit, Int>(
                name = "test",
                description = "test",
                nodes = nodes(stringOutStep(), longToInt()),
            )
        }
    }

    @Test
    fun `should fail when InOutStep output does not match InStep input`() {
        assertThrows<IllegalArgumentException> {
            RuntimeUseCaseContainer<String, Unit>(
                name = "test",
                description = "test",
                nodes = nodes(stringToLong(), stringInStep()),
            )
        }
    }

    @Test
    fun `should fail when steps agree on the raw type but not on its generics`() {
        assertThrows<IllegalArgumentException> {
            RuntimeUseCaseContainer<Unit, Int>(
                name = "test",
                description = "test",
                nodes = nodes(listOfStringOutStep(), listOfLongToInt()),
            )
        }.also {
            assert(it.message!!.contains("java.lang.String")) { "Expected the generic in the message: ${it.message}" }
        }
    }

    @Test
    fun `should succeed when a step produces a subtype of the next step's input`() {
        RuntimeUseCaseContainer<Unit, Int>(
            name = "test",
            description = "test",
            nodes = nodes(arrayListOfLongOutStep(), listOfLongToInt()),
        )
    }

    // -- InOutStep fixtures --

    private fun stringToInt() =
        object : Step.InOut<String, Int> {
            override val name = "string-to-int"
            override var settings: StepSettings? = null

            override fun execute(input: String): Int = input.toInt()
        }

    private fun stringToLong() =
        object : Step.InOut<String, Long> {
            override val name = "string-to-long"
            override var settings: StepSettings? = null

            override fun execute(input: String): Long = input.toLong()
        }

    private fun longToInt() =
        object : Step.InOut<Long, Int> {
            override val name = "long-to-int"
            override var settings: StepSettings? = null

            override fun execute(input: Long): Int = input.toInt()
        }

    private fun intToString() =
        object : Step.InOut<Int, String> {
            override val name = "int-to-string"
            override var settings: StepSettings? = null

            override fun execute(input: Int): String = input.toString()
        }

    // -- InStep fixtures --

    private fun stringInStep() =
        object : Step.In<String> {
            override val name = "string-in-step"
            override var settings: StepSettings? = null

            override fun execute(input: String) {}
        }

    private fun longInStep() =
        object : Step.In<Long> {
            override val name = "long-in-step"
            override var settings: StepSettings? = null

            override fun execute(input: Long) {}
        }

    // -- OutStep fixtures --

    private fun stringOutStep() =
        object : Step.Out<String> {
            override val name = "string-out-step"
            override var settings: StepSettings? = null

            override fun execute(input: Unit): String = "out"
        }

    private fun longOutStep() =
        object : Step.Out<Long> {
            override val name = "long-out-step"
            override var settings: StepSettings? = null

            override fun execute(input: Unit): Long = 0L
        }

    // -- generic fixtures --

    private fun listOfStringOutStep() =
        object : Step.Out<List<String>> {
            override val name = "list-of-string-out-step"
            override var settings: StepSettings? = null

            override fun execute(input: Unit): List<String> = emptyList()
        }

    private fun arrayListOfLongOutStep() =
        object : Step.Out<ArrayList<Long>> {
            override val name = "array-list-of-long-out-step"
            override var settings: StepSettings? = null

            override fun execute(input: Unit): ArrayList<Long> = ArrayList()
        }

    private fun listOfLongToInt() =
        object : Step.InOut<List<Long>, Int> {
            override val name = "list-of-long-to-int"
            override var settings: StepSettings? = null

            override fun execute(input: List<Long>): Int = input.size
        }
}
