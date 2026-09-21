package io.enact.trigger.rest

import io.enact.core.annotation.Step
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest(classes = [RestTriggerHandlerTest.App::class])
@AutoConfigureMockMvc
class RestTriggerHandlerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `should bind body and path variable to input`() {
        mockMvc
            .post("/orders/c-1") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"amount": 10}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.customerId") { value("c-1") }
                jsonPath("$.amount") { value(10) }
            }
    }

    @Test
    fun `should bind path variable and matching query parameter without body`() {
        mockMvc.get("/orders/42?verbose=true&unknown=ignored").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(42) }
            jsonPath("$.verbose") { value(true) }
        }
    }

    @Test
    fun `should reject body field that is bound from path variable`() {
        mockMvc
            .post("/orders/c-1") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"customerId": "c-2", "amount": 10}"""
            }.andExpect {
                status {
                    isBadRequest()
                    reason(containsString("'customerId' is bound from path variable {customerId}"))
                }
            }
    }

    @Test
    fun `should reject body field that is also sent as query parameter`() {
        mockMvc
            .post("/orders/c-1?amount=5") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"amount": 10}"""
            }.andExpect {
                status {
                    isBadRequest()
                    reason(containsString("'amount' is bound from query parameter amount"))
                }
            }
    }

    @Test
    fun `should bind header, query, whole body and body pointer`() {
        mockMvc
            .post("/customers/c-1/orders?dry-run=true&tags=a&tags=b") {
                header("X-Tenant-Id", "acme")
                contentType = MediaType.APPLICATION_JSON
                content = """{"lines": ["book", "pen"], "tenantId": "not-a-conflict"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.customerId") { value("c-1") }
                jsonPath("$.tenantId") { value("acme") }
                jsonPath("$.dryRun") { value(true) }
                jsonPath("$.tags.length()") { value(2) }
                jsonPath("$.order.lines[1]") { value("pen") }
                jsonPath("$.firstLine") { value("book") }
            }
    }

    @Test
    fun `should apply defaults for absent optional values`() {
        mockMvc
            .post("/customers/c-1/orders") {
                header("X-Tenant-Id", "acme")
                contentType = MediaType.APPLICATION_JSON
                content = """{"lines": []}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.dryRun") { value(false) }
                jsonPath("$.tags.length()") { value(0) }
                jsonPath("$.firstLine") { doesNotExist() }
            }
    }

    @Test
    fun `should respond bad request when required header is missing`() {
        mockMvc
            .post("/customers/c-1/orders") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"lines": []}"""
            }.andExpect {
                status {
                    isBadRequest()
                    reason(containsString("tenantId"))
                }
            }
    }

    @Test
    fun `should respond bad request when single-valued property receives several values`() {
        mockMvc
            .post("/customers/c-1/orders?dry-run=true&dry-run=false") {
                header("X-Tenant-Id", "acme")
                contentType = MediaType.APPLICATION_JSON
                content = """{"lines": []}"""
            }.andExpect {
                status {
                    isBadRequest()
                    reason(containsString("'dryRun' expects a single value"))
                }
            }
    }

    @Test
    fun `should bind renamed path variable`() {
        mockMvc.get("/shipments/s-7").andExpect {
            status { isOk() }
            jsonPath("$.reference") { value("s-7") }
        }
    }

    @Test
    fun `should bind single path variable to scalar input`() {
        mockMvc.get("/items/42").andExpect {
            status { isOk() }
            content { string("43") }
        }
    }

    @Test
    fun `should bind body to scalar input`() {
        mockMvc
            .post("/echo") {
                contentType = MediaType.APPLICATION_JSON
                content = "\"hello\""
            }.andExpect {
                status { isOk() }
                content { string("hello!") }
            }
    }

    @Test
    fun `should respond bad request on null body for scalar input`() {
        mockMvc
            .post("/echo") {
                contentType = MediaType.APPLICATION_JSON
                content = "null"
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `should execute use case without input`() {
        mockMvc.get("/ping").andExpect {
            status { isOk() }
            content { string("pong") }
        }
    }

    @Test
    fun `should respond without body when use case returns nothing`() {
        mockMvc.delete("/orders").andExpect {
            status { isNoContent() }
            content { string("") }
        }
    }

    @Test
    fun `should respond bad request on unreadable input`() {
        mockMvc
            .post("/orders/c-1") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"amount": "not a number"}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }

    data class CreateOrder(
        val customerId: String,
        val amount: Int,
    )

    data class FindOrder(
        val id: Long,
        val verbose: Boolean = false,
    )

    data class OrderBody(
        val lines: List<String>,
    )

    data class PlaceOrder(
        val customerId: String,
        val tenantId: String,
        val dryRun: Boolean = false,
        val tags: List<String> = emptyList(),
        val order: OrderBody,
        val firstLine: String? = null,
    )

    data class FindShipment(
        val reference: String,
    )

    class OrderSteps {
        @Step
        fun createOrder(input: CreateOrder): CreateOrder = input

        @Step
        fun findOrder(input: FindOrder): FindOrder = input

        @Step
        fun placeOrder(input: PlaceOrder): PlaceOrder = input

        @Step
        fun findShipment(input: FindShipment): FindShipment = input

        @Step
        fun findItem(id: Long): Long = id + 1

        @Step
        fun echo(text: String): String = "$text!"

        @Step
        fun ping(): String = "pong"

        @Step
        fun purge() {}
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    class App {
        @Bean
        fun orderSteps() = OrderSteps()
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun enactDefinitions(registry: DynamicPropertyRegistry) {
            val location =
                definitionsFile(
                    """
                    use-cases:
                      createOrder:
                        steps:
                          - step: createOrder
                        trigger:
                          rest:
                            method: POST
                            path: "/orders/{customerId}"
                            status: 201
                      findOrder:
                        steps:
                          - step: findOrder
                        trigger:
                          rest:
                            method: GET
                            path: "/orders/{id}"
                      ping:
                        steps:
                          - step: ping
                        trigger:
                          rest:
                            method: GET
                            path: "/ping"
                      purge:
                        steps:
                          - step: purge
                        trigger:
                          rest:
                            method: DELETE
                            path: "/orders"
                            status: 204
                      placeOrder:
                        steps:
                          - step: placeOrder
                        trigger:
                          rest:
                            method: POST
                            path: "/customers/{customerId}/orders"
                            bind:
                              tenantId: "header:X-Tenant-Id"
                              dryRun: "query:dry-run"
                              order: body
                              firstLine: "body:/lines/0"
                      findItem:
                        steps:
                          - step: findItem
                        trigger:
                          rest:
                            method: GET
                            path: "/items/{id}"
                      echo:
                        steps:
                          - step: echo
                        trigger:
                          rest:
                            method: POST
                            path: "/echo"
                      findShipment:
                        steps:
                          - step: findShipment
                        trigger:
                          rest:
                            method: GET
                            path: "/shipments/{code}"
                            bind:
                              reference: "path:code"
                    """,
                )

            registry.add("enact.definitions") { location }
        }
    }
}
