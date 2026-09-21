package io.enact.core.usecase

import io.enact.core.observation.DefaultUseCaseObservationConvention
import io.enact.core.observation.EnactObservationDocumentation
import io.enact.core.observation.EnactObservations
import io.enact.core.observation.UseCaseObservationContext
import io.enact.core.step.Step
import org.springframework.cache.CacheManager
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.core.task.SimpleAsyncTaskExecutor
import java.util.function.Supplier

class RuntimeUseCaseContainer<Input, Output>(
    override val name: String,
    override val description: String,
    /** Steps of the use case in declaration order; each may only bind to one declared before it. */
    nodes: List<StepNode>,
    /** Node whose output is the use case's. Inferred when not set: the only one nothing reads. */
    output: String? = null,
    cacheManager: CacheManager? = null,
    /** Group of the use case, recorded as a tag. `default` when not set. */
    group: String? = null,
    private val observations: EnactObservations = EnactObservations.NONE,
    /** Whether steps that do not read each other run at once. Sequential on the caller's thread when false. */
    concurrent: Boolean = false,
    /** Runs the steps when [concurrent]; virtual threads when not given. */
    executor: AsyncTaskExecutor? = null,
) : UseCase<Input, Output> {
    override val steps: List<Step<*, *>> = nodes.map { it.step }
    override val inputType: Class<*>
    override val outputType: Class<*>

    private val groupName = group ?: DEFAULT_GROUP
    private val graph = buildGraph(name, nodes, output)
    private val invokers: List<StepInvoker>
    private val scheduler = StepScheduler(graph, if (concurrent) executor ?: virtualThreads() else null)

    init {
        inputType = graph.inputType.toClass()
        outputType = graph.sink.outputType.toClass()

        invokers =
            graph.nodes.map { node ->
                StepInvoker(node, cacheManager, name, groupName, observations)
            }
    }

    override fun execute(input: Input): Output {
        val observation =
            EnactObservationDocumentation.USE_CASE.observation(
                observations.useCaseConvention,
                DefaultUseCaseObservationConvention,
                { UseCaseObservationContext(name, groupName) },
                observations.registry,
            )

        @Suppress("UNCHECKED_CAST")
        return observation.observe(Supplier { runSteps(input) }) as Output
    }

    /**
     * Steps observe themselves within the scope opened above, so their spans nest under the use case's; the
     * scheduler carries that scope onto its workers.
     *
     * Steps of one level never read each other, so the map is only written between levels.
     */
    private fun runSteps(input: Input): Any? {
        val outputs = HashMap<String, Any?>(graph.nodes.size)

        scheduler.run(
            step = { index ->
                val node = graph.nodes[index]
                invokers[index].invoke(
                    node.sources.map { source ->
                        when (source) {
                            is Binding.Input -> input
                            is Binding.Output -> outputs[source.node]
                        }
                    },
                )
            },
            publish = { index, value -> outputs[graph.nodes[index].id] = value },
        )

        return outputs[graph.sink.id]
    }

    private companion object {
        const val DEFAULT_GROUP = "default"

        fun virtualThreads() = SimpleAsyncTaskExecutor("enact-").apply { setVirtualThreads(true) }
    }
}
