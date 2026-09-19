package io.enact.core.configuration

import io.enact.core.annotation.Cached
import io.enact.core.cache.CacheSpec
import io.enact.core.retry.RetryableSpec
import io.enact.core.step.MethodAdapter
import io.enact.core.step.Step
import io.enact.core.step.StepRegistrar
import io.enact.core.step.StepSettings
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.EmbeddedValueResolverAware
import org.springframework.core.BridgeMethodResolver
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.format.annotation.DurationFormat
import org.springframework.format.datetime.standard.DurationFormatterUtils
import org.springframework.resilience.annotation.Retryable
import org.springframework.util.StringUtils
import org.springframework.util.StringValueResolver
import java.lang.reflect.Method
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier
import io.enact.core.annotation.Step as StepAnnotation

class StepDiscoveryBeanPostProcessor(
    private val registrar: StepRegistrar,
) : BeanPostProcessor,
    EmbeddedValueResolverAware {
    private lateinit var embeddedValueResolver: StringValueResolver

    override fun postProcessAfterInitialization(
        bean: Any,
        beanName: String,
    ): Any {
        if (bean is Step<*, *>) {
            registrar.register(bean)
            return bean
        }

        val targetClass = AopUtils.getTargetClass(bean)

        val (isEligible, method) = isEligibleType(bean, targetClass)
        if (isEligible) {
            val annotation = AnnotationUtils.findAnnotation(targetClass, StepAnnotation::class.java)!!
            val stepName = annotation.name.ifBlank { beanName }
            registerMethod(stepName, method!!, bean, annotation)
            return bean
        }

        targetClass.declaredMethods
            .filter { AnnotationUtils.findAnnotation(it, StepAnnotation::class.java) != null }
            .forEach { method ->
                val annotation = method.getAnnotation(StepAnnotation::class.java)
                registerMethod(extractStepName(method), method, bean, annotation)
            }

        return bean
    }

    private fun registerMethod(
        stepName: String,
        method: Method,
        bean: Any,
        annotation: StepAnnotation,
    ) {
        val stepAdapter =
            MethodAdapter(
                stepName,
                StepSettings(retry = toRetrySpec(annotation.retry), cache = toCacheSpec(annotation.cache)),
                bean,
                method,
                extractInputType(method),
                extractOutputType(method),
            )

        registrar.register(stepAdapter)
    }

    private fun toRetrySpec(retry: Retryable): RetryableSpec? =
        retry.takeIf { it.maxRetries > 0 }?.run {
            RetryableSpec(
                includes.map { it.java },
                excludes.map { it.java },
                parseLong(maxRetries, maxRetriesString),
                parseDuration(timeout, timeoutString, timeUnit),
                parseDuration(delay, delayString, timeUnit),
                parseDuration(jitter, jitterString, timeUnit),
                parseDouble(multiplier, multiplierString),
                parseDuration(maxDelay, maxDelayString, timeUnit),
            )
        }

    private fun toCacheSpec(cached: Cached): CacheSpec? =
        cached.name.takeIf { it.isNotBlank() }?.let { CacheSpec(it, cached.key.ifBlank { null }) }

    private fun isEligibleType(
        bean: Any,
        clazz: Class<*>,
    ): Pair<Boolean, Method?> {
        val annotationExists = AnnotationUtils.findAnnotation(clazz, StepAnnotation::class.java) != null

        val methodName =
            when (bean) {
                is Function<*, *> -> "apply"
                is Predicate<*> -> "test"
                is Consumer<*> -> "accept"
                is Supplier<*> -> "get"
                is Function0<*> -> "invoke"
                is Function1<*, *> -> "invoke"
                else -> null
            }

        val method =
            methodName?.let { name ->
                clazz.methods
                    .filter { it.name == name }
                    .map { BridgeMethodResolver.findBridgedMethod(it) }
                    .firstOrNull { !it.isBridge }
            }

        return (annotationExists && method != null) to method
    }

    private fun extractStepName(method: Method) =
        method
            .getAnnotation(StepAnnotation::class.java)
            .name
            .takeIf { it.isNotBlank() }
            ?: method.name

    private fun extractInputType(method: Method) =
        when (method.parameterCount) {
            0 -> Unit::class.java
            1 -> method.parameters[0].type
            else -> throw IllegalArgumentException("Method ${method.name} must have 0 to 1 parameter")
        }

    private fun extractOutputType(method: Method) = if (method.returnType == Void.TYPE) Unit::class.java else method.returnType

    private fun parseLong(
        value: Long,
        stringValue: String?,
    ): Long {
        var stringValue = stringValue
        if (StringUtils.hasText(stringValue)) {
            stringValue = embeddedValueResolver.resolveStringValue(stringValue!!)
            if (StringUtils.hasText(stringValue)) {
                return stringValue!!.toLong()
            }
        }
        return value
    }

    private fun parseDouble(
        value: Double,
        stringValue: String?,
    ): Double {
        var stringValue = stringValue
        if (StringUtils.hasText(stringValue)) {
            stringValue = embeddedValueResolver.resolveStringValue(stringValue!!)
            if (StringUtils.hasText(stringValue)) {
                return stringValue!!.toDouble()
            }
        }
        return value
    }

    private fun parseDuration(
        value: Long,
        stringValue: String?,
        timeUnit: TimeUnit,
    ): Duration {
        var stringValue = stringValue
        if (StringUtils.hasText(stringValue)) {
            stringValue = embeddedValueResolver.resolveStringValue(stringValue!!)
            if (StringUtils.hasText(stringValue)) {
                return toDuration(stringValue!!, timeUnit)
            }
        }
        return toDuration(value, timeUnit)
    }

    private fun toDuration(
        value: Long,
        timeUnit: TimeUnit,
    ): Duration = Duration.of(value, timeUnit.toChronoUnit())

    private fun toDuration(
        value: String,
        timeUnit: TimeUnit,
    ): Duration {
        val unit = DurationFormat.Unit.fromChronoUnit(timeUnit.toChronoUnit())
        return DurationFormatterUtils.detectAndParse(value, unit)
    }

    override fun setEmbeddedValueResolver(resolver: StringValueResolver) {
        this.embeddedValueResolver = resolver
    }
}
