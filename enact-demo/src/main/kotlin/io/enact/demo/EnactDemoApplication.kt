package io.enact.demo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching

@SpringBootApplication
@EnableCaching
class EnactDemoApplication

fun main(args: Array<String>) {
    runApplication<EnactDemoApplication>(*args)
}
