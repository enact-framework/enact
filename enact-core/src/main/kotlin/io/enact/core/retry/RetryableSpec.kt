package io.enact.core.retry

import org.springframework.core.retry.RetryPolicy
import java.time.Duration

data class RetryableSpec(
    val includes: List<Class<out Throwable>> = emptyList(),
    val excludes: List<Class<out Throwable>> = emptyList(),
    val maxRetries: Long = RetryPolicy.Builder.DEFAULT_MAX_RETRIES,
    val timeout: Duration = Duration.ZERO,
    val delay: Duration = Duration.ofMillis(RetryPolicy.Builder.DEFAULT_DELAY),
    val jitter: Duration = Duration.ZERO,
    val multiplier: Double = RetryPolicy.Builder.DEFAULT_MULTIPLIER,
    val maxDelay: Duration = Duration.ofMillis(RetryPolicy.Builder.DEFAULT_MAX_DELAY),
) {
    fun toRetryPolicy(): RetryPolicy =
        RetryPolicy
            .builder()
            .maxRetries(maxRetries)
            .delay(delay)
            .jitter(jitter)
            .multiplier(multiplier)
            .maxDelay(maxDelay)
            .timeout(timeout)
            .apply {
                if (includes.isNotEmpty()) includes(*includes.toTypedArray())
                if (excludes.isNotEmpty()) excludes(*excludes.toTypedArray())
            }.build()
}
