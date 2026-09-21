package io.enact.autoconfigure

import io.enact.core.annotation.Step
import io.enact.core.usecase.UseCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

@Suppress("UNCHECKED_CAST")
class UseCaseGraphDefinitionTest {
    private val runner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EnactAutoConfiguration::class.java))
            .withBean(Words::class.java)

    @Test
    fun `should bind each parameter of a step taking several`() {
        runner
            .withPropertyValues(
                definitions(
                    """
                    use-cases:
                      describe:
                        steps:
                          - step: shout
                          - step: length
                            in: ${'$'}input
                          - step: combine
                            in:
                              text: ${'$'}shout
                              count: ${'$'}length
                    """,
                ),
            ).run { context ->
                assertThat(context).hasNotFailed()
                assertThat(useCase(context).execute("hi")).isEqualTo("HI!2")
            }
    }

    @Test
    fun `should read the step declared before it when nothing is bound`() {
        runner
            .withPropertyValues(
                definitions(
                    """
                    use-cases:
                      describe:
                        steps:
                          - step: shout
                          - step: length
                    """,
                ),
            ).run { context ->
                assertThat(useCase(context).execute("hi")).isEqualTo(3)
            }
    }

    @Test
    fun `should run the same step twice under different ids`() {
        runner
            .withPropertyValues(
                definitions(
                    """
                    use-cases:
                      describe:
                        steps:
                          - step: shout
                            id: first
                          - step: shout
                            id: second
                            in: ${'$'}first
                    """,
                ),
            ).run { context ->
                assertThat(useCase(context).execute("hi")).isEqualTo("HI!!")
            }
    }

    @Test
    fun `should return the step named as the output`() {
        runner
            .withPropertyValues(
                definitions(
                    """
                    use-cases:
                      describe:
                        output: shout
                        steps:
                          - step: shout
                          - step: length
                            in: ${'$'}input
                    """,
                ),
            ).run { context ->
                assertThat(useCase(context).execute("hi")).isEqualTo("HI!")
            }
    }

    @Test
    fun `should fail startup when two steps are terminal and none is named`() {
        runner
            .withPropertyValues(
                definitions(
                    """
                    use-cases:
                      describe:
                        steps:
                          - step: shout
                          - step: length
                            in: ${'$'}input
                    """,
                ),
            ).run { context ->
                assertThat(context)
                    .hasFailed()
                    .failure
                    .rootCause()
                    .hasMessageContaining("whose output nothing reads: [shout, length]")
            }
    }

    @Test
    fun `should fail startup on a binding that is not a reference`() {
        runner
            .withPropertyValues(
                definitions(
                    """
                    use-cases:
                      describe:
                        steps:
                          - step: shout
                          - step: length
                            in: shout
                    """,
                ),
            ).run { context ->
                assertThat(context)
                    .hasFailed()
                    .failure
                    .rootCause()
                    .hasMessageContaining("which is not a reference")
            }
    }

    @Test
    fun `should fail startup on a binding to an unknown step`() {
        runner
            .withPropertyValues(
                definitions(
                    """
                    use-cases:
                      describe:
                        steps:
                          - step: shout
                          - step: length
                            in: ${'$'}nope
                    """,
                ),
            ).run { context ->
                assertThat(context)
                    .hasFailed()
                    .failure
                    .rootCause()
                    .hasMessageContaining("not a step declared before it")
            }
    }

    @Test
    fun `should fail startup when a binding type does not fit the parameter`() {
        runner
            .withPropertyValues(
                definitions(
                    """
                    use-cases:
                      describe:
                        steps:
                          - step: length
                          - step: combine
                            in:
                              text: ${'$'}length
                              count: ${'$'}length
                    """,
                ),
            ).run { context ->
                assertThat(context)
                    .hasFailed()
                    .failure
                    .rootCause()
                    .hasMessageContaining("but the parameter expects")
            }
    }

    private fun useCase(context: org.springframework.context.ApplicationContext) = context.getBean("describe") as UseCase<String, Any>

    class Words {
        @Step
        fun shout(text: String): String = "${text.uppercase()}!"

        @Step
        fun length(text: String): Int = text.length

        @Step
        fun combine(
            text: String,
            count: Int,
        ): String = "$text$count"
    }
}
