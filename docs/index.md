---
hide:
  - navigation
  - toc
---

<div class="enact-hero" markdown>

![Enact logo](assets/logo-hero.png)

<div markdown>

# Enact

Build Spring Boot applications around **use cases**. Write small steps as ordinary Spring beans,
wire them together in YAML, and let Enact check the chain at startup.

[Get started :material-arrow-right:](getting-started.md){ .md-button .md-button--primary }
[View on GitHub :fontawesome-brands-github:](https://github.com/enact-framework/enact){ .md-button }

</div>

</div>

!!! warning "Alpha"
    `0.0.1-alpha` is an early preview. APIs and the YAML schema may change before 1.0.

<div class="grid cards" markdown>

-   :material-puzzle-outline:{ .lg .middle } __Steps are beans__

    ---

    Annotate a method, or implement a function interface. No base classes, no framework lock-in.

    [:octicons-arrow-right-24: Steps](steps.md)

-   :material-file-tree-outline:{ .lg .middle } __Use cases in YAML__

    ---

    Wire steps into use cases in `application.yaml`. Every use case becomes an injectable `UseCase<I, O>` bean.

    [:octicons-arrow-right-24: Use cases](use-cases.md)

-   :material-shield-check-outline:{ .lg .middle } __Fails fast__

    ---

    Missing steps, duplicate names, and mismatched types between steps stop the application at startup, not in production.

    [:octicons-arrow-right-24: Validation](use-cases.md#validation)

-   :material-refresh:{ .lg .middle } __Retry and cache__

    ---

    Add retry with backoff or caching to any step, per use case, without changing its code.

    [:octicons-arrow-right-24: Retry and cache](step-settings.md)

-   :material-api:{ .lg .middle } __HTTP trigger__

    ---

    Expose a use case as a REST endpoint with a few lines of YAML: method, path, and status.

    [:octicons-arrow-right-24: HTTP trigger](http-trigger.md)

-   :material-language-kotlin:{ .lg .middle } __Kotlin and Java__

    ---

    Built on Spring Boot 4 and Java 25. The examples show both Kotlin and Java.

    [:octicons-arrow-right-24: Getting started](getting-started.md)

</div>

## At a glance

```yaml title="application.yaml"
enact:
  use-cases:
    - name: createOrder # (1)!
      trigger:
        rest: # (2)!
          method: POST
          path: /api/v1/orders
          status: 201
      steps: # (3)!
        - step: validateOrderCreation
        - step: saveOrder
          settings:
            retry:
              max-retries: 3 # (4)!
        - step: mapOrderResponse
```

1.  The use case is registered as a Spring bean named `createOrder`.
2.  Optional. Without a trigger, the use case is only available through injection.
3.  Steps run in order. Each step's output type must match the next step's input type.
4.  Settings apply to this use case only. The same step can be retried here and not elsewhere.

At startup Enact:

- resolves every step name to a step bean,
- checks that each step's output type matches the next step's input type, and fails fast if it doesn't,
- registers each use case as a Spring bean you can inject, and
- optionally exposes it as an HTTP endpoint.

## Modules

| Artifact                                      | Purpose                                                          |
|-----------------------------------------------|------------------------------------------------------------------|
| `io.github.enact-framework:enact-core`        | Step and use case model, validation, execution                   |
| `io.github.enact-framework:enact-starter`     | Spring Boot auto-configuration, YAML loading, use case injection |
| `io.github.enact-framework:enact-starter-web` | HTTP (REST) trigger for Spring MVC applications                  |

## Requirements

- Java 25
- Spring Boot 4.0
- Kotlin 2.3 or Java
