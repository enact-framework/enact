package io.enact.core.step

import org.apache.commons.logging.LogFactory

class StepRegistrar {
    companion object {
        private val log = LogFactory.getLog(StepRegistrar::class.java)
    }

    private val registry = mutableMapOf<String, Step<*, *>>()

    fun register(step: Step<*, *>) {
        if (registry.containsKey(step.name)) {
            log.error("Step ${step.name} already registered.")
            throw IllegalArgumentException("Step ${step.name} already registered.")
        }

        registry[step.name] = step
        log.trace("Registered step ${step.name}")
    }

    fun getStep(name: String): Step<*, *> =
        registry[name]
            ?: throw IllegalArgumentException("Step '$name' not found")
}
