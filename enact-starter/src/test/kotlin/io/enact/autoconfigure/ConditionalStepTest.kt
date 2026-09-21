package io.enact.autoconfigure

import io.enact.core.annotation.Step
import io.enact.core.usecase.UseCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.ApplicationContext

@Suppress("UNCHECKED_CAST")
class ConditionalStepTest {
    private val runner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EnactAutoConfiguration::class.java))
            .withBean(Orders::class.java)

    @Test
    fun `should run a step whose condition holds`() {
        discount().run { context ->
            assertThat(useCase(context).execute(Order("ann", 200))).isEqualTo(Order("ann", 180))
        }
    }

    @Test
    fun `should pass the matching input through when the condition does not hold`() {
        discount().run { context ->
            assertThat(useCase(context).execute(Order("ann", 50))).isEqualTo(Order("ann", 50))
        }
    }

    @Test
    fun `should read the condition's variables by parameter name`() {
        runner
            .withPropertyValues(
                definitions(
                    """
                    use-cases:
                      describe:
                        steps:
                          - step: findCoupon
                          - step: applyCoupon
                            in:
                              order: ${'$'}input
                              coupon: ${'$'}findCoupon
                            when: "#coupon != null"
                    """,
                ),
            ).run { context ->
                assertThat(useCase(context).execute(Order("ann", 200))).isEqualTo(Order("ann", 100))
                assertThat(useCase(context).execute(Order("no-coupon", 200))).isEqualTo(Order("no-coupon", 200))
            }
    }

    @Test
    fun `should yield the declared value when the condition does not hold`() {
        runner
            .withPropertyValues(
                definitions(
                    """
                    use-cases:
                      describe:
                        steps:
                          - step: fee
                            when: "#input.amount > 100"
                            else: 0
                    """,
                ),
            ).run { context ->
                assertThat(useCase(context).execute(Order("ann", 200))).isEqualTo(20)
                assertThat(useCase(context).execute(Order("ann", 50))).isEqualTo(0)
            }
    }

    @Test
    fun `should pass through the input named by else`() {
        runner
            .withPropertyValues(
                definitions(
                    """
                    use-cases:
                      describe:
                        steps:
                          - step: merge
                            in:
                              first: ${'$'}input
                              second: ${'$'}input
                            when: "false"
                            else: ${'$'}second
                    """,
                ),
            ).run { context ->
                assertThat(useCase(context).execute(Order("ann", 7))).isEqualTo(Order("ann", 7))
            }
    }

    @Test
    fun `should not require a fallback for a conditional step nothing reads`() {
        runner
            .withPropertyValues(
                definitions(
                    """
                    use-cases:
                      describe:
                        output: findCoupon
                        steps:
                          - step: findCoupon
                          - step: fee
                            in: ${'$'}input
                            when: "false"
                    """,
                ),
            ).run { context ->
                assertThat(context).hasNotFailed()
                assertThat(useCase(context).execute(Order("ann", 200))).isEqualTo("half")
            }
    }

    @Test
    fun `should fail startup when no input can stand in for the output`() {
        runner
            .withPropertyValues(
                definitions(
                    """
                    use-cases:
                      describe:
                        steps:
                          - step: fee
                            when: "true"
                    """,
                ),
            ).run { context ->
                assertThat(context)
                    .hasFailed()
                    .failure
                    .rootCause()
                    .hasMessageContaining("can stand in for its")
                    .hasMessageContaining("give a value with 'else'")
            }
    }

    @Test
    fun `should fail startup when several inputs could stand in for the output`() {
        runner
            .withPropertyValues(
                definitions(
                    """
                    use-cases:
                      describe:
                        steps:
                          - step: merge
                            in:
                              first: ${'$'}input
                              second: ${'$'}input
                            when: "true"
                    """,
                ),
            ).run { context ->
                assertThat(context)
                    .hasFailed()
                    .failure
                    .rootCause()
                    .hasMessageContaining("could each stand in for its output")
            }
    }

    @Test
    fun `should fail startup when the declared value is not of the output type`() {
        runner
            .withPropertyValues(
                definitions(
                    """
                    use-cases:
                      describe:
                        steps:
                          - step: fee
                            when: "true"
                            else: not-a-number
                    """,
                ),
            ).run { context ->
                assertThat(context)
                    .hasFailed()
                    .failure
                    .hasStackTraceContaining("declares an 'else' that is not a")
            }
    }

    private fun discount() =
        runner.withPropertyValues(
            definitions(
                """
                use-cases:
                  describe:
                    steps:
                      - step: applyDiscount
                        when: "#input.amount > 100"
                """,
            ),
        )

    private fun useCase(context: ApplicationContext) = context.getBean("describe") as UseCase<Order, Any>

    data class Order(
        val customer: String,
        val amount: Int,
    )

    class Orders {
        @Step
        fun applyDiscount(order: Order): Order = order.copy(amount = order.amount - 20)

        @Step
        fun findCoupon(order: Order): String? = "half".takeIf { order.customer != "no-coupon" }

        @Step
        fun applyCoupon(
            order: Order,
            coupon: String?,
        ): Order = order.copy(amount = order.amount / 2)

        @Step
        fun fee(order: Order): Int = order.amount / 10

        @Step
        fun merge(
            first: Order,
            second: Order,
        ): Order = first.copy(amount = first.amount + second.amount)
    }
}
