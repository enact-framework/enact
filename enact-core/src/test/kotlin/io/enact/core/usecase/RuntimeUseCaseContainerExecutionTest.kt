package io.enact.core.usecase

import io.enact.core.step.Step
import io.enact.core.step.StepSettings
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RuntimeUseCaseContainerExecutionTest {
    @Test
    fun `should execute single step and return result`() {
        val useCase =
            RuntimeUseCaseContainer<String, Int>(
                name = "parse-int",
                description = "Parses string to int",
                steps = listOf(stringToIntStep()),
            )

        val result = useCase.execute("42")

        assert(result == 42)
    }

    @Test
    fun `should chain multiple steps passing output to next input`() {
        val useCase =
            RuntimeUseCaseContainer<String, String>(
                name = "transform",
                description = "Transforms string",
                steps = listOf(stringToIntStep(), intToStringStep()),
            )

        val result = useCase.execute("42")

        assert(result == "42")
    }

    @Test
    fun `should execute three steps in sequence`() {
        val appendStep =
            object : Step.InOut<String, String> {
                override val name = "append"
                override var settings: StepSettings? = null

                override fun execute(input: String): String = "$input-processed"
            }
        val lengthStep =
            object : Step.InOut<String, Int> {
                override val name = "length"
                override var settings: StepSettings? = null

                override fun execute(input: String): Int = input.length
            }
        val doubleStep =
            object : Step.InOut<Int, Int> {
                override val name = "double"
                override var settings: StepSettings? = null

                override fun execute(input: Int): Int = input * 2
            }

        val useCase =
            RuntimeUseCaseContainer<String, Int>(
                name = "pipeline",
                description = "Three step pipeline",
                steps = listOf(appendStep, lengthStep, doubleStep),
            )

        val result = useCase.execute("hi")

        // "hi" -> "hi-processed" (12 chars) -> 24
        assert(result == 24)
    }

    @Test
    fun `should propagate exception from step execution`() {
        val failingStep =
            object : Step.InOut<String, String> {
                override val name = "failing"
                override var settings: StepSettings? = null

                override fun execute(input: String): String = throw RuntimeException("step failed")
            }

        val useCase =
            RuntimeUseCaseContainer<String, String>(
                name = "failing-uc",
                description = "Fails",
                steps = listOf(failingStep),
            )

        assertThrows<RuntimeException> {
            useCase.execute("input")
        }.also {
            assert(it.message == "step failed")
        }
    }

    @Test
    fun `should pass null through the chain when a step returns null`() {
        val nullStep =
            object : Step.InOut<String, String?> {
                override val name = "null-step"
                override var settings: StepSettings? = null

                override fun execute(input: String): String? = null
            }

        val useCase =
            RuntimeUseCaseContainer<String, String?>(
                name = "null-uc",
                description = "Returns null",
                steps = listOf(nullStep),
            )

        val result = useCase.execute("input")

        assert(result == null)
    }

    private fun stringToIntStep() =
        object : Step.InOut<String, Int> {
            override val name = "string-to-int"
            override var settings: StepSettings? = null

            override fun execute(input: String): Int = input.toInt()
        }

    private fun intToStringStep() =
        object : Step.InOut<Int, String> {
            override val name = "int-to-string"
            override var settings: StepSettings? = null

            override fun execute(input: Int): String = input.toString()
        }
}
