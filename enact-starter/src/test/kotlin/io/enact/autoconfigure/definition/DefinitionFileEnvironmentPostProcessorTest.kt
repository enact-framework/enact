package io.enact.autoconfigure.definition

import io.enact.autoconfigure.EnactAutoConfiguration
import io.enact.core.annotation.Step
import io.enact.core.usecase.UseCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

class DefinitionFileEnvironmentPostProcessorTest {
    @Test
    @Suppress("UNCHECKED_CAST")
    fun `should register a use case read from a definition file of the default location`() {
        SpringApplicationBuilder(TestApplication::class.java)
            .web(WebApplicationType.NONE)
            .bannerMode(org.springframework.boot.Banner.Mode.OFF)
            .run()
            .use { context ->
                val greet = context.getBean("greet") as UseCase<String, String>

                assertThat(greet.execute("Ann")).isEqualTo("Hello Ann")
            }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(EnactAutoConfiguration::class)
    class TestApplication {
        @Bean
        fun greeter() = Greeter()
    }

    class Greeter {
        @Step
        fun greet(name: String) = "Hello $name"
    }
}
