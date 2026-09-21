# Observability

Enact records a [Micrometer observation](https://docs.micrometer.io/micrometer/reference/observation.html)
around every use case and every step it runs. Each observation produces a metric, and a span when a tracer is
on the classpath.

## Setup

Nothing to add to Enact. Observations go into the application's `ObservationRegistry`, which Spring Boot
publishes when Actuator is present:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-actuator")
```

Without Actuator there is no registry. Enact then falls back to `ObservationRegistry.NOOP` and records nothing.
The observation is skipped before its context is created, so it costs nothing at runtime.

Traces also need a tracer. Enact ships none, so add a bridge and an exporter and the same observations become
spans:

```kotlin
implementation("io.micrometer:micrometer-tracing-bridge-otel")
implementation("io.opentelemetry:opentelemetry-exporter-otlp")
```

## What is recorded

| Observation | Covers | Tags |
|---|---|---|
| `enact.use.case` | the whole use case, from the first step's input to the last step's output | `enact.use.case.name`, `enact.group`, `error` |
| `enact.step` | one step, including its cache lookup and every retry attempt | `enact.step.name`, `enact.use.case.name`, `enact.group`, `enact.cache`, `error` |

* `enact.group` is the use case's group, or `default` when it declares none.
* `enact.cache` is `hit`, `miss`, or `none` when the step has no cache. Without this tag a cached step would
  mix fast hits and slow misses in one timer, and the average would mean nothing.
* `error` is added by Micrometer itself. It holds the exception's simple name, or `none`.

A timer carries count, total time and max, so one timer tells you both how often a use case ran and how long it
took.

Steps are observed inside the use case's scope, so in a trace each step is a child span of its use case:

```text
createOrder                  42ms
  validateOrderCreation       2ms
  saveOrder                  38ms
  mapOrderResponse            1ms
```

Injected use cases are observed too. A use case called from a `@Scheduled` job gets the same timer and spans as
one behind an HTTP endpoint.

!!! note
    The REST trigger adds nothing of its own. Spring Boot already records `http.server.requests` for Enact's
    routes, tagged with the URI pattern such as `/api/v1/orders/{id}`. That timer measures the HTTP exchange,
    while `enact.use.case` measures the execution, so the two answer different questions.

## Percentiles

Percentiles come from Spring Boot's timer configuration. They work for every use case, whatever trigger it has:

```yaml
management:
  metrics:
    distribution:
      percentiles:
        enact.use.case: 0.95, 0.99     # computed in the application
      percentiles-histogram:
        enact.use.case: true           # buckets, aggregated in the backend
```

!!! warning
    `percentiles-histogram` publishes one series per bucket. That is usually 30 to 70 extra series for each use
    case and each step, so turn it on only for what you chart.

## Turning it off

Observability is on by default. It can be turned off globally, for a group, or for a single use case. The
narrower setting wins: a use case falls back to its group, and a group to `enact.observability`.

```yaml
enact:
  observability:
    enabled: true            # global default
  groups:
    internal:
      observability:
        enabled: false       # nothing in this group is measured
  use-cases:
    - name: healthPing
      observability:
        enabled: false       # this one is not measured
      steps:
        - step: ping
    - name: auditExport
      group: internal
      observability:
        enabled: true        # but this one is, despite its group
      steps:
        - step: exportAudit
```

This is resolved once at startup. A use case that is turned off is built with `ObservationRegistry.NOOP`, so
there is no check left at execution time.

Spring Boot can also silence Enact by observation name, without touching the `enact` configuration:

```yaml
management:
  observations:
    enable:
      enact: false           # both observations
      enact.step: false      # only the step ones, keeping the use case timers
```

## Custom names and tags

Publish a `UseCaseObservationConvention` or `StepObservationConvention` bean to replace the defaults. It applies
to every use case, so read the context if you want to treat one of them differently:

```kotlin
@Bean
fun tenantAwareUseCaseConvention() =
    object : UseCaseObservationConvention {
        override fun getName() = DefaultUseCaseObservationConvention.getName()

        override fun getContextualName(context: UseCaseObservationContext) = context.useCaseName

        override fun getLowCardinalityKeyValues(context: UseCaseObservationContext) =
            DefaultUseCaseObservationConvention
                .getLowCardinalityKeyValues(context)
                .and("tenant", TenantContext.current())
    }
```

For tags that are not specific to Enact, use Spring Boot instead: an `ObservationFilter` bean, or
`management.observations.key-values.*` to tag everything the application records.
