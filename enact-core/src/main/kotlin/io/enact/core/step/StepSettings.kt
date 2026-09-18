package io.enact.core.step

import io.enact.core.cache.CacheSpec
import io.enact.core.retry.RetryableSpec

data class StepSettings(
    val retry: RetryableSpec? = null,
    val cache: CacheSpec? = null,
) {
    /** Returns these settings with unset values taken from [defaults]. */
    fun orElse(defaults: StepSettings?): StepSettings =
        StepSettings(
            retry = retry ?: defaults?.retry,
            cache = cache ?: defaults?.cache,
        )
}
