package io.enact.autoconfigure

import io.enact.core.annotation.Step
import io.enact.core.usecase.UseCase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Suppress("UNCHECKED_CAST")
class ConcurrentUseCaseTest {
    private val runner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EnactAutoConfiguration::class.java))
            .withBean(Slow::class.java)

    @Test
    fun `should run steps that do not read each other at once`() {
        runner.withPropertyValues(definitions(fork(concurrent = true))).run { context ->
            // Each step waits for the other to have started, so this only returns if they truly overlap.
            assertThat(useCase(context).execute("go")).isEqualTo("left|right")
        }
    }

    @Test
    fun `should run steps in sequence without the flag`() {
        runner.withPropertyValues(definitions(fork(concurrent = false))).run { context ->
            val slow = context.getBean(Slow::class.java)

            assertThatThrownBy { useCase(context).execute("go") }
                .hasMessageContaining("timed out")
            assertThat(slow.threads).hasSize(1)
        }
    }

    @Test
    fun `should run each step on its own thread when concurrent`() {
        runner.withPropertyValues(definitions(fork(concurrent = true))).run { context ->
            useCase(context).execute("go")

            assertThat(context.getBean(Slow::class.java).threads).hasSize(2)
        }
    }

    @Test
    fun `should report the failure of a concurrent step`() {
        runner
            .withPropertyValues(
                definitions(
                    """
                    use-cases:
                      describe:
                        concurrent: true
                        steps:
                          - step: boom
                          - step: right
                            in: ${'$'}input
                          - step: join
                            in:
                              left: ${'$'}boom
                              right: ${'$'}right
                    """,
                ),
            ).run { context ->
                assertThatThrownBy { useCase(context).execute("go") }
                    .isInstanceOf(IllegalStateException::class.java)
                    .hasMessage("boom")
            }
    }

    private fun fork(concurrent: Boolean) =
        """
        use-cases:
          describe:
            concurrent: $concurrent
            steps:
              - step: left
              - step: right
                in: ${'$'}input
              - step: join
                in:
                  left: ${'$'}left
                  right: ${'$'}right
        """

    private fun useCase(context: ApplicationContext) = context.getBean("describe") as UseCase<String, String>

    class Slow {
        private val started = CountDownLatch(2)
        val threads: MutableSet<String> = ConcurrentHashMap.newKeySet()

        @Step
        fun left(input: String): String = rendezvous("left")

        @Step
        fun right(input: String): String = rendezvous("right")

        @Step
        fun boom(input: String): String = throw IllegalStateException("boom")

        @Step
        fun join(
            left: String,
            right: String,
        ): String = "$left|$right"

        /** Returns only once the other step has started too, so sequential execution cannot get past it. */
        private fun rendezvous(name: String): String {
            threads += Thread.currentThread().name
            started.countDown()
            check(started.await(2, TimeUnit.SECONDS)) { "Step '$name' timed out waiting for its sibling." }
            return name
        }
    }
}
