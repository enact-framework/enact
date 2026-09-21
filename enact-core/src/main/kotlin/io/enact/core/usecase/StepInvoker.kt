package io.enact.core.usecase

import io.enact.core.observation.CacheStatus
import io.enact.core.observation.DefaultStepObservationConvention
import io.enact.core.observation.EnactObservationDocumentation
import io.enact.core.observation.EnactObservations
import io.enact.core.observation.StepObservationContext
import io.enact.core.step.MethodAdapter
import io.enact.core.step.Step
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
    private val node: ResolvedNode,
    cacheManager: CacheManager?,
    private val useCaseName: String,
    private val group: String,
    private val observations: EnactObservations,
) {
    private val step = node.node.step
    private val settings = node.node.settings
    private val retryTemplate = settings?.retry?.let { RetryTemplate(it.toRetryPolicy()) }
    private val cache: Cache? =
        settings?.cache?.let { spec ->
            requireNotNull(cacheManager) {
                "Step '${node.id}' uses cache '${spec.name}' but no CacheManager bean is available."
            }
            requireNotNull(cacheManager.getCache(spec.name)) {
                "Step '${node.id}' uses cache '${spec.name}' which is not known to the CacheManager."
            }
        }
    private val keyExpression: Expression? = settings?.cache?.key?.let { parser.parseExpression(it) }
    private val condition: Expression? = node.node.condition?.let { parser.parseExpression(it) }

    /**
     * A step whose condition does not hold is not run, and yields what the graph resolved for it: one of its
     * own inputs, a declared value, or nothing when no other step reads it. It is not observed either, since
     * nothing was executed to measure.
     */
    fun invoke(arguments: List<Any?>): Any? {
        if (condition != null && !holds(arguments)) return skipped(arguments)

        val observation =
            EnactObservationDocumentation.STEP.observation(
                observations.stepConvention,
                DefaultStepObservationConvention,
                { StepObservationContext(node.name, useCaseName, group) },
                observations.registry,
            )
        // Null while observability is off: the registry short-circuits before creating our context.
        val context = observation.context as? StepObservationContext

        return observation.observe(Supplier { invokeStep(arguments, context) })
    }

    private fun holds(arguments: List<Any?>): Boolean =
        condition!!.getValue(evaluationContext(arguments), Boolean::class.java)
            ?: throw IllegalStateException(
                "Condition \"${node.node.condition}\" of step '${node.id}' did not evaluate to a boolean.",
            )

    private fun skipped(arguments: List<Any?>): Any? =
        when (val fallback = node.fallback) {
            null -> null
            is Fallback.Value -> fallback.value
            is Fallback.Parameter -> arguments[node.parameters.indexOfFirst { it.name == fallback.name }]
        }

    private fun invokeStep(
        arguments: List<Any?>,
        context: StepObservationContext?,
    ): Any? {
        val cache = cache ?: return execute(arguments)
        var miss = false
        try {
            // Spring's cache stores null results itself (when the cache allows null values)
            @Suppress("UNCHECKED_CAST")
            return cache.get(
                cacheKey(arguments),
                Callable {
                    miss = true
                    execute(arguments)
                } as Callable<Any>,
            )
        } catch (e: Cache.ValueRetrievalException) {
            throw e.cause ?: e
        } finally {
            context?.cacheStatus = if (miss) CacheStatus.MISS else CacheStatus.HIT
        }
    }

    private fun execute(arguments: List<Any?>): Any? =
        if (retryTemplate != null) {
            retryTemplate.invoke(Supplier { call(arguments) })
        } else {
            call(arguments)
        }

    @Suppress("UNCHECKED_CAST")
    private fun call(arguments: List<Any?>): Any? =
        if (step is MethodAdapter) {
            step.invoke(arguments)
        } else {
            (step as Step<Any?, Any?>).execute(arguments.singleOrNull() ?: Unit)
        }

    /** Without a key, a step taking one argument is keyed by it, and one taking several by all of them. */
    private fun cacheKey(arguments: List<Any?>): Any {
        if (keyExpression == null) {
            return arguments.singleOrNull() ?: SimpleKey(*arguments.toTypedArray())
        }
        return keyExpression.getValue(evaluationContext(arguments)) ?: SimpleKey.EMPTY
    }

    /**
     * Each argument is readable by its parameter name, and `#input` names the only one of a step taking a
     * single parameter, which is what a cache key declared on a step can rely on.
     */
    private fun evaluationContext(arguments: List<Any?>) =
        SimpleEvaluationContext
            .forReadOnlyDataBinding()
            .withInstanceMethods()
            .build()
            .apply {
                node.parameters.forEachIndexed { index, parameter -> setVariable(parameter.name, arguments[index]) }
                arguments.singleOrNull()?.let { setVariable("input", it) }
            }

    private companion object {
        val parser = SpelExpressionParser()
    }
}
