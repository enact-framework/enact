package io.enact.autoconfigure

import io.enact.core.annotation.Step
import io.enact.core.usecase.UseCase
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.tck.TestObservationRegistry
import io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.assertj.core.api.Assertions.assertThat as assertThatObject

class ObservabilityAutoConfigurationTest {
    private val runner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EnactAutoConfiguration::class.java))
            .withBean(Greeter::class.java)
            .withBean(ObservationRegistry::class.java, { TestObservationRegistry.create() })

    @Test
    fun `should observe use cases out of the box when a registry is present`() {
        runner
            .withPropertyValues(
                definitions(
                    """
                    use-cases:
                      greet:
                        steps:
                          - step: greet
                    """,
                ),
            ).run { context ->
                val registry = context.execute()

                assertThat(registry)
                    .hasObservationWithNameEqualTo("enact.use.case")
                    .that()
                    .hasLowCardinalityKeyValue("enact.use.case.name", "greet")
                assertThat(registry).hasObservationWithNameEqualTo("enact.step")
            }
    }

    @Test
    fun `should record nothing when observability is disabled globally`() {
        runner
            .withPropertyValues(
                "enact.observability.enabled=false",
                definitions(
                    """
                    use-cases:
                      greet:
                        steps:
                          - step: greet
                    """,
                ),
            ).run { context ->
                assertThat(context.execute()).doesNotHaveAnyObservation()
            }
    }

    @Test
    fun `should record nothing when the use case's group is disabled`() {
        runner
            .withPropertyValues(
                definitions(
                    """
                    use-cases:
                      greet:
                        group: admin
                        steps:
                          - step: greet
                    groups:
                      admin:
                        observability:
                          enabled: false
                    """,
                ),
            ).run { context ->
                assertThat(context.execute()).doesNotHaveAnyObservation()
            }
    }

    @Test
    fun `should record nothing when the use case itself is disabled`() {
        runner
            .withPropertyValues(
                definitions(
                    """
                    use-cases:
                      greet:
                        observability:
                          enabled: false
                        steps:
                          - step: greet
                    """,
                ),
            ).run { context ->
                assertThat(context.execute()).doesNotHaveAnyObservation()
            }
    }

    @Test
    fun `should let a use case opt back in when its group is disabled`() {
        runner
            .withPropertyValues(
                definitions(
                    """
                    use-cases:
                      greet:
                        group: admin
                        observability:
                          enabled: true
                        steps:
                          - step: greet
                    groups:
                      admin:
                        observability:
                          enabled: false
                    """,
                ),
            ).run { context ->
                assertThat(context.execute()).hasObservationWithNameEqualTo("enact.use.case")
            }
    }

    @Test
    fun `should let a group opt back in when observability is disabled globally`() {
        runner
            .withPropertyValues(
                "enact.observability.enabled=false",
                definitions(
                    """
                    use-cases:
                      greet:
                        group: admin
                        steps:
                          - step: greet
                    groups:
                      admin:
                        observability:
                          enabled: true
                    """,
                ),
            ).run { context ->
                assertThat(context.execute()).hasObservationWithNameEqualTo("enact.use.case")
            }
    }

    /** The meter name and tag keys are the contract users build dashboards on, so pin them end to end. */
    @Test
    fun `should publish timers named after the observations`() {
        val meterRegistry = SimpleMeterRegistry()
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EnactAutoConfiguration::class.java))
            .withBean(Greeter::class.java)
            .withBean(MeterRegistry::class.java, { meterRegistry })
            .withBean(
                ObservationRegistry::class.java,
                {
                    ObservationRegistry.create().apply {
                        observationConfig().observationHandler(
                            DefaultMeterObservationHandler(meterRegistry) as ObservationHandler<*>,
                        )
                    }
                },
            ).withPropertyValues(
                definitions(
                    """
                    use-cases:
                      greet:
                        steps:
                          - step: greet
                    """,
                ),
            ).run { context ->
                @Suppress("UNCHECKED_CAST")
                (context.getBean("greet") as UseCase<String, String>).execute("Ann")

                val useCaseTimer = meterRegistry.find("enact.use.case").timer()
                assertThatObject(useCaseTimer).isNotNull
                assertThatObject(useCaseTimer!!.count()).isEqualTo(1)
                assertThatObject(useCaseTimer.id.getTag("enact.use.case.name")).isEqualTo("greet")
                assertThatObject(useCaseTimer.id.getTag("enact.group")).isEqualTo("default")
                // Micrometer adds `error` itself; Enact must not declare it
                assertThatObject(useCaseTimer.id.getTag("error")).isEqualTo("none")

                val stepTimer = meterRegistry.find("enact.step").timer()
                assertThatObject(stepTimer).isNotNull
                assertThatObject(stepTimer!!.id.getTag("enact.step.name")).isEqualTo("greet")
                assertThatObject(stepTimer.id.getTag("enact.cache")).isEqualTo("none")
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun AssertableApplicationContext.execute(): TestObservationRegistry {
        (getBean("greet") as UseCase<String, String>).execute("Ann")
        return getBean(ObservationRegistry::class.java) as TestObservationRegistry
    }

    class Greeter {
        @Step
        fun greet(name: String): String = "Hello $name"
    }
}
