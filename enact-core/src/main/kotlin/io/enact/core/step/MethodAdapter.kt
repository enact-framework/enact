package io.enact.core.step

import java.lang.reflect.Method

class MethodAdapter(
    override val name: String,
    override var settings: StepSettings?,
    val targetObject: Any,
    val method: Method,
    val inputClass: Class<*>,
    val outputClass: Class<*>,
) : Step.InOut<Any, Any> {
    override fun execute(input: Any): Any {
        if (inputClass == Unit::class.java) {
            return method.invoke(targetObject)
        }

        return method.invoke(targetObject, input)
    }
}
