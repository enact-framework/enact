package io.enact.core.annotation

import org.springframework.core.annotation.AliasFor
import org.springframework.stereotype.Component

/**
 * Marks a class as a Spring component that defines steps, either as [Step]-annotated methods or as a
 * functional step class. Use it in place of `@Component`/`@Service` to make step definitions easy to find.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Component
annotation class StepDefinition(
    /** Bean name, as with `@Component`. */
    @get:AliasFor(annotation = Component::class)
    val value: String = "",
)
