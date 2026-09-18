package io.enact.core.step

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

class MethodAdapter(
    override val name: String,
    override val settings: StepSettings?,
    val targetObject: Any,
    val method: Method,
    val inputClass: Class<*>,
    val outputClass: Class<*>,
) : Step.InOut<Any, Any> {
    override fun execute(input: Any): Any {
        try {
            val result =
                if (inputClass == Unit::class.java) {
                    method.invoke(targetObject)
                } else {
                    method.invoke(targetObject, input)
                }
            // void methods return null from reflection
            return result ?: Unit
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }
}
