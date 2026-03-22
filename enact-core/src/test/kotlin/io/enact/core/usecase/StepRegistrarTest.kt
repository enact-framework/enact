package io.enact.core.usecase

import io.enact.core.step.Step
import io.enact.core.step.StepRegistrar
import io.enact.core.step.StepSettings
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StepRegistrarTest {
    @Test
    fun `should register and retrieve a step`() {
        val registrar = StepRegistrar()
        val step = simpleStep("my-step")

        registrar.register(step)

        assert(registrar.getStep("my-step") === step)
    }

    @Test
    fun `should throw when registering a step with duplicate name`() {
        val registrar = StepRegistrar()
        registrar.register(simpleStep("duplicate"))

        assertThrows<IllegalArgumentException> {
            registrar.register(simpleStep("duplicate"))
        }.also {
            assert(it.message == "Step duplicate already registered.")
        }
    }

    @Test
    fun `should throw when getting a step that does not exist`() {
        val registrar = StepRegistrar()

        assertThrows<IllegalArgumentException> {
            registrar.getStep("nonexistent")
        }.also {
            assert(it.message == "Step 'nonexistent' not found")
        }
    }

    @Test
    fun `should register multiple steps with different names`() {
        val registrar = StepRegistrar()
        val step1 = simpleStep("step-1")
        val step2 = simpleStep("step-2")

        registrar.register(step1)
        registrar.register(step2)

        assert(registrar.getStep("step-1") === step1)
        assert(registrar.getStep("step-2") === step2)
    }

    private fun simpleStep(name: String) =
        object : Step.InOut<String, String> {
            override val name = name
            override var settings: StepSettings? = null

            override fun execute(input: String): String = input
        }
}
