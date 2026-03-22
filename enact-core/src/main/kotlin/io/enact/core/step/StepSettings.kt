package io.enact.core.step

import io.enact.core.retry.RetryableSpec

data class StepSettings(
    val retry: RetryableSpec? = null,
)
