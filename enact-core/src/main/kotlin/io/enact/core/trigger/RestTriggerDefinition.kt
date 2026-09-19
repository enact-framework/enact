package io.enact.core.trigger

/**
 * REST HTTP endpoint trigger, bound from `trigger.rest` and handled by `enact-starter-web`.
 *
 * By default, path variables and matching query parameters are bound to input properties of the same name and the
 * JSON body to the input itself. [bind] declares anything else.
 */
data class RestTriggerDefinition(
    /** HTTP method (GET, POST, PUT, PATCH, DELETE). */
    val method: String = "",
    /** URL path for the REST endpoint. May contain path variables, e.g. `/orders/{id}`. */
    val path: String = "",
    /**
     * Input property to request source: `header:<name>`, `query:<name>`, `path:<name>`, `attribute:<name>` (a
     * request attribute, e.g. set by a filter), `body` or `body:<json-pointer>`, e.g. `tenantId: header:X-Tenant-Id`.
     */
    val bind: Map<String, String> = emptyMap(),
    /** Names of `HandlerFilterFunction` beans applied after the filters of the use case's group. */
    val filters: List<String> = emptyList(),
    /** Media type the endpoint produces. */
    val produces: String = "application/json",
    /** HTTP status code for successful responses. */
    val status: Int = 200,
)
