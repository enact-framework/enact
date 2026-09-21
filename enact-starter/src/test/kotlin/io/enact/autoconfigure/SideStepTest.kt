package io.enact.autoconfigure

import io.enact.core.annotation.Step
import io.enact.core.usecase.UseCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.ApplicationContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@Suppress("UNCHECKED_CAST")
class SideStepTest {
    private val runner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EnactAutoConfiguration::class.java))
            .withBean(Notifier::class.java)

    @Test
    fun `should return without waiting for a step that runs aside`() {
        runner.withPropertyValues(definitions(NOTIFY)).run { context ->
            val notifier = context.getBean(Notifier::class.java)

            // The notification cannot finish until released, so returning proves it was not awaited.
            assertThat(useCase(context).execute("ann")).isEqualTo("Hello ann")

            notifier.release()
            assertThat(notifier.awaitNotified()).isEqualTo("Hello ann")
        }
    }

    @Test
    fun `should run a step aside on another thread`() {
        runner.withPropertyValues(definitions(NOTIFY)).run { context ->
            val notifier = context.getBean(Notifier::class.java)
            notifier.release()

            useCase(context).execute("ann")

            assertThat(notifier.awaitNotified()).isEqualTo("Hello ann")
            assertThat(notifier.thread.get()).isNotEqualTo(Thread.currentThread().name)
        }
    }

    @Test
    fun `should not let a step that runs aside fail the use case`() {
        runner
            .withPropertyValues(
                definitions(
                    """
                    use-cases:
                      greet:
                        steps:
                          - step: greet
                          - step: explode
                            side: true
                    """,
                ),
            ).run { context ->
                assertThat(useCase(context).execute("ann")).isEqualTo("Hello ann")
            }
    }

    @Test
    fun `should fail startup when a step reads one that runs aside`() {
        runner
            .withPropertyValues(
                definitions(
                    """
                    use-cases:
                      greet:
                        steps:
                          - step: greet
                          - step: notify
                            side: true
                          - step: shout
                            in: ${'$'}notify
                    """,
                ),
            ).run { context ->
                assertThat(context)
                    .hasFailed()
                    .failure
                    .rootCause()
                    .hasMessageContaining("which runs aside")
            }
    }

    @Test
    fun `should fail startup when every step runs aside`() {
        runner
            .withPropertyValues(
                definitions(
                    """
                    use-cases:
                      greet:
                        steps:
                          - step: greet
                            side: true
                    """,
                ),
            ).run { context ->
                assertThat(context)
                    .hasFailed()
                    .failure
                    .rootCause()
                    .hasMessageContaining("has no step to return")
            }
    }

    private fun useCase(context: ApplicationContext) = context.getBean("greet") as UseCase<String, String>

    class Notifier {
        private val released = CountDownLatch(1)
        private val notified = CountDownLatch(1)
        private val message = AtomicReference<String>()
        val thread = AtomicReference<String>()

        @Step
        fun greet(name: String): String = "Hello $name"

        @Step
        fun notify(greeting: String) {
            check(released.await(2, TimeUnit.SECONDS)) { "Never released." }
            thread.set(Thread.currentThread().name)
            message.set(greeting)
            notified.countDown()
        }

        @Step
        fun explode(greeting: String): Unit = throw IllegalStateException("boom")

        @Step
        fun shout(greeting: String): String = greeting.uppercase()

        fun release() = released.countDown()

        fun awaitNotified(): String {
            check(notified.await(2, TimeUnit.SECONDS)) { "The step running aside never ran." }
            return message.get()
        }
    }

    private companion object {
        val NOTIFY =
            """
            use-cases:
              greet:
                steps:
                  - step: greet
                  - step: notify
                    side: true
            """
    }
}
