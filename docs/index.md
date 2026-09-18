# Enact

Enact is a Spring Boot extension for building applications around **use cases**.
You write small, focused **steps** as ordinary Spring beans, then wire them into use cases in YAML:

```yaml
enact:
  use-cases:
    - name: createOrder
      trigger:
        rest:
          method: POST
          path: /api/v1/orders
          status: 201
      steps:
        - step: validateOrderCreation
        - step: saveOrder
          settings:
            retry:
              max-retries: 3
        - step: mapOrderResponse
```

At startup Enact:

- resolves every step name to a step bean,
- checks that each step's output type matches the next step's input type, and fails fast if it doesn't,
- registers each use case as a Spring bean you can inject, and
- optionally exposes it as an HTTP endpoint.

!!! warning "Alpha"
    `0.0.1-alpha` is an early preview. APIs and the YAML schema may change before 1.0.

## Modules

| Artifact | Purpose |
|---|---|
| `io.github.gvart:enact-core` | Step and use case model, validation, execution |
| `io.github.gvart:enact-starter` | Spring Boot auto-configuration, YAML loading, use case injection |
| `io.github.gvart:enact-starter-web` | HTTP (REST) trigger for Spring MVC applications |

## Requirements

- Java 25
- Spring Boot 4.0
- Kotlin 2.3 (Java applications work too, but the examples use Kotlin)

Next: [Getting started](getting-started.md).
