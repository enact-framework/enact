package io.enact.core.usecase

import io.enact.core.observation.CacheStatus
import io.enact.core.observation.DefaultStepObservationConvention
import io.enact.core.observation.EnactObservationDocumentation
import io.enact.core.observation.EnactObservations
import io.enact.core.observation.StepObservationContext
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
 *
 * The whole invocation is observed, cache lookup and retries included, so the recorded duration is what the
 * use case waited for.
 */
internal class StepInvoker(
    private val step: Step<Any?, Any?>,
    settings: StepSettings?,
    cacheManager: CacheManager?,
    private val useCaseName: String,
    private val group: String,
    private val observations: EnactObservations,
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
        val observation =
            EnactObservationDocumentation.STEP.observation(
                observations.stepConvention,
                DefaultStepObservationConvention,
                { StepObservationContext(step.name, useCaseName, group) },
                observations.registry,
            )
        // Null while observability is off: the registry short-circuits before creating our context.
        val context = observation.context as? StepObservationContext

        return observation.observe(Supplier { invokeStep(input, context) })
    }

    private fun invokeStep(
        input: Any?,
        context: StepObservationContext?,
    ): Any? {
        val cache = cache ?: return execute(input)
        var miss = false
        try {
            // Spring's cache stores null results itself (when the cache allows null values)
            @Suppress("UNCHECKED_CAST")
            return cache.get(
                cacheKey(input),
                Callable {
                    miss = true
                    execute(input)
                } as Callable<Any>,
            )
        } catch (e: Cache.ValueRetrievalException) {
            throw e.cause ?: e
        } finally {
            context?.cacheStatus = if (miss) CacheStatus.MISS else CacheStatus.HIT
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
