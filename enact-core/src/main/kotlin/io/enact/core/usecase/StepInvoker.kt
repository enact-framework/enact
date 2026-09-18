package io.enact.core.usecase

import io.enact.core.step.Step
import io.enact.core.step.StepSettings
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.cache.interceptor.SimpleKey
import org.springframework.core.retry.RetryTemplate
import org.springframework.expression.Expression
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.SimpleEvaluationContext
import java.util.concurrent.Callable
import java.util.function.Supplier

/**
 * Executes a single step of a use case, applying the step's cache and retry settings.
 * A cache hit skips the step (and its retries) entirely.
 */
internal class StepInvoker(
    private val step: Step<Any?, Any?>,
    settings: StepSettings?,
    cacheManager: CacheManager?,
) {
    private val retryTemplate = settings?.retry?.let { RetryTemplate(it.toRetryPolicy()) }
    private val cache: Cache? =
        settings?.cache?.let { spec ->
            requireNotNull(cacheManager) {
                "Step '${step.name}' uses cache '${spec.name}' but no CacheManager bean is available."
            }
            requireNotNull(cacheManager.getCache(spec.name)) {
                "Step '${step.name}' uses cache '${spec.name}' which is not known to the CacheManager."
            }
        }
    private val keyExpression: Expression? = settings?.cache?.key?.let { parser.parseExpression(it) }

    fun invoke(input: Any?): Any? {
        val cache = cache ?: return execute(input)
        try {
            // Spring's cache stores null results itself (when the cache allows null values)
            @Suppress("UNCHECKED_CAST")
            return cache.get(cacheKey(input), Callable { execute(input) } as Callable<Any>)
        } catch (e: Cache.ValueRetrievalException) {
            throw e.cause ?: e
        }
    }

    private fun execute(input: Any?): Any? =
        if (retryTemplate != null) {
            retryTemplate.invoke(Supplier { step.execute(input) })
        } else {
            step.execute(input)
        }

    private fun cacheKey(input: Any?): Any {
        if (keyExpression == null) return input ?: SimpleKey.EMPTY

        val context =
            SimpleEvaluationContext
                .forReadOnlyDataBinding()
                .withInstanceMethods()
                .build()
                .apply { setVariable("input", input) }
        return keyExpression.getValue(context) ?: SimpleKey.EMPTY
    }

    private companion object {
        val parser = SpelExpressionParser()
    }
}
