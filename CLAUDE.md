# Enact

A Spring Boot extension that lets developers build applications around use cases defined in YAML.

## Concept

Enact follows a use-case-oriented approach: developers define steps as Spring beans, then wire them into use cases declaratively in YAML. The framework validates definitions at startup, registers each use case as an injectable `UseCase<Input, Output>` bean, and can expose it through a trigger (HTTP for now).

## Architecture

- **`enact-core`** — `@Step` annotation, `Step`/`UseCase` model, step discovery (`StepDiscoveryBeanPostProcessor`), validation, execution with retry/cache (`RuntimeUseCaseContainer`, `StepInvoker`), trigger SPI
- **`enact-starter`** — Spring Boot auto-configuration: binds `enact.*` properties, registers use case beans (`DefinitionLoaderConfiguration`), generic-aware injection (`UseCaseAutowireCandidateResolver`), trigger activation
- **`enact-starter-web`** — REST trigger (`RestTriggerHandler`) built on Spring MVC functional routes
- **`enact-demo`** — runnable sample app (not published)

## Steps

- `@StepDefinition` — `@Component` meta-annotation marking a class that defines steps (optional; any Spring bean is scanned)
- `@Step` on a method (0 or 1 parameter) — name defaults to the method name
- `@Step` on a class implementing `Function`/`Supplier`/`Consumer`/`Predicate`/Kotlin function type — name defaults to the bean name
- Beans implementing `io.enact.core.step.Step` — registered under `name`

## YAML Schema

```yaml
enact:
  enabled: true
  groups:                        # optional; REST filters shared by use cases of a group
    default:                     # applies to use cases without `group`
      filters: [bearerAuth]      # HandlerFilterFunction bean names, outermost first
    admin:
      filters: [bearerAuth, requireAdmin]
  use-cases:
    - name: createOrder          # use case bean name
      description: ...
      group: admin               # optional; defaults to `default`
      trigger:                   # optional; without it the use case is injection-only
        rest:
          method: POST
          path: /api/v1/orders/{customerId}   # {customerId} → input property customerId
          status: 201
          produces: application/json
          filters: [audit]       # optional; appended after the group's filters
          bind:                  # optional; defaults: path vars + matching query params by name, body → input
            tenantId: header:X-Tenant-Id   # header:/query:/path:/attribute:<name>, body, body:<json-pointer>
      steps:
        - step: validateOrderCreation
        - step: saveOrder
          settings:              # per use case; overrides retry/cache declared on @Step
            retry:
              max-retries: 3
              delay: 100ms
            cache:
              name: orders       # needs a CacheManager bean
              key: "#input.id"   # SpEL, optional
        - step: mapOrderResponse
```

## Validation Rules (startup-time)

1. Every step reference in YAML must resolve to a registered step
2. Each step's output type must equal the next step's input type
3. Step names must be unique
4. A step with `cache` settings requires a `CacheManager` bean that knows the cache name
5. REST `bind` keys must be input properties, `path:` sources must exist in the path, and every path variable must bind to a property
6. A use case `group` must be declared in `enact.groups`, and every filter name must be a `HandlerFilterFunction` bean

## Tech Stack

- Kotlin 2.3, Java 25 toolchain
- Spring Boot 4.0 / Spring Framework 7 (core retry: `org.springframework.core.retry`)
- Jackson 3 (`tools.jackson`)
- Gradle (Kotlin DSL), multi-module; conventions in `buildSrc` (`kotlin-jvm`, `enact-publish`)
- Package namespace: `io.enact`; Maven group `io.github.enact-framework` (repo github.com/enact-framework/enact)

## Build

```sh
./gradlew build           # build + tests + spotless (ktlint)
./gradlew spotlessApply   # fix formatting
```

Docs: MkDocs Material in `docs/` + `mkdocs.yml`, deployed to GitHub Pages by `.github/workflows/docs.yml`.

## Conventions

- Semantic commits
- Keep it simple — no over-engineering
- Future extensions (sub-steps, parallel execution, fire-and-forget, more triggers) will come later; see `docs/roadmap.md`, don't design for them now
