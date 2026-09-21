package io.enact.autoconfigure

import io.enact.core.annotation.Cached
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
import java.util.function.Function

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
                "enact.use-cases.greet.steps[0].step=greet",
            ).run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context.getBean(Consumer::class.java).useCase.execute("Ann")).isEqualTo("Hello Ann")
            }
    }

    @Test
    fun `should fail startup on unknown group`() {
        runner
            .withBean(Greeter::class.java)
            .withPropertyValues(
                "enact.use-cases.greet.group=admin",
                "enact.use-cases.greet.steps[0].step=greet",
            ).run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).hasStackTraceContaining("unknown group 'admin'")
            }
    }

    @Test
    fun `should retry step configured in yaml`() {
        runner
            .withBean(Flaky::class.java)
            .withPropertyValues(
                "enact.use-cases.flaky.steps[0].step=flaky",
                "enact.use-cases.flaky.steps[0].settings.retry.max-retries=2",
                "enact.use-cases.flaky.steps[0].settings.retry.delay=1ms",
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
                "enact.use-cases.flaky.steps[0].step=flaky",
                "enact.use-cases.flaky.steps[0].settings.retry.max-retries=1",
                "enact.use-cases.flaky.steps[0].settings.retry.delay=1ms",
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
                "enact.use-cases.withRetry.steps[0].step=flaky",
                "enact.use-cases.withRetry.steps[0].settings.retry.max-retries=2",
                "enact.use-cases.withRetry.steps[0].settings.retry.delay=1ms",
                "enact.use-cases.withoutRetry.steps[0].step=flaky",
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
                "enact.use-cases.flaky.steps[0].step=annotatedFlaky",
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
                "enact.use-cases.count.steps[0].step=count",
                "enact.use-cases.count.steps[0].settings.cache.name=counts",
                "enact.use-cases.count.steps[0].settings.cache.key=#input.id",
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
                "enact.use-cases.count.steps[0].step=count",
                "enact.use-cases.count.steps[0].settings.cache.name=counts",
            ).run { context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("no CacheManager bean is available")
            }
    }

    @Test
    fun `should use input as cache key when no key is set`() {
        runner
            .withBean(Counter::class.java)
            .withBean(CacheManager::class.java, { ConcurrentMapCacheManager() })
            .withPropertyValues(
                "enact.use-cases.count.steps[0].step=count",
                "enact.use-cases.count.steps[0].settings.cache.name=counts",
            ).run { context ->
                val useCase = context.getBean("count") as UseCase<Query, Int>

                assertThat(useCase.execute(Query("a", "first"))).isEqualTo(1)
                assertThat(useCase.execute(Query("a", "first"))).isEqualTo(1)
                assertThat(useCase.execute(Query("a", "second"))).isEqualTo(2)
            }
    }

    @Test
    fun `should not cache failed step`() {
        runner
            .withBean(Flaky::class.java)
            .withBean(CacheManager::class.java, { ConcurrentMapCacheManager() })
            .withPropertyValues(
                "enact.use-cases.flaky.steps[0].step=flaky",
                "enact.use-cases.flaky.steps[0].settings.cache.name=results",
            ).run { context ->
                val useCase = context.getBean("flaky") as UseCase<String, String>

                assertThatThrownBy { useCase.execute("x") }.hasMessage("attempt 1")
                assertThatThrownBy { useCase.execute("x") }.hasMessage("attempt 2")
                assertThat(useCase.execute("x")).isEqualTo("x")
                assertThat(useCase.execute("x")).isEqualTo("x")
                assertThat(context.getBean(Flaky::class.java).calls.get()).isEqualTo(3)
            }
    }

    @Test
    fun `should serve cached result without retrying`() {
        runner
            .withBean(Flaky::class.java)
            .withBean(CacheManager::class.java, { ConcurrentMapCacheManager() })
            .withPropertyValues(
                "enact.use-cases.flaky.steps[0].step=flaky",
                "enact.use-cases.flaky.steps[0].settings.retry.max-retries=2",
                "enact.use-cases.flaky.steps[0].settings.retry.delay=1ms",
                "enact.use-cases.flaky.steps[0].settings.cache.name=results",
            ).run { context ->
                val useCase = context.getBean("flaky") as UseCase<String, String>

                assertThat(useCase.execute("x")).isEqualTo("x")
                assertThat(useCase.execute("x")).isEqualTo("x")
                assertThat(context.getBean(Flaky::class.java).calls.get()).isEqualTo(3)
            }
    }

    @Test
    fun `should use cache declared on step annotation`() {
        runner
            .withBean(AnnotatedCounter::class.java)
            .withBean(CacheManager::class.java, { ConcurrentMapCacheManager() })
            .withPropertyValues(
                "enact.use-cases.count.steps[0].step=annotatedCount",
            ).run { context ->
                val useCase = context.getBean("count") as UseCase<Query, Int>

                assertThat(useCase.execute(Query("a", "first"))).isEqualTo(1)
                assertThat(useCase.execute(Query("a", "second"))).isEqualTo(1)
                assertThat(useCase.execute(Query("b", "first"))).isEqualTo(2)
            }
    }

    @Test
    fun `should use cache declared on step class annotation`() {
        runner
            .withBean("classCounter", ClassCounter::class.java)
            .withBean(CacheManager::class.java, { ConcurrentMapCacheManager() })
            .withPropertyValues(
                "enact.use-cases.count.steps[0].step=classCounter",
            ).run { context ->
                val useCase = context.getBean("count") as UseCase<Query, Int>

                assertThat(useCase.execute(Query("a", "first"))).isEqualTo(1)
                assertThat(useCase.execute(Query("a", "second"))).isEqualTo(1)
                assertThat(useCase.execute(Query("b", "first"))).isEqualTo(2)
            }
    }

    @Test
    fun `should override annotation cache with yaml cache`() {
        runner
            .withBean(AnnotatedCounter::class.java)
            .withBean(CacheManager::class.java, { ConcurrentMapCacheManager() })
            .withPropertyValues(
                "enact.use-cases.count.steps[0].step=annotatedCount",
                "enact.use-cases.count.steps[0].settings.cache.name=byNote",
                "enact.use-cases.count.steps[0].settings.cache.key=#input.note",
            ).run { context ->
                val useCase = context.getBean("count") as UseCase<Query, Int>

                assertThat(useCase.execute(Query("a", "first"))).isEqualTo(1)
                assertThat(useCase.execute(Query("b", "first"))).isEqualTo(1)
                assertThat(useCase.execute(Query("a", "second"))).isEqualTo(2)
                assertThat(context.getBean(CacheManager::class.java).cacheNames).containsExactly("byNote")
            }
    }

    @Test
    fun `should fail startup when cache name is unknown to cache manager`() {
        runner
            .withBean(Counter::class.java)
            .withBean(CacheManager::class.java, { ConcurrentMapCacheManager("other") })
            .withPropertyValues(
                "enact.use-cases.count.steps[0].step=count",
                "enact.use-cases.count.steps[0].settings.cache.name=counts",
            ).run { context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("which is not known to the CacheManager")
            }
    }

    @Test
    fun `should inject use case by parameter name when types are ambiguous`() {
        runner
            .withBean(Greeter::class.java)
            .withBean(Consumer::class.java)
            .withPropertyValues(
                "enact.use-cases.other.steps[0].step=greet",
                "enact.use-cases.useCase.steps[0].step=greet",
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
                "enact.use-cases.flaky.steps[0].step=flaky",
                "enact.use-cases.flaky.steps[0].settings.retry.max-retries=2",
                "enact.use-cases.flaky.steps[0].settings.retry.delay=1ms",
                "enact.use-cases.flaky.steps[0].settings.retry.includes=java.lang.IllegalArgumentException",
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

    class AnnotatedCounter {
        private val calls = AtomicInteger()

        @Step(cache = Cached(name = "counts", key = "#input.id"))
        fun annotatedCount(query: Query): Int = calls.incrementAndGet()
    }

    @Step(cache = Cached(name = "counts", key = "#input.id"))
    class ClassCounter : Function<Query, Int> {
        private val calls = AtomicInteger()

        override fun apply(query: Query): Int = calls.incrementAndGet()
    }
}
