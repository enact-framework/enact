package io.enact.autoconfigure

import io.enact.autoconfigure.configuration.DefinitionLoaderConfiguration
import io.enact.autoconfigure.definition.Definitions
import io.enact.autoconfigure.properties.EnactProperties
import io.enact.core.configuration.EnableEnact
import io.enact.core.trigger.TriggerHandler
import io.enact.core.trigger.TriggerHandlerRegistry
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import

@AutoConfiguration
@EnableEnact
@EnableConfigurationProperties(EnactProperties::class)
@Import(DefinitionLoaderConfiguration::class)
@ConditionalOnBooleanProperty("enact.enabled", havingValue = true, matchIfMissing = true)
class EnactAutoConfiguration {
    @Bean
    fun triggerHandlerRegistry(handlers: List<TriggerHandler<*>>): TriggerHandlerRegistry = TriggerHandlerRegistry(handlers)

    @Bean
    fun triggerActivator(
        definitions: Definitions,
        registry: TriggerHandlerRegistry,
        applicationContext: ApplicationContext,
    ): TriggerActivator = TriggerActivator(definitions, registry, applicationContext)
}
