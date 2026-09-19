package io.enact.trigger.rest

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.Duration
import java.util.UUID

class RestInputBinderTest {
    private val jsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    data class Order(
        val customerId: String,
        val tenantId: String,
        val order: Map<String, Any>,
    )

    private fun binder(
        path: String,
        bind: Map<String, String> = emptyMap(),
        inputType: Class<*> = Order::class.java,
    ) = RestInputBinder.of("placeOrder", path, bind, inputType, jsonMapper)

    @Test
    fun `should accept valid bindings`() {
        assertThatCode {
            binder("/customers/{customerId}/orders", mapOf("tenantId" to "header:X-Tenant-Id", "order" to "body:/order"))
        }.doesNotThrowAnyException()
    }

    @Test
    fun `should accept attribute binding`() {
        assertThatCode { binder("/customers/{customerId}/orders", mapOf("tenantId" to "attribute:tenant")) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `should reject binding to unknown property`() {
        assertThatThrownBy { binder("/orders", mapOf("tenant" to "header:X-Tenant-Id")) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("'tenant'")
            .hasMessageContaining("placeOrder")
    }

    @Test
    fun `should reject unknown source`() {
        assertThatThrownBy { binder("/orders", mapOf("tenantId" to "cookie:tenant")) }
            .hasMessageContaining("cookie:tenant")
    }

    @Test
    fun `should reject binding to path variable missing from path`() {
        assertThatThrownBy { binder("/orders", mapOf("customerId" to "path:customer")) }
            .hasMessageContaining("{customer}")
    }

    @Test
    fun `should reject path variable that binds to no property`() {
        assertThatThrownBy { binder("/shops/{shopId}/orders") }
            .hasMessageContaining("{shopId}")
    }

    @Test
    fun `should reject whole body bound more than once`() {
        assertThatThrownBy { binder("/orders", mapOf("tenantId" to "body", "order" to "body")) }
            .hasMessageContaining("body")
    }

    @Test
    fun `should reject invalid body pointer`() {
        assertThatThrownBy { binder("/orders", mapOf("order" to "body:order")) }
            .hasMessageContaining("body:order")
    }

    @Test
    fun `should reject bindings for scalar input`() {
        assertThatThrownBy { binder("/orders", mapOf("id" to "header:X-Id"), UUID::class.java) }
            .hasMessageContaining("data class")
    }

    @Test
    fun `should reject several path variables for scalar input`() {
        assertThatThrownBy { binder("/customers/{customerId}/orders/{id}", inputType = UUID::class.java) }
            .hasMessageContaining("data class")
    }

    @Test
    fun `should treat string-readable types as scalar input`() {
        assertThatCode { binder("/timeouts/{timeout}", inputType = Duration::class.java) }.doesNotThrowAnyException()
    }

    @Test
    fun `should reject bindings for use case without input`() {
        assertThatThrownBy { binder("/orders", mapOf("id" to "header:X-Id"), Unit::class.java) }
            .hasMessageContaining("no input")
    }
}
