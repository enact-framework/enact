package io.enact.core.usecase

import io.enact.core.step.Step

/** A linear chain: each step reads the one before it, keyed by the step's own name. */
fun nodes(vararg steps: Step<*, *>): List<StepNode> = steps.map { StepNode(it.name, it) }
