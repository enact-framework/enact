package io.enact.core.annotation

import org.springframework.resilience.annotation.Retryable

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Step(
    val name: String = "",
    val retry: Retryable = Retryable(maxRetries = 0),
)
