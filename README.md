# Enact

Build Spring Boot applications around **use cases** defined in YAML.

Write small steps as ordinary Spring beans, wire them into use cases in a YAML file per domain, and let Enact
check every connection at startup, register each use case as an injectable bean, and expose it over HTTP.

A use case is a graph, not a list: a step says where its inputs come from, so steps can fan out, fan in, run
only under a condition, run at once, or run aside. Every edge is type-checked before the application starts.

```kotlin
@StepDefinition
class OrderService {
    @Step fun validateOrder(request: OrderRequest): OrderRequest = request.also { require(it.amount > 0) }
    @Step fun saveOrder(request: OrderRequest): OrderEntity = TODO("persist")
    @Step fun priceOrder(request: OrderRequest): Money = TODO("price")
    @Step fun toResponse(order: OrderEntity, price: Money): OrderResponse = OrderResponse(order.id, price)
    @Step fun notifyOps(response: OrderResponse) = TODO("send")
}
```

```yaml
# src/main/resources/enact/orders.yaml
use-cases:
  createOrder:
    concurrent: true            # steps that do not read each other run at once
    trigger:
      rest:
        method: POST
        path: /api/v1/orders
        status: 201
    steps:
      - step: validateOrder     # reads the use case's input
      - step: saveOrder
        settings:
          retry:
            max-retries: 3
      - step: priceOrder
        in: $validateOrder      # reaches back, so it runs alongside saveOrder
      - step: toResponse        # two inputs, bound by parameter name
        in: { order: $saveOrder, price: $priceOrder }
      - step: notifyOps
        side: true              # started, never waited for
```

> **Status:** `0.0.1-alpha`, an early preview. APIs may change before 1.0.

## Installation

<!-- x-release-please-start-version -->
```kotlin
dependencies {
    implementation("io.github.enact-framework:enact-starter-web:0.0.1-alpha") // or enact-starter without HTTP
}
```
<!-- x-release-please-end -->

Requires Java 25 and Spring Boot 4.0.

## Documentation

Read the full guide at **https://enact-framework.github.io/enact/**. Its sources are in [`docs/`](docs).

A runnable example lives in [`enact-demo`](enact-demo) (`./gradlew :enact-demo:bootRun`, then use [`requests.http`](enact-demo/requests.http)).

## Building

```sh
./gradlew build            # compile, test, check formatting
./gradlew spotlessApply    # fix formatting
./gradlew publishToMavenLocal -PsignAllPublications=false
```

## Releasing

Releases are automated with [release-please](https://github.com/googleapis/release-please) from the semantic commit messages:

1. Every push to `main` updates an open **release PR** with the next version and `CHANGELOG.md`.
2. Merging the release PR tags the commit, creates the GitHub release and publishes the artifacts to Maven Central
   (`.github/workflows/release.yml`).

Every push to `main` also publishes the current `VERSION_NAME` from `gradle.properties` (always a `-SNAPSHOT`) to
`https://central.sonatype.com/repository/maven-snapshots/` (`.github/workflows/snapshot.yml`). Bump it after a release.

Both workflows need the repository secrets `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY` and
`SIGNING_KEY_PASSWORD`. To publish manually, export them as `ORG_GRADLE_PROJECT_mavenCentralUsername`,
`ORG_GRADLE_PROJECT_mavenCentralPassword`, `ORG_GRADLE_PROJECT_signingInMemoryKey` and
`ORG_GRADLE_PROJECT_signingInMemoryKeyPassword`, then run `./gradlew publishToMavenCentral`.

## License

[Apache License 2.0](LICENSE)
