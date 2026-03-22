package io.enact.core.usecase

import io.enact.core.step.Step
import org.springframework.core.retry.RetryTemplate
import java.util.concurrent.ConcurrentHashMap

class UseCaseExecutor {
    private val retryStrategyCache: ConcurrentHashMap<String, RetryTemplate> = ConcurrentHashMap()

    fun <Input, Output> execute(
        input: Input,
        useCase: UseCase<Input, Output>,
    ): Output {
        var current: Any? = input
        for (step in useCase.steps) {
            val castedStep = step as Step<Any?, Any?>

            current =
                if (step.settings?.retry != null) {
                    val retryTemplate =
                        retryStrategyCache.computeIfAbsent(
                            getSepRetryCacheKey(
                                useCase,
                                step,
                            ),
                        ) { RetryTemplate(step.settings!!.retry!!.toRetryPolicy()) }

                    retryTemplate.execute { castedStep.execute(current) }
                } else {
                    castedStep.execute(current)
                }
        }
        return current as Output
    }

    private fun getSepRetryCacheKey(
        useCase: UseCase<*, *>,
        step: Step<*, *>,
    ): String = "${useCase.name}_${step.name}"
}
