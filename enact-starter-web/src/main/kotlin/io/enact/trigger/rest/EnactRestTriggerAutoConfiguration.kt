package io.enact.trigger.rest

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.ServerResponse
import tools.jackson.databind.json.JsonMapper

@AutoConfiguration
@ConditionalOnClass(RouterFunction::class)
class EnactRestTriggerAutoConfiguration {
    @Bean
    fun restTriggerHandler(jsonMapper: JsonMapper): RestTriggerHandler = RestTriggerHandler(jsonMapper)

    /** Routes are only known after use cases are created, so delegate lazily to the handler. */
    @Bean
    fun enactRestRoutes(handler: RestTriggerHandler): RouterFunction<ServerResponse> = RouterFunction { handler.findHandler(it) }
}
