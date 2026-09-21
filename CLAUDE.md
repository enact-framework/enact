# Enact

A Spring Boot extension that lets developers build applications around use cases defined in YAML.

## Concept

Enact follows a use-case-oriented approach: developers define steps as Spring beans, then wire them into use cases declaratively in YAML. A use case is a **typed directed graph** of steps: each step says where its inputs come from, so steps can fan out, fan in, run only under a condition, run at once or run aside. The framework checks every edge at startup, registers each use case as an injectable `UseCase<Input, Output>` bean, and can expose it through a trigger (HTTP for now).

## Architecture

- **`enact-core`** — `@Step` annotation, `Step`/`UseCase` model, step discovery (`StepDiscoveryBeanPostProcessor`), graph resolution and validation (`UseCaseGraph`, `StepNode`, `Binding`, `Fallback`), execution with retry/cache (`RuntimeUseCaseContainer`, `StepInvoker`, `StepScheduler`), trigger SPI (`TriggerHandler`, `TriggerRegistration`), Micrometer observations (`io.enact.core.observation`)
- **`enact-starter`** — Spring Boot auto-configuration: binds `enact.*` properties, reads definition files with Jackson (`DefinitionReader`, `Definitions`, `DefinitionMapper`), registers use case beans (`DefinitionLoaderConfiguration`), generic-aware injection (`UseCaseAutowireCandidateResolver`), trigger activation
- **`enact-starter-web`** — REST trigger (`RestTriggerHandler`) built on Spring MVC functional routes
- **`enact-demo`** — runnable sample app (not published)

## Steps

- `@StepDefinition` — `@Component` meta-annotation marking a class that defines steps (optional; any Spring bean is scanned)
- `@Step` on a method — name defaults to the method name. Several parameters are allowed; each is one typed edge, bound by name in the YAML. Parameter names come from kotlin-reflect (Kotlin) or `-parameters` (Java)
- `@Step` on a class implementing `Function`/`Supplier`/`Consumer`/`Predicate`/Kotlin function type — name defaults to the bean name
- Beans implementing `io.enact.core.step.Step` — registered under `name`

## Definition files

Use cases live in YAML files, read from `classpath:enact/` by default and listed under `enact.definitions`
(a file, a directory or an Ant pattern; `optional:` for a location that may be missing). A file declares
`use-cases` and `groups` at its root, without the `enact:` prefix.

`DefinitionReader` parses them with Jackson (kebab-case, Kotlin defaults, unknown key rejected with its
line), **not** through Spring's property sources. That is deliberate: a configuration property name holds a
map key in lower case, and a use case name, a `bind` entry and every binding key name something
case-sensitively. A use case name and a group name are each declared once across the files; a use case and a
group may share a name. See `docs/definition-files.md`.

Only `enact.enabled`, `enact.definitions` and `enact.observability` remain configuration properties.

## YAML Schema

```yaml
# application.yaml holds only Enact's own settings
enact:
  definitions: [classpath:enact/]  # optional; where the definition files are
  enabled: true
  observability:
    enabled: true                # optional; metrics and traces, on by default
```

```yaml
# a definition file: `use-cases` and `groups` at the root, without the `enact:` prefix
groups:                          # optional; filters shared by use cases of a group, resolved by the trigger
  default:                       # applies to use cases without `group`
    filters: [bearerAuth]        # bean names, outermost first (HandlerFilterFunction for the REST trigger)
  admin:
    filters: [bearerAuth, requireAdmin]
    observability:
      enabled: false             # optional; overrides the global setting for this group

use-cases:
  createOrder:                   # key = use case name = bean name
    description: ...
    group: admin                 # optional; defaults to `default`
    concurrent: true             # optional; runs steps that do not read each other at once
    output: buildInvoice         # optional; inferred as the only step nothing reads
    observability:
      enabled: true              # optional; overrides the group, then the global setting
    trigger:                     # optional, exactly one type; without it the use case is injection-only
      rest:                      # trigger type: the handler claiming it binds the block below
        method: POST
        path: /api/v1/orders/{customerId}   # {customerId} → input property customerId
        status: 201
        produces: application/json
        filters: [audit]         # optional; appended after the group's filters
        bind:                    # optional; defaults: path vars + matching query params by name, body → input
          tenantId: header:X-Tenant-Id   # header:/query:/path:/attribute:<name>, body, body:<json-pointer>
    steps:
      - step: validateOrder      # first step, one parameter, no `in` → reads $input
      - step: saveOrder
        settings:                # per use case; overrides retry/cache declared on @Step
          retry: { max-retries: 3, delay: 100ms }
          cache: { name: orders, key: "#input.id" }
      - step: priceOrder
        in: $validateOrder       # reaches back instead of reading the step above
      - step: findCoupon
        in: $validateOrder
      - step: applyDiscount      # (order: Order, coupon: Coupon?) -> Order
        in: { order: $priceOrder, coupon: $findCoupon }
        when: "#coupon != null"  # SpEL; inputs readable by parameter name
        # skipped → `order` passes through, inferred as the only input fitting the output
      - step: buildInvoice       # fan-in; the sink
        in: { order: $saveOrder, price: $applyDiscount }
      - step: notify
        id: notifyOps            # a step used twice needs an id
        in: { invoice: $buildInvoice }
        side: true               # off-thread, never waited for, never read
```

A reference starts with `$`: `$input` is the use case's input, `$<step id>` another step's output, and a
step may only bind to one declared above it — so declaration order is a topological order and a cycle cannot
be written. Without `$` a value is a literal, which is what makes `else` unambiguous. A step taking one
parameter and no `in` reads the step declared before it. `else:` gives what a conditional step yields when
skipped: `$<parameter>` to pass an input through, or a literal read into the output type at startup.

There is no `break`: a step that must stop the use case throws.

## Validation Rules (startup-time)

**Steps and edges**
1. Every step reference resolves to a registered step, and step ids are unique within a use case
2. Every edge's producer type is assignable to the consumer parameter's type, generics included
   (`ResolvableType`, so `List<Order>` does not feed `List<String>`, while `ArrayList<Order>` feeds `List<Order>`)
3. Every `in` key is a parameter of the step, every parameter is bound, and a step binds only to one declared above it
4. Every step reading `$input` expects the same type; that type is the use case's input

**Graph shape**
5. Exactly one step whose output nothing reads — ignoring steps that run aside — or an explicit `output`
6. Nothing binds to a step that runs aside
7. A conditional step that is read has a determinate `else`: declared, or inferred as the only input that can
   stand in for its output

**Everything else**
8. A step with `cache` settings requires a `CacheManager` bean that knows the cache name
9. REST `bind` keys must be input properties, `path:` sources must exist in the path, and every path variable must bind to a property
10. A use case `group` must be declared, and every filter name must be a bean of the kind the trigger expects
11. A use case declares at most one trigger, whose type has a registered `TriggerHandler`
12. A use case name and a group name are each declared once across the definition files; a location matches at
    least one YAML file unless it is `optional:`; a definition file declares nothing but `use-cases` and `groups`

## Observability

`RuntimeUseCaseContainer` and `StepInvoker` record the `enact.use.case` and `enact.step` observations (metrics,
plus spans when a tracer is present). Whether a use case is observed is decided at startup in
`DefinitionLoaderConfiguration`: a use case that is turned off is built with `ObservationRegistry.NOOP`, so
nothing is checked at execution time. Without Actuator there is no `ObservationRegistry` bean and nothing is
recorded. See `docs/observability.md`.

## Concurrency

`concurrent: true` on a use case runs each level of the graph at once; a level holds the steps whose inputs
are all ready. Off by default, because a step moved off the caller's thread leaves any surrounding
`@Transactional`, `SecurityContext` and MDC behind. Steps run on the application's `AsyncTaskExecutor` bean
when there is one, virtual threads otherwise, and a Micrometer `ContextSnapshot` is restored around each one
so spans keep nesting. A failure stops the next level; steps already running are awaited, not interrupted.

`side: true` on a step is independent of `concurrent`: it always runs off-thread, is never waited for and
never read, and its failure is logged and recorded rather than reaching the caller.

## Triggers

Trigger types are discovered from the configuration, so a starter adds one without changing core. `TriggerProperties`
names only the types Enact ships (`rest`), so that editors complete and validate them. Nothing reads it at runtime.

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
- Future extensions (more triggers, an Actuator endpoint, compile-time type evaluation) will come later; see `docs/roadmap.md`, don't design for them now
