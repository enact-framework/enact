package io.enact.core.configuration

import io.enact.core.step.MethodAdapter
import io.enact.core.step.StepRegistrar
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext

class StepDefinitionTest {
    @Test
    fun `should component scan @StepDefinition classes and register their steps`() {
        scannedContext().use { context ->
            val stepRegistrar = context.getBean(StepRegistrar::class.java)

            assert(stepRegistrar.getStep("scannedStep") is MethodAdapter)
            assert(stepRegistrar.getStep("scanned-functional") is MethodAdapter)
        }
    }

    @Test
    fun `should use @StepDefinition value as bean name`() {
        scannedContext().use { context ->
            assert(context.containsBeanDefinition("functionalSteps"))
            assert(context.containsBeanDefinition("scannedSteps"))
        }
    }

    private fun scannedContext() =
        AnnotationConfigApplicationContext().apply {
            register(EnactConfiguration::class.java)
            scan("io.enact.core.configuration.stepdefinitions")
            refresh()
        }
}
