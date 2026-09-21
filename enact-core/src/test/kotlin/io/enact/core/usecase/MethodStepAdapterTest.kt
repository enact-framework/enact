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

    @Test
    fun `should return null from a method that returns null`() {
        val adapter =
            MethodAdapter(
                name = "maybe",
                settings = null,
                targetObject = TestService(),
                method = TestService::class.java.getMethod("maybe", String::class.java),
            )

        assert(adapter.invoke(listOf("none")) == null) { "Got ${adapter.invoke(listOf("none"))}" }
        assert(adapter.invoke(listOf("some")) == "value")
    }

    @Test
    fun `should return Unit from a method that returns nothing`() {
        val adapter =
            MethodAdapter(
                name = "consume",
                settings = null,
                targetObject = TestService(),
                method = TestService::class.java.getMethod("consume", String::class.java),
            )

        assert(adapter.invoke(listOf("x")) == Unit)
    }

    class TestService {
        fun maybe(input: String): String? = "value".takeIf { input == "some" }

        fun consume(input: String) {
            check(input.isNotEmpty())
        }

        fun toUpperCase(input: String): String = input.uppercase()

        fun generateValue(): String = "generated"
    }
}
