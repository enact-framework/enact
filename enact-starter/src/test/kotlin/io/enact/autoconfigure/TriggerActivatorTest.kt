package io.enact.autoconfigure

import io.enact.core.annotation.Step
import io.enact.core.trigger.TriggerHandler
import io.enact.core.trigger.TriggerRegistration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/** A trigger type the framework knows nothing about, standing in for a Kafka or SQS starter. */
class TriggerActivatorTest {
    @Test
    fun `should bind definition of a trigger type unknown to the framework`() {
        val handler = QueueTriggerHandler()

        runner(handler)
            .withPropertyValues(
                *useCase(
                    "enact.use-cases.greet.trigger.queue.name=orders",
                    "enact.use-cases.greet.trigger.queue.batch-size=10",
                    "enact.use-cases.greet.trigger.queue.attributes.region=eu-west-1",
                ),
            ).run { context ->
                assertThat(context).hasNotFailed()

                val registration = handler.registrations.single()
                assertThat(registration.useCaseName).isEqualTo("greet")
                assertThat(registration.useCase.execute("Ann")).isEqualTo("Hello Ann")
                assertThat(registration.definition)
                    .isEqualTo(QueueTriggerDefinition("orders", 10, mapOf("region" to "eu-west-1")))
            }
    }

    @Test
    fun `should create definition with its defaults when trigger has no properties`() {
        val handler = QueueTriggerHandler()

        runner(handler)
            .withPropertyValues(*useCase("enact.use-cases.greet.trigger.queue.name=orders"))
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(handler.registrations.single().definition).isEqualTo(QueueTriggerDefinition("orders"))
            }
    }

    @Test
    fun `should hand the filters of the use case group to the handler`() {
        val handler = QueueTriggerHandler()

        runner(handler)
            .withPropertyValues(
                "enact.groups.audited.filters=logMessage,traceMessage",
                *useCase("enact.use-cases.greet.group=audited", "enact.use-cases.greet.trigger.queue.name=orders"),
            ).run { context ->
                assertThat(handler.registrations.single().groupFilters).containsExactly("logMessage", "traceMessage")
            }
    }

    @Test
    fun `should start handler once every use case is registered, and stop it on shutdown`() {
        val handler = QueueTriggerHandler()

        runner(handler)
            .withPropertyValues(
                *useCase("enact.use-cases.greet.trigger.queue.name=orders"),
                "enact.use-cases.welcome.steps[0].step=greet",
                "enact.use-cases.welcome.trigger.queue.name=signups",
            ).run { context ->
                assertThat(handler.registeredWhenStarted).containsExactly("greet", "welcome")
                assertThat(handler.stopped).isFalse()
            }

        assertThat(handler.stopped).isTrue()
    }

    @Test
    fun `should not register a use case without trigger`() {
        val handler = QueueTriggerHandler()

        runner(handler).withPropertyValues(*useCase()).run { context ->
            assertThat(context).hasNotFailed()
            assertThat(handler.registrations).isEmpty()
            assertThat(handler.registeredWhenStarted).isEmpty()
        }
    }

    @Test
    fun `should fail startup on unknown trigger type`() {
        runner(QueueTriggerHandler())
            .withPropertyValues(*useCase("enact.use-cases.greet.trigger.carrierPigeon.name=orders"))
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure)
                    .hasStackTraceContaining("No trigger handler for 'carrierPigeon'")
                    .hasStackTraceContaining("[queue]")
            }
    }

    @Test
    fun `should fail startup when a use case declares several triggers`() {
        runner(QueueTriggerHandler())
            .withPropertyValues(
                *useCase(
                    "enact.use-cases.greet.trigger.queue.name=orders",
                    "enact.use-cases.greet.trigger.rest.path=/greet",
                ),
            ).run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).hasStackTraceContaining("must declare exactly one trigger")
            }
    }

    private fun runner(handler: QueueTriggerHandler) =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EnactAutoConfiguration::class.java))
            .withBean(Greeter::class.java)
            .withBean(QueueTriggerHandler::class.java, { handler })

    private fun useCase(vararg extra: String) =
        arrayOf(
            "enact.use-cases.greet.steps[0].step=greet",
            *extra,
        )

    data class QueueTriggerDefinition(
        val name: String = "",
        val batchSize: Int = 1,
        val attributes: Map<String, String> = emptyMap(),
    )

    class QueueTriggerHandler : TriggerHandler<QueueTriggerDefinition> {
        override val triggerType = "queue"
        override val definitionType = QueueTriggerDefinition::class.java

        val registrations = mutableListOf<TriggerRegistration<QueueTriggerDefinition>>()
        var registeredWhenStarted = emptyList<String>()
        var stopped = false

        override fun register(registration: TriggerRegistration<QueueTriggerDefinition>) {
            registrations.add(registration)
        }

        override fun start() {
            registeredWhenStarted = registrations.map { it.useCaseName }
        }

        override fun stop() {
            stopped = true
        }
    }

    class Greeter {
        @Step
        fun greet(name: String): String = "Hello $name"
    }
}
