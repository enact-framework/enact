package io.enact.core.observation

import io.micrometer.observation.ObservationRegistry

/**
 * Observation wiring shared by the use cases of an application: the registry to record into, and the
 * conventions overriding the default names and tags.
 *
 * A use case built with [NONE] records nothing. [ObservationRegistry.NOOP] returns before the observation
 * context is created, so a use case that is turned off carries no cost at execution time.
 */
class EnactObservations(
    val registry: ObservationRegistry = ObservationRegistry.NOOP,
    val useCaseConvention: UseCaseObservationConvention? = null,
    val stepConvention: StepObservationConvention? = null,
) {
    companion object {
        /** Records nothing. */
        val NONE = EnactObservations()
    }
}
