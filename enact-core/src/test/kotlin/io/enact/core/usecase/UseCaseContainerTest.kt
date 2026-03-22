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
                steps = emptyList(),
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
            steps = listOf(stringToInt()),
        )
    }

    @Test
    fun `should succeed with two compatible chained steps`() {
        RuntimeUseCaseContainer<String, Int>(
            name = "test",
            description = "test",
            steps = listOf(stringToLong(), longToInt()),
        )
    }

    @Test
    fun `should fail when consecutive steps have incompatible types`() {
        assertThrows<IllegalArgumentException> {
            RuntimeUseCaseContainer<String, String>(
                name = "test",
                description = "test",
                steps = listOf(stringToLong(), intToString()),
            )
        }
    }

    @Test
    fun `should fail on first type mismatch in chain`() {
        assertThrows<IllegalArgumentException> {
            RuntimeUseCaseContainer<String, Int>(
                name = "test",
                description = "test",
                steps = listOf(stringToLong(), stringToInt()),
            )
        }
    }

    @Test
    fun `should succeed with InOutStep followed by InStep`() {
        RuntimeUseCaseContainer<String, Unit>(
            name = "test",
            description = "test",
            steps = listOf(stringToLong(), longInStep()),
        )
    }

    @Test
    fun `should succeed with OutStep followed by InOutStep`() {
        RuntimeUseCaseContainer<Unit, Int>(
            name = "test",
            description = "test",
            steps = listOf(longOutStep(), longToInt()),
        )
    }

    @Test
    fun `should succeed with OutStep into InOutStep into InStep`() {
        RuntimeUseCaseContainer<Unit, Unit>(
            name = "test",
            description = "test",
            steps = listOf(stringOutStep(), stringToLong(), longInStep()),
        )
    }

    @Test
    fun `should fail when OutStep output does not match next step input`() {
        assertThrows<IllegalArgumentException> {
            RuntimeUseCaseContainer<Unit, Int>(
                name = "test",
                description = "test",
                steps = listOf(stringOutStep(), longToInt()),
            )
        }
    }

    @Test
    fun `should fail when InOutStep output does not match InStep input`() {
        assertThrows<IllegalArgumentException> {
            RuntimeUseCaseContainer<String, Unit>(
                name = "test",
                description = "test",
                steps = listOf(stringToLong(), stringInStep()),
            )
        }
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
}
