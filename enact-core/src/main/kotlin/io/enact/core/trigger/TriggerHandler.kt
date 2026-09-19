package io.enact.core.trigger

/**
 * Exposes use cases through a transport: HTTP, Kafka, SQS, ...
 *
 * One implementation per trigger type, published as a bean. At startup the framework binds the YAML under
 * `trigger.<triggerType>` of every use case to [definitionType] and calls [register] for it, then [start] once
 * all use cases are registered and [stop] when the application shuts down.
 *
 * @param D the trigger definition, a class Spring Boot can bind configuration properties to.
 */
interface TriggerHandler<D : Any> {
    /** Key under `trigger:` selecting this handler, e.g. `rest`. Lowercase, unique across handlers. */
    val triggerType: String

    /** Type the YAML under `trigger.<triggerType>` binds to; created with its defaults when the YAML is empty. */
    val definitionType: Class<D>

    /** Registers a use case. Throw to fail the application startup on an invalid definition. */
    fun register(registration: TriggerRegistration<D>)

    /** Opens routes, consumers or connections. Called once, after every use case is registered. */
    fun start() {}

    /** Closes what [start] opened. Called when the application shuts down. */
    fun stop() {}
}
