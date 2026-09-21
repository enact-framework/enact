package io.enact.core.usecase

import io.enact.core.step.StepParameter
import io.enact.core.step.outputType
import io.enact.core.step.parameters
import org.springframework.core.ResolvableType
import org.springframework.util.ClassUtils

/**
 * The steps of a use case with every edge resolved and checked.
 *
 * A node may only bind to a node declared before it, so declaration order is already a topological order and
 * the graph can be run by walking [nodes] once. That rule also makes a cycle impossible to write.
 */
internal class UseCaseGraph(
    val nodes: List<ResolvedNode>,
    /** Type every node reading the use case's input expects; `Unit` when no node reads it. */
    val inputType: ResolvableType,
    /** Node whose output is the use case's output. */
    val sink: ResolvedNode,
)

internal class ResolvedNode(
    val node: StepNode,
    val parameters: List<StepParameter>,
    val outputType: ResolvableType,
    /** Where each parameter takes its value, in parameter order. */
    val sources: List<Binding>,
) {
    val id get() = node.id
    val name get() = node.step.name

    /** Set once the graph knows whether anything reads this node; null when nothing does. */
    var fallback: Fallback? = null
        internal set
}

/**
 * Resolves the bindings of [nodes], checks every edge and finds the use case's input and output.
 *
 * @param output node whose output is the use case's; inferred from the graph when null.
 */
internal fun buildGraph(
    useCase: String,
    nodes: List<StepNode>,
    output: String? = null,
): UseCaseGraph {
    require(nodes.isNotEmpty()) { "Must specify at least one step." }

    val byId = LinkedHashMap<String, ResolvedNode>()
    nodes.forEachIndexed { index, node ->
        require(node.id !in byId) { "Use case '$useCase' declares the step id '${node.id}' twice." }
        byId[node.id] = resolve(useCase, node, index, byId)
    }

    val resolved = byId.values.toList()
    val read = resolved.readSteps()
    // Feeding a step that runs aside does not use a value up: nothing waits for it, so the step feeding it
    // is still free to be what the use case returns.
    val consumed = resolved.filterNot { it.node.side }.readSteps()
    val sink = sinkOf(useCase, resolved, consumed, output)

    // A conditional step only needs something to yield when skipped if its value is read, aside or not.
    resolved.filter { it.node.condition != null && (it.id in read || it === sink) }.forEach {
        it.fallback = fallbackOf(useCase, it)
    }

    return UseCaseGraph(resolved, inputTypeOf(useCase, resolved), sink)
}

/**
 * What a conditional step yields when it is skipped: the input it declares, a literal, or the only input
 * that can stand in for its output.
 */
private fun fallbackOf(
    useCase: String,
    node: ResolvedNode,
): Fallback {
    val where = "Step '${node.id}' of use case '$useCase' is conditional and read by another step"

    return when (val declared = node.node.fallback) {
        is Fallback.Parameter -> {
            val parameter =
                requireNotNull(node.parameters.firstOrNull { it.name == declared.name }) {
                    "$where, but its 'else' names '${declared.name}', which is not one of its parameters. " +
                        "Known: ${node.parameters.map { it.name }}."
                }
            require(node.outputType.isAssignableFrom(parameter.type)) {
                "$where, but its 'else' passes '${declared.name}' through, which is ${parameter.type} " +
                    "while the step produces ${node.outputType}."
            }
            declared
        }

        is Fallback.Value -> {
            // `isInstance` is always false on a primitive class, and a step may well produce `int`.
            val expected = ClassUtils.resolvePrimitiveIfNecessary(node.outputType.toClass())
            require(declared.value == null || expected.isInstance(declared.value)) {
                "$where, but its 'else' is a ${declared.value!!::class.java.name} while the step produces " +
                    "${node.outputType}."
            }
            declared
        }

        null -> {
            val candidates = node.parameters.filter { node.outputType.isAssignableFrom(it.type) }
            require(candidates.size == 1) {
                "$where, so it must say what it yields when skipped. " +
                    if (candidates.isEmpty()) {
                        "None of its inputs ${node.parameters.map { it.name }} can stand in for its " +
                            "${node.outputType} output, so give a value with 'else'."
                    } else {
                        "Its inputs ${candidates.map { it.name }} could each stand in for its output; " +
                            "pick one with 'else: \$${candidates.first().name}'."
                    }
            }
            Fallback.Parameter(candidates.single().name)
        }
    }
}

private fun List<ResolvedNode>.readSteps(): Set<String> =
    flatMap { node -> node.sources.filterIsInstance<Binding.Output>().map { it.node } }.toSet()

private fun resolve(
    useCase: String,
    node: StepNode,
    index: Int,
    declared: Map<String, ResolvedNode>,
): ResolvedNode {
    val where = "Step '${node.id}' of use case '$useCase'"
    val parameters = node.step.parameters
    val names = parameters.map { it.name }

    node.bindings.keys.forEach { name ->
        require(name in names) {
            "$where binds '$name', which is not one of its parameters. Known: $names."
        }
    }

    val sources =
        parameters.map { parameter ->
            node.bindings[parameter.name] ?: implicit(where, parameters, index, declared)
        }

    sources.forEachIndexed { position, source ->
        if (source is Binding.Output) {
            val producer =
                requireNotNull(declared[source.node]) {
                    "$where binds '${parameters[position].name}' to '${source.node}', which is not a step " +
                        "declared before it. Known: ${declared.keys}."
                }
            require(!producer.node.side) {
                "$where binds '${parameters[position].name}' to '${source.node}', which runs aside. " +
                    "A step run aside is never waited for, so nothing can read what it produces."
            }
            val expected = parameters[position].type
            require(expected.isAssignableFrom(producer.outputType)) {
                "$where binds '${parameters[position].name}' to '${source.node}', which produces " +
                    "${producer.outputType}, but the parameter expects $expected."
            }
        }
    }

    return ResolvedNode(node, parameters, node.step.outputType, sources)
}

/** A single parameter left unbound reads the node declared before it, or the use case's input. */
private fun implicit(
    where: String,
    parameters: List<StepParameter>,
    index: Int,
    declared: Map<String, ResolvedNode>,
): Binding {
    require(parameters.size == 1) {
        "$where takes ${parameters.size} parameters, so each of them must be bound with 'in'. " +
            "Missing: ${parameters.map { it.name }}."
    }
    return declared.values
        .lastOrNull()
        ?.takeIf { index > 0 }
        ?.let { Binding.Output(it.id) } ?: Binding.Input
}

private fun inputTypeOf(
    useCase: String,
    nodes: List<ResolvedNode>,
): ResolvableType {
    val readers =
        nodes.flatMap { node ->
            node.sources
                .withIndex()
                .filter { it.value is Binding.Input }
                .map { node to node.parameters[it.index] }
        }
    if (readers.isEmpty()) return ResolvableType.forClass(Unit::class.java)

    val (first, parameter) = readers.first()
    readers.drop(1).forEach { (node, other) ->
        // Not `==`: a ResolvableType also carries where it was read from, so two `String` are not equal.
        require(parameter.type.isAssignableFrom(other.type) && other.type.isAssignableFrom(parameter.type)) {
            "Use case '$useCase' feeds its input to '${first.id}' as ${parameter.type} and to '${node.id}' " +
                "as ${other.type}. Every step reading the use case's input must expect the same type."
        }
    }
    return parameter.type
}

private fun sinkOf(
    useCase: String,
    nodes: List<ResolvedNode>,
    consumed: Set<String>,
    output: String?,
): ResolvedNode {
    if (output != null) {
        return requireNotNull(nodes.firstOrNull { it.id == output }) {
            "Use case '$useCase' names '$output' as its output, which is not one of its steps. " +
                "Known: ${nodes.map { it.id }}."
        }
    }

    val terminal = nodes.filterNot { it.id in consumed || it.node.side }

    require(terminal.isNotEmpty()) {
        "Use case '$useCase' has no step to return: every step it declares either feeds another one or runs " +
            "aside. Name the one to return with 'output'."
    }
    require(terminal.size == 1) {
        "Use case '$useCase' has ${terminal.size} steps whose output nothing reads: ${terminal.map { it.id }}. " +
            "Name the one to return with 'output', or mark one 'side: true'."
    }
    return terminal.single()
}
