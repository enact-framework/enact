package io.enact.trigger.rest

import io.enact.core.annotation.Step
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest(
    properties = [
        "enact.use-cases[0].name=createOrder",
        "enact.use-cases[0].trigger.rest.method=POST",
        "enact.use-cases[0].trigger.rest.path=/orders/{customerId}",
        "enact.use-cases[0].trigger.rest.status=201",
        "enact.use-cases[0].steps[0].step=createOrder",
        "enact.use-cases[1].name=findOrder",
        "enact.use-cases[1].trigger.rest.method=GET",
        "enact.use-cases[1].trigger.rest.path=/orders/{id}",
        "enact.use-cases[1].steps[0].step=findOrder",
        "enact.use-cases[2].name=ping",
        "enact.use-cases[2].trigger.rest.method=GET",
        "enact.use-cases[2].trigger.rest.path=/ping",
        "enact.use-cases[2].steps[0].step=ping",
        "enact.use-cases[3].name=purge",
        "enact.use-cases[3].trigger.rest.method=DELETE",
        "enact.use-cases[3].trigger.rest.path=/orders",
        "enact.use-cases[3].trigger.rest.status=204",
        "enact.use-cases[3].steps[0].step=purge",
    ],
)
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
    fun `should bind path variable and query parameter without body`() {
        mockMvc.get("/orders/42?verbose=true").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(42) }
            jsonPath("$.verbose") { value(true) }
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

    class OrderSteps {
        @Step
        fun createOrder(input: CreateOrder): CreateOrder = input

        @Step
        fun findOrder(input: FindOrder): FindOrder = input

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
}
