package io.enact.core.configuration

import org.springframework.context.annotation.Import

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(EnactConfiguration::class)
annotation class EnableEnact
