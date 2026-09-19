package io.enact.core.annotation

/** Cache settings for a [Step]. An empty [name] means the step is not cached. */
@Target()
@Retention(AnnotationRetention.RUNTIME)
annotation class Cached(
    /** Name of the cache in the application's `CacheManager`. */
    val name: String = "",
    /** SpEL expression evaluated against the step input (`#input`). Defaults to the input itself. */
    val key: String = "",
)
