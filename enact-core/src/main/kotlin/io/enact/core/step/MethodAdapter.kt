package io.enact.core.step

import org.springframework.core.ResolvableType
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

class MethodAdapter(
    override val name: String,
    override val settings: StepSettings?,
    val targetObject: Any,
    val method: Method,
) : Step.InOut<Any, Any> {
    /** Type of the method's parameter, generics included. Kotlin's `Unit` stands in for a method without one. */
    val inputType: ResolvableType = inputTypeOf(method)

    /** Type the method returns, generics included. Kotlin's `Unit` stands in for `void`. */
    val outputType: ResolvableType = outputTypeOf(method)

    private val takesNoInput = method.parameterCount == 0

    override fun execute(input: Any): Any {
        try {
            val result = if (takesNoInput) method.invoke(targetObject) else method.invoke(targetObject, input)
            // void methods return null from reflection
            return result ?: Unit
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    private companion object {
        val UNIT: ResolvableType = ResolvableType.forClass(Unit::class.java)

        fun inputTypeOf(method: Method): ResolvableType =
            when (method.parameterCount) {
                0 -> UNIT
                1 -> ResolvableType.forMethodParameter(method, 0)
                else -> throw IllegalArgumentException("Method ${method.name} must have 0 to 1 parameter")
            }

        fun outputTypeOf(method: Method): ResolvableType =
            if (method.returnType == Void.TYPE) UNIT else ResolvableType.forMethodReturnType(method)
    }
}
