package io.enact.core.observation

import io.micrometer.common.KeyValues
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationConvention

/** Where a step's result came from. */
enum class CacheStatus(
    val value: String,
) {
    /** The cache answered, so the step did not run. */
    HIT("hit"),

    /** The cache was missed, so the step ran and its result was stored. */
    MISS("miss"),

    /** The step has no cache configured. */
    NONE("none"),
}

/** Context of a [EnactObservationDocumentation.STEP] observation. */
class StepObservationContext(
    /** Name of the step. */
    val stepName: String,
    /** Name of the use case executing it. */
    val useCaseName: String,
    /** Group of the use case executing it. */
    val group: String,
) : Observation.Context() {
    /**
     * Set once the invocation is over, because only then is it known.
     *
     * The convention runs when the observation starts and again when it stops, and the value recorded on stop
     * wins, so the `Timer` carries the real status. The `<name>.active` `LongTaskTimer` is built on start, so
     * it always reads `none`. That is fine: a step still running has no cache status yet.
     */
    var cacheStatus: CacheStatus = CacheStatus.NONE
}

/**
 * Names and tags of the step observation. Publish a bean of this type to replace
 * [DefaultStepObservationConvention]; it applies to every step of every use case.
 */
interface StepObservationConvention : ObservationConvention<StepObservationContext> {
    override fun supportsContext(context: Observation.Context): Boolean = context is StepObservationContext
}

/**
 * Records the step name, its use case and group, and where the result came from; the span is named after the
 * step.
 */
object DefaultStepObservationConvention : StepObservationConvention {
    override fun getName(): String = "enact.step"

    override fun getContextualName(context: StepObservationContext): String = context.stepName

    override fun getLowCardinalityKeyValues(context: StepObservationContext): KeyValues =
        KeyValues.of(
            StepKeyNames.NAME.withValue(context.stepName),
            StepKeyNames.USE_CASE_NAME.withValue(context.useCaseName),
            StepKeyNames.GROUP.withValue(context.group),
            StepKeyNames.CACHE.withValue(context.cacheStatus.value),
        )
}
