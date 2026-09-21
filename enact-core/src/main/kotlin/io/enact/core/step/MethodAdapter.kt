package io.enact.core.step

import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.core.ResolvableType
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

class MethodAdapter(
    override val name: String,
    override val settings: StepSettings?,
    val targetObject: Any,
    val method: Method,
) : Step.InOut<Any, Any> {
    /** One entry per method parameter, in declaration order. Empty for a method taking none. */
    val parameters: List<StepParameter> = parametersOf(name, method)

    /** Type the method returns, generics included. Kotlin's `Unit` stands in for `void`. */
    val outputType: ResolvableType =
        if (method.returnType == Void.TYPE) UNIT else ResolvableType.forMethodReturnType(method)

    /** Invokes the method with one value per entry of [parameters]. */
    fun invoke(arguments: List<Any?>): Any {
        require(arguments.size == parameters.size) {
            "Step '$name' takes ${parameters.size} arguments, got ${arguments.size}."
        }
        try {
            // void methods return null from reflection
            return method.invoke(targetObject, *arguments.toTypedArray()) ?: Unit
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    override fun execute(input: Any): Any = invoke(if (parameters.isEmpty()) emptyList() else listOf(input))

    private companion object {
        val UNIT: ResolvableType = ResolvableType.forClass(Unit::class.java)
        val discoverer = DefaultParameterNameDiscoverer()

        fun parametersOf(
            step: String,
            method: Method,
        ): List<StepParameter> {
            val names = discoverer.getParameterNames(method)
            require(names != null || method.parameterCount <= 1) {
                "Step '$step' takes ${method.parameterCount} parameters, but their names cannot be read, " +
                    "so a use case cannot bind them. Compile with parameter names (`-java-parameters` for " +
                    "Kotlin, `-parameters` for Java), or keep the step to a single parameter."
            }
            return method.parameters.mapIndexed { index, parameter ->
                StepParameter(
                    names?.get(index) ?: parameter.name,
                    ResolvableType.forMethodParameter(method, index),
                )
            }
        }
    }
}
