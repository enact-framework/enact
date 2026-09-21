package io.enact.core.step

import org.springframework.core.ResolvableType

/** One input of a step: what a use case binds a value to. */
data class StepParameter(
    val name: String,
    val type: ResolvableType,
)
