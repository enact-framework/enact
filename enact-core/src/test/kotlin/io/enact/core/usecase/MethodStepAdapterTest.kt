package io.enact.core.usecase

import io.enact.core.step.MethodAdapter
import org.junit.jupiter.api.Test

class MethodStepAdapterTest {
    @Test
    fun `should invoke method with input parameter`() {
        val target = TestService()
        val method = TestService::class.java.getMethod("toUpperCase", String::class.java)

        val adapter =
            MethodAdapter(
                name = "to-upper",
                settings = null,
                targetObject = target,
                method = method,
            )

        val result = adapter.execute("hello")

        assert(result == "HELLO")
    }

    @Test
    fun `should invoke method with no parameters when input is Unit`() {
        val target = TestService()
        val method = TestService::class.java.getMethod("generateValue")

        val adapter =
            MethodAdapter(
                name = "generate",
                settings = null,
                targetObject = target,
                method = method,
            )

        val result = adapter.execute(Unit)

        assert(result == "generated")
    }

    class TestService {
        fun toUpperCase(input: String): String = input.uppercase()

        fun generateValue(): String = "generated"
    }
}
