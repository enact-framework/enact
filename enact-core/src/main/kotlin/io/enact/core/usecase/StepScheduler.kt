package io.enact.core.usecase

import io.micrometer.context.ContextSnapshot
import io.micrometer.context.ContextSnapshotFactory
import org.apache.commons.logging.LogFactory
import org.springframework.core.task.AsyncTaskExecutor
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future

/**
 * Runs the steps of a use case, level by level.
 *
 * A level holds the steps whose inputs are all ready, so they may run at once. Whether they actually do is
 * the use case's choice: a step moved off the caller's thread leaves any surrounding transaction,
 * `SecurityContext` and MDC behind, so running in sequence stays the default. A step declared to run aside
 * is the exception, since not waiting for it is the whole point.
 */
internal class StepScheduler(
    private val useCase: String,
    private val graph: UseCaseGraph,
    private val executor: AsyncTaskExecutor,
    private val concurrent: Boolean,
) {
    /** Indices into the graph's nodes, grouped by how deep they are in the graph. */
    private val levels: List<List<Int>> = levelsOf(graph)

    /**
     * Runs every step, publishing a level's results before the next one starts, since the next level reads
     * them. Steps running aside are started and left to finish on their own.
     */
    fun run(
        step: (Int) -> Any?,
        publish: (Int, Any?) -> Unit,
    ) = levels.forEach { level ->
        val snapshot = ContextSnapshotFactory.builder().build().captureAll()
        val (aside, awaited) = level.partition { graph.nodes[it].node.side }

        aside.forEach { index -> runAside(index, step, snapshot) }
        runLevel(awaited, step, snapshot).forEach { (index, value) -> publish(index, value) }
    }

    /** Nothing reads this step and nobody waits for it, so a failure can only be logged; it is observed too. */
    private fun runAside(
        index: Int,
        step: (Int) -> Any?,
        snapshot: ContextSnapshot,
    ) {
        executor.submit {
            try {
                snapshot.setThreadLocals().use { step(index) }
            } catch (throwable: Throwable) {
                log.error("Step '${graph.nodes[index].id}' of use case '$useCase' runs aside and failed.", throwable)
            }
        }
    }

    private fun runLevel(
        level: List<Int>,
        step: (Int) -> Any?,
        snapshot: ContextSnapshot,
    ): List<Pair<Int, Any?>> {
        if (!concurrent || level.size <= 1) {
            return level.map { it to step(it) }
        }

        // The snapshot carries the observation scope, and whatever else registered a ThreadLocalAccessor.
        val futures: List<Future<Any?>> =
            level.map { index ->
                executor.submit(Callable { snapshot.setThreadLocals().use { step(index) } })
            }

        val results = mutableListOf<Pair<Int, Any?>>()
        val failures = mutableListOf<Throwable>()
        // Every task of the level is already running, so they are awaited rather than cancelled: interrupting
        // a blocking call is not something this can promise to do.
        futures.forEachIndexed { position, future ->
            try {
                results += level[position] to future.get()
            } catch (exception: ExecutionException) {
                failures += exception.cause ?: exception
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                throw exception
            }
        }

        failures.firstOrNull()?.let { first ->
            failures.drop(1).filterNot { it === first }.forEach(first::addSuppressed)
            throw first
        }
        return results
    }

    private companion object {
        val log = LogFactory.getLog(StepScheduler::class.java)

        /** A step sits one level below the deepest step it reads, which declaration order already orders. */
        fun levelsOf(graph: UseCaseGraph): List<List<Int>> {
            val depths = HashMap<String, Int>(graph.nodes.size)
            val levels = mutableListOf<MutableList<Int>>()

            graph.nodes.forEachIndexed { index, node ->
                val depth =
                    node.sources
                        .filterIsInstance<Binding.Output>()
                        .maxOfOrNull { depths.getValue(it.node) + 1 } ?: 0
                depths[node.id] = depth
                while (levels.size <= depth) levels += mutableListOf<Int>()
                levels[depth] += index
            }
            return levels
        }
    }
}
