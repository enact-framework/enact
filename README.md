# Enact

Build Spring Boot applications around **use cases** defined in YAML.

Write small steps as ordinary Spring beans, wire them into use cases in `application.yaml`, and let Enact
validate the chain at startup, register each use case as an injectable bean, and expose it over HTTP.

```kotlin
@Service
class OrderService {
    @Step fun validateOrder(request: OrderRequest): OrderRequest = request.also { require(it.amount > 0) }
    @Step fun saveOrder(request: OrderRequest): OrderEntity = TODO("persist")
    @Step fun toResponse(order: OrderEntity): OrderResponse = OrderResponse(order.id)
}
```

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
        - step: validateOrder
        - step: saveOrder
          settings:
            retry:
              max-retries: 3
        - step: toResponse
```

> **Status:** `0.0.1-alpha`, an early preview. APIs may change before 1.0.

## Installation

```kotlin
dependencies {
    implementation("io.github.enact-framework:enact-starter-web:0.0.1-alpha") // or enact-starter without HTTP
}
```

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

Publishing to Maven Central uses [gradle-maven-publish-plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/central/).
Provide these as environment variables, then run `./gradlew publishToMavenCentral`:

```
ORG_GRADLE_PROJECT_mavenCentralUsername
ORG_GRADLE_PROJECT_mavenCentralPassword
ORG_GRADLE_PROJECT_signingInMemoryKey
ORG_GRADLE_PROJECT_signingInMemoryKeyPassword
```

## License

[Apache License 2.0](LICENSE)
