package io.enact.autoconfigure.definition

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.Ordered
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

/**
 * Adds the use cases read from definition files to the environment, before anything binds them.
 *
 * It runs last, once Spring Boot has read the application configuration, so `enact.definitions` may itself
 * come from an imported file or a profile. The definitions go behind the other property sources: they are
 * merged with the use cases of `application.yaml` by name, and the application configuration comes first.
 */
class DefinitionFileEnvironmentPostProcessor :
    EnvironmentPostProcessor,
    Ordered {
    override fun postProcessEnvironment(
        environment: ConfigurableEnvironment,
        application: SpringApplication,
    ) {
        val resolver = PathMatchingResourcePatternResolver(application.classLoader)
        DefinitionFileLoader(resolver).load(environment)?.let { environment.propertySources.addLast(it) }
    }

    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE
}
