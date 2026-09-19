package io.enact.demo.filter

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.servlet.function.HandlerFilterFunction
import org.springframework.web.servlet.function.ServerResponse

/** Filters referenced by `enact.groups` and `trigger.rest.filters` in application.yaml. */
@Configuration(proxyBeanMethods = false)
class DemoFilters {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Logs every request of the `default` and `authenticated` groups. */
    @Bean
    fun requestLog() =
        HandlerFilterFunction<ServerResponse, ServerResponse> { request, next ->
            log.info("filter requestLog: {} {}", request.method(), request.requestPath())
            next.handle(request)
        }

    /** Stand-in for real authentication: rejects requests without `X-User-Id` and exposes it as the `userId` attribute. */
    @Bean
    fun requireUser() =
        HandlerFilterFunction<ServerResponse, ServerResponse> { request, next ->
            val userId =
                request.headers().firstHeader("X-User-Id")
                    ?: return@HandlerFilterFunction ServerResponse.status(HttpStatus.UNAUTHORIZED).build()
            log.info("filter requireUser: authenticated {}", userId)
            request.attributes()["userId"] = userId
            next.handle(request)
        }

    /** Declared on the use case itself, so it runs after the filters of its group and sees what they left behind. */
    @Bean
    fun auditUpdate() =
        HandlerFilterFunction<ServerResponse, ServerResponse> { request, next ->
            log.info("filter auditUpdate: {} updates order {}", request.attributes()["userId"], request.pathVariable("id"))
            next.handle(request)
        }
}
