package io.enact.trigger.rest

import io.enact.autoconfigure.EnactAutoConfiguration
import io.enact.core.annotation.Step
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.servlet.function.HandlerFilterFunction
import org.springframework.web.servlet.function.ServerResponse
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@SpringBootTest(
    classes = [RestTriggerFiltersTest.App::class],
    properties = [
        "enact.groups.default.filters=requireUser",
        "enact.groups.public.filters=",
        "enact.groups.traced.filters=traceA,traceB",
        "enact.use-cases[0].name=whoAmI",
        "enact.use-cases[0].trigger.rest.method=GET",
        "enact.use-cases[0].trigger.rest.path=/me",
        "enact.use-cases[0].trigger.rest.bind.userId=attribute:userId",
        "enact.use-cases[0].trigger.rest.bind.trail=attribute:trail",
        "enact.use-cases[0].steps[0].step=whoAmI",
        "enact.use-cases[1].name=health",
        "enact.use-cases[1].group=public",
        "enact.use-cases[1].trigger.rest.method=GET",
        "enact.use-cases[1].trigger.rest.path=/health",
        "enact.use-cases[1].steps[0].step=health",
        "enact.use-cases[2].name=traced",
        "enact.use-cases[2].group=traced",
        "enact.use-cases[2].trigger.rest.method=GET",
        "enact.use-cases[2].trigger.rest.path=/traced",
        "enact.use-cases[2].trigger.rest.filters=traceC",
        "enact.use-cases[2].trigger.rest.bind.trail=attribute:trail",
        "enact.use-cases[2].steps[0].step=traced",
    ],
)
@AutoConfigureMockMvc
class RestTriggerFiltersTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `should apply default group to use case without group`() {
        mockMvc.get("/me") { header("X-User-Id", USER_ID) }.andExpect {
            status { isOk() }
            jsonPath("$.userId") { value(USER_ID) }
            jsonPath("$.trail") { doesNotExist() }
        }
    }

    @Test
    fun `should reject request in filter before binding input`() {
        mockMvc.get("/me").andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `should not apply default filters to use case with empty group`() {
        mockMvc.get("/health").andExpect {
            status { isOk() }
            content { string("ok") }
        }
    }

    @Test
    fun `should apply group filters in order, then trigger filters`() {
        mockMvc.get("/traced").andExpect {
            status { isOk() }
            jsonPath("$.trail") { value("a,b,c") }
        }
    }

    @Test
    fun `should fail startup on unknown filter`() {
        runner
            .withPropertyValues(*useCase("enact.use-cases[0].trigger.rest.filters=missing"))
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).hasStackTraceContaining("filter 'missing'")
            }
    }

    @Test
    fun `should fail startup on filter that is not a HandlerFilterFunction`() {
        runner
            .withBean("notAFilter", String::class.java, { "nope" })
            .withPropertyValues(*useCase("enact.groups.default.filters=notAFilter"))
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).hasStackTraceContaining("filter 'notAFilter'")
            }
    }

    private val runner =
        WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EnactAutoConfiguration::class.java, EnactRestTriggerAutoConfiguration::class.java))
            .withBean(JsonMapper::class.java, { JsonMapper.builder().build() })
            .withBean(Steps::class.java)

    private fun useCase(vararg extra: String) =
        arrayOf(
            "enact.use-cases[0].name=health",
            "enact.use-cases[0].trigger.rest.method=GET",
            "enact.use-cases[0].trigger.rest.path=/health",
            "enact.use-cases[0].steps[0].step=health",
            *extra,
        )

    data class Me(
        val userId: UUID,
        val trail: String? = null,
    )

    data class Trail(
        val trail: String,
    )

    class Steps {
        @Step
        fun whoAmI(input: Me): Me = input

        @Step
        fun traced(input: Trail): Trail = input

        @Step
        fun health(): String = "ok"
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    class App {
        @Bean
        fun steps() = Steps()

        /** Rejects requests without `X-User-Id`; exposes it as a [UUID] attribute. */
        @Bean
        fun requireUser() =
            HandlerFilterFunction<ServerResponse, ServerResponse> { request, next ->
                val userId = request.headers().firstHeader("X-User-Id") ?: return@HandlerFilterFunction ServerResponse.status(401).build()
                request.attributes()["userId"] = UUID.fromString(userId)
                next.handle(request)
            }

        @Bean
        fun traceA() = trace("a")

        @Bean
        fun traceB() = trace("b")

        @Bean
        fun traceC() = trace("c")

        /** Appends [label] to the `trail` attribute, recording the order filters ran in. */
        private fun trace(label: String) =
            HandlerFilterFunction<ServerResponse, ServerResponse> { request, next ->
                request.attributes().merge("trail", label) { trail, _ -> "$trail,$label" }
                next.handle(request)
            }
    }

    private companion object {
        const val USER_ID = "7f1c1a52-6f7e-4a7e-9d59-2b1a3d7c0e11"
    }
}
