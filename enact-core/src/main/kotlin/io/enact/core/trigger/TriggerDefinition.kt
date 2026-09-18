package io.enact.core.trigger

interface TriggerDefinition {
    val type: String
}

/** REST HTTP endpoint trigger. Request body, query parameters and path variables are bound to the use case input. */
data class RestTriggerDefinition(
    /** HTTP method (GET, POST, PUT, PATCH, DELETE). */
    val method: String = "",
    /** URL path for the REST endpoint. May contain path variables, e.g. `/orders/{id}`. */
    val path: String = "",
    /** Media type the endpoint produces. */
    val produces: String = "application/json",
    /** HTTP status code for successful responses. */
    val status: Int = 200,
) : TriggerDefinition {
    override val type: String = "rest"
}
