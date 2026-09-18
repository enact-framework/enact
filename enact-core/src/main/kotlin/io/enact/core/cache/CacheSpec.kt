package io.enact.core.cache

data class CacheSpec(
    /** Name of the cache in the application's `CacheManager`. */
    val name: String,
    /** SpEL expression evaluated against the step input (`#input`). Defaults to the input itself. */
    val key: String? = null,
)
