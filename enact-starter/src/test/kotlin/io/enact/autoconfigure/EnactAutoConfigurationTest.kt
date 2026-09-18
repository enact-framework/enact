package io.enact.autoconfigure

import io.enact.core.annotation.Step
import io.enact.core.usecase.UseCase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.resilience.annotation.Retryable
import java.util.concurrent.atomic.AtomicInteger

class EnactAutoConfigurationTest {
    private val runner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EnactAutoConfiguration::class.java))

    @Test
    fun `should inject use case into bean created before its step beans`() {
        runner
            .withBean(Consumer::class.java)
            .withBean(Greeter::class.java)
            .withPropertyValues(
                "enact.use-cases[0].name=greet",
                "enact.use-cases[0].steps[0].step=greet",
            ).run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context.getBean(Consumer::class.java).useCase.execute("Ann")).isEqualTo("Hello Ann")
            }
    }

    @Test
    fun `should retry step configured in yaml`() {
        runner
            .withBean(Flaky::class.java)
            .withPropertyValues(
                "enact.use-cases[0].name=flaky",
                "enact.use-cases[0].steps[0].step=flaky",
                "enact.use-cases[0].steps[0].settings.retry.max-retries=2",
                "enact.use-cases[0].steps[0].settings.retry.delay=1ms",
            ).run { context ->
                val useCase = context.getBean("flaky") as UseCase<String, String>

                assertThat(useCase.execute("x")).isEqualTo("x")
                assertThat(context.getBean(Flaky::class.java).calls.get()).isEqualTo(3)
            }
    }

    @Test
    fun `should rethrow original step exception when retries are exhausted`() {
        runner
            .withBean(Flaky::class.java)
            .withPropertyValues(
                "enact.use-cases[0].name=flaky",
                "enact.use-cases[0].steps[0].step=flaky",
                "enact.use-cases[0].steps[0].settings.retry.max-retries=1",
                "enact.use-cases[0].steps[0].settings.retry.delay=1ms",
            ).run { context ->
                val useCase = context.getBean("flaky") as UseCase<String, String>

                assertThatThrownBy { useCase.execute("x") }
                    .isInstanceOf(IllegalStateException::class.java)
                    .hasMessage("attempt 2")
            }
    }

    @Test
    fun `should keep step settings per use case`() {
        runner
            .withBean(Flaky::class.java)
            .withPropertyValues(
                "enact.use-cases[0].name=withRetry",
                "enact.use-cases[0].steps[0].step=flaky",
                "enact.use-cases[0].steps[0].settings.retry.max-retries=2",
                "enact.use-cases[0].steps[0].settings.retry.delay=1ms",
                "enact.use-cases[1].name=withoutRetry",
                "enact.use-cases[1].steps[0].step=flaky",
            ).run { context ->
                val useCase = context.getBean("withRetry") as UseCase<String, String>

                assertThat(useCase.execute("x")).isEqualTo("x")
            }
    }

    @Test
    fun `should keep retry declared on step annotation when yaml has no settings`() {
        runner
            .withBean(AnnotatedFlaky::class.java)
            .withPropertyValues(
                "enact.use-cases[0].name=flaky",
                "enact.use-cases[0].steps[0].step=annotatedFlaky",
            ).run { context ->
                val useCase = context.getBean("flaky") as UseCase<String, String>

                assertThat(useCase.execute("x")).isEqualTo("x")
            }
    }

    @Test
    fun `should serve cached step result by spel key`() {
        runner
            .withBean(Counter::class.java)
            .withBean(CacheManager::class.java, { ConcurrentMapCacheManager() })
            .withPropertyValues(
                "enact.use-cases[0].name=count",
                "enact.use-cases[0].steps[0].step=count",
                "enact.use-cases[0].steps[0].settings.cache.name=counts",
                "enact.use-cases[0].steps[0].settings.cache.key=#input.id",
            ).run { context ->
                val useCase = context.getBean("count") as UseCase<Query, Int>

                assertThat(useCase.execute(Query("a", "first"))).isEqualTo(1)
                assertThat(useCase.execute(Query("a", "second"))).isEqualTo(1)
                assertThat(useCase.execute(Query("b", "first"))).isEqualTo(2)
            }
    }

    @Test
    fun `should fail startup when cache is configured without cache manager`() {
        runner
            .withBean(Counter::class.java)
            .withPropertyValues(
                "enact.use-cases[0].name=count",
                "enact.use-cases[0].steps[0].step=count",
                "enact.use-cases[0].steps[0].settings.cache.name=counts",
            ).run { context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("no CacheManager bean is available")
            }
    }

    @Test
    fun `should inject use case by parameter name when types are ambiguous`() {
        runner
            .withBean(Greeter::class.java)
            .withBean(Consumer::class.java)
            .withPropertyValues(
                "enact.use-cases[0].name=other",
                "enact.use-cases[0].steps[0].step=greet",
                "enact.use-cases[1].name=useCase",
                "enact.use-cases[1].steps[0].step=greet",
            ).run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context.getBean(Consumer::class.java).useCase).isSameAs(context.getBean("useCase"))
            }
    }

    @Test
    fun `should only retry included exceptions from yaml`() {
        runner
            .withBean(Flaky::class.java)
            .withPropertyValues(
                "enact.use-cases[0].name=flaky",
                "enact.use-cases[0].steps[0].step=flaky",
                "enact.use-cases[0].steps[0].settings.retry.max-retries=2",
                "enact.use-cases[0].steps[0].settings.retry.delay=1ms",
                "enact.use-cases[0].steps[0].settings.retry.includes=java.lang.IllegalArgumentException",
            ).run { context ->
                val useCase = context.getBean("flaky") as UseCase<String, String>

                assertThatThrownBy { useCase.execute("x") }.hasMessage("attempt 1")
            }
    }

    class Consumer(
        val useCase: UseCase<String, String>,
    )

    class Greeter {
        @Step
        fun greet(name: String): String = "Hello $name"
    }

    class Flaky {
        val calls = AtomicInteger()

        @Step
        fun flaky(input: String): String {
            val attempt = calls.incrementAndGet()
            check(attempt >= 3) { "attempt $attempt" }
            return input
        }
    }

    class AnnotatedFlaky {
        private val calls = AtomicInteger()

        @Step(retry = Retryable(maxRetries = 2, delay = 1))
        fun annotatedFlaky(input: String): String {
            check(calls.incrementAndGet() >= 3)
            return input
        }
    }

    data class Query(
        val id: String,
        val note: String,
    )

    class Counter {
        private val calls = AtomicInteger()

        @Step
        fun count(query: Query): Int = calls.incrementAndGet()
    }
}
