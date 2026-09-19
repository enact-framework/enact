package io.enact.trigger.rest

import io.enact.autoconfigure.properties.EnactProperties
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.ServerResponse
import tools.jackson.databind.json.JsonMapper

@AutoConfiguration
@ConditionalOnClass(RouterFunction::class)
@ConditionalOnBooleanProperty("enact.enabled", havingValue = true, matchIfMissing = true)
class EnactRestTriggerAutoConfiguration {
    @Bean
    fun restTriggerHandler(
        jsonMapper: JsonMapper,
        properties: EnactProperties,
        beanFactory: ListableBeanFactory,
    ): RestTriggerHandler = RestTriggerHandler(jsonMapper, properties, beanFactory)

    /** Routes are only known after use cases are created, so delegate lazily to the handler. */
    @Bean
    fun enactRestRoutes(handler: RestTriggerHandler): RouterFunction<ServerResponse> = RouterFunction { handler.findHandler(it) }
}
