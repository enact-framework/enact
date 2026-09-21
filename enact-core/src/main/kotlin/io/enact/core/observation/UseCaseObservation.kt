package io.enact.core.observation

import io.micrometer.common.KeyValues
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationConvention

/** Context of a [EnactObservationDocumentation.USE_CASE] observation. */
class UseCaseObservationContext(
    /** Name of the use case, which is also its bean name. */
    val useCaseName: String,
    /** Group the use case belongs to, `default` when it declares none. */
    val group: String,
) : Observation.Context()

/**
 * Names and tags of the use case observation. Publish a bean of this type to replace
 * [DefaultUseCaseObservationConvention]; it applies to every use case, so branch on
 * [UseCaseObservationContext.useCaseName] to treat one differently.
 */
interface UseCaseObservationConvention : ObservationConvention<UseCaseObservationContext> {
    override fun supportsContext(context: Observation.Context): Boolean = context is UseCaseObservationContext
}

/** Records the use case name and its group; the span is named after the use case. */
object DefaultUseCaseObservationConvention : UseCaseObservationConvention {
    override fun getName(): String = "enact.use.case"

    override fun getContextualName(context: UseCaseObservationContext): String = context.useCaseName

    override fun getLowCardinalityKeyValues(context: UseCaseObservationContext): KeyValues =
        KeyValues.of(
            UseCaseKeyNames.NAME.withValue(context.useCaseName),
            UseCaseKeyNames.GROUP.withValue(context.group),
        )
}
