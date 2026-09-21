package io.enact.core.observation

import io.micrometer.common.docs.KeyName
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationConvention
import io.micrometer.observation.docs.ObservationDocumentation

/**
 * The observations Enact records. Each one drives both a meter (a `Timer` named after the observation) and,
 * when a tracer is on the classpath, a span.
 *
 * No observation declares an `error` tag: Micrometer adds one itself, holding the exception's simple name or
 * `none`.
 */
enum class EnactObservationDocumentation : ObservationDocumentation {
    /** Execution of a use case, from the first step's input to the last step's output. */
    USE_CASE {
        override fun getDefaultConvention(): Class<out ObservationConvention<out Observation.Context>> =
            DefaultUseCaseObservationConvention::class.java

        override fun getLowCardinalityKeyNames(): Array<KeyName> = UseCaseKeyNames.entries.toTypedArray()
    },

    /** Execution of a single step, covering its cache lookup and every retry attempt. */
    STEP {
        override fun getDefaultConvention(): Class<out ObservationConvention<out Observation.Context>> =
            DefaultStepObservationConvention::class.java

        override fun getLowCardinalityKeyNames(): Array<KeyName> = StepKeyNames.entries.toTypedArray()
    },
}

/** Tags of the [EnactObservationDocumentation.USE_CASE] observation. */
enum class UseCaseKeyNames(
    private val key: String,
) : KeyName {
    /** Name of the use case, which is also its bean name. */
    NAME("enact.use.case.name"),

    /** Group the use case belongs to, `default` when it declares none. */
    GROUP("enact.group"),
    ;

    override fun asString(): String = key
}

/** Tags of the [EnactObservationDocumentation.STEP] observation. */
enum class StepKeyNames(
    private val key: String,
) : KeyName {
    /** Name of the step. */
    NAME("enact.step.name"),

    /** Name of the use case executing the step; the same step may behave differently in another one. */
    USE_CASE_NAME("enact.use.case.name"),

    /** Group of the use case executing the step. */
    GROUP("enact.group"),

    /** Whether the step's result came from its cache: `hit`, `miss`, or `none` when it has no cache. */
    CACHE("enact.cache"),
    ;

    override fun asString(): String = key
}
