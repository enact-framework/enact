package io.enact.core.retry

import org.springframework.core.retry.RetryPolicy
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

data class RetryableSpec(
    val includes: List<KClass<out Throwable>> = emptyList(),
    val excludes: List<KClass<out Throwable>> = emptyList(),
    val maxRetries: Long = 3,
    val timeout: Duration = Duration.ZERO,
    val delay: Duration = Duration.ofMillis(1000),
    val jitter: Duration = Duration.ZERO,
    val multiplier: Double = 1.0,
    val maxDelay: Duration = Duration.ZERO,
    val timeUnit: TimeUnit = TimeUnit.MILLISECONDS,
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
                if (includes.isNotEmpty()) includes(*includes.map { it.java }.toTypedArray())
                if (excludes.isNotEmpty()) excludes(*excludes.map { it.java }.toTypedArray())
            }.build()
}
