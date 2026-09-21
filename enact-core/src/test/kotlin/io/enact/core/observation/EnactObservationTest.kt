package io.enact.core.observation

import io.enact.core.cache.CacheSpec
import io.enact.core.step.Step
import io.enact.core.step.StepSettings
import io.enact.core.usecase.RuntimeUseCaseContainer
import io.micrometer.observation.Observation
import io.micrometer.observation.tck.TestObservationRegistry
import io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.cache.concurrent.ConcurrentMapCacheManager

class EnactObservationTest {
    private val registry = TestObservationRegistry.create()

    @Test
    fun `should record the use case with its name and group`() {
        useCase(steps = listOf(upperCase()), group = "admin").execute("ann")

        assertThat(registry)
            .hasObservationWithNameEqualTo("enact.use.case")
            .that()
            .hasContextualNameEqualTo("greet")
            .hasLowCardinalityKeyValue("enact.use.case.name", "greet")
            .hasLowCardinalityKeyValue("enact.group", "admin")
    }

    @Test
    fun `should default the group tag when the use case declares none`() {
        useCase(steps = listOf(upperCase())).execute("ann")

        assertThat(registry)
            .hasObservationWithNameEqualTo("enact.use.case")
            .that()
            .hasLowCardinalityKeyValue("enact.group", "default")
    }

    @Test
    fun `should record one observation per step, naming its use case`() {
        useCase(steps = listOf(upperCase(), exclaim())).execute("ann")

        assertThat(registry).hasNumberOfObservationsWithNameEqualTo("enact.step", 2)
        assertThat(registry)
            .hasAnObservation { it.hasContextualNameEqualTo("upperCase") }
            .hasAnObservation { it.hasContextualNameEqualTo("exclaim") }
        assertThat(registry)
            .forAllObservationsWithNameEqualTo("enact.step") {
                it.hasLowCardinalityKeyValue("enact.use.case.name", "greet")
            }
    }

    @Test
    fun `should nest step observations under the use case`() {
        useCase(steps = listOf(upperCase())).execute("ann")

        val step = handledContexts().single { it is StepObservationContext }
        val useCase = handledContexts().single { it is UseCaseObservationContext }
        Assertions.assertThat(step.parentObservation?.contextView).isSameAs(useCase)
    }

    @Test
    fun `should record the error when a step fails`() {
        val failing =
            object : Step.InOut<String, String> {
                override val name = "boom"

                override fun execute(input: String): String = throw IllegalStateException("nope")
            }

        assertThrows<IllegalStateException> { useCase(steps = listOf(failing)).execute("ann") }

        assertThat(registry)
            .hasObservationWithNameEqualTo("enact.use.case")
            .that()
            .hasError()
        assertThat(registry)
            .hasObservationWithNameEqualTo("enact.step")
            .that()
            .hasError()
    }

    @Test
    fun `should tag an uncached step as none`() {
        useCase(steps = listOf(upperCase())).execute("ann")

        assertThat(registry)
            .hasObservationWithNameEqualTo("enact.step")
            .that()
            .hasLowCardinalityKeyValue("enact.cache", "none")
    }

    @Test
    fun `should tag the first call as a miss and the next as a hit`() {
        val cacheManager = ConcurrentMapCacheManager("greetings")
        val useCase =
            useCase(
                steps = listOf(upperCase()),
                stepSettings = listOf(StepSettings(cache = CacheSpec("greetings"))),
                cacheManager = cacheManager,
            )

        useCase.execute("ann")
        useCase.execute("ann")

        val statuses = handledContexts().filterIsInstance<StepObservationContext>().map { it.cacheStatus }
        Assertions.assertThat(statuses).containsExactly(CacheStatus.MISS, CacheStatus.HIT)
        assertThat(registry).hasAnObservationWithAKeyValue("enact.cache", "hit")
        assertThat(registry).hasAnObservationWithAKeyValue("enact.cache", "miss")
    }

    @Test
    fun `should execute unchanged and record nothing without a registry`() {
        val useCase =
            RuntimeUseCaseContainer<String, String>(
                name = "greet",
                description = "greets",
                steps = listOf(upperCase(), exclaim()),
            )

        Assertions.assertThat(useCase.execute("ann")).isEqualTo("ANN!")
        assertThat(registry).doesNotHaveAnyObservation()
    }

    private fun handledContexts(): List<Observation.Context> {
        val contexts = mutableListOf<Observation.Context>()
        assertThat(registry).hasHandledContextsThatSatisfy { contexts.addAll(it) }
        return contexts
    }

    private fun useCase(
        steps: List<Step<*, *>>,
        stepSettings: List<StepSettings?> = steps.map { it.settings },
        cacheManager: ConcurrentMapCacheManager? = null,
        group: String? = null,
    ) = RuntimeUseCaseContainer<String, String>(
        name = "greet",
        description = "greets",
        steps = steps,
        stepSettings = stepSettings,
        cacheManager = cacheManager,
        group = group,
        observations = EnactObservations(registry),
    )

    private fun upperCase() =
        object : Step.InOut<String, String> {
            override val name = "upperCase"

            override fun execute(input: String): String = input.uppercase()
        }

    private fun exclaim() =
        object : Step.InOut<String, String> {
            override val name = "exclaim"

            override fun execute(input: String): String = "$input!"
        }
}
