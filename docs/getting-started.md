# Getting started

## 1. Add the dependency

For a Spring MVC application that exposes use cases over HTTP:

<!-- x-release-please-start-version -->
```kotlin
dependencies {
    implementation("io.github.enact-framework:enact-starter-web:0.0.1-alpha")
}
```
<!-- x-release-please-end -->

If you only need injectable use cases (no HTTP endpoints), use `io.github.enact-framework:enact-starter` instead.

!!! tip "Snapshots"
    Every change merged to `main` is published as a snapshot (for example `0.0.2-SNAPSHOT`).
    To try unreleased changes, add the snapshot repository:

    ```kotlin
    repositories {
        mavenCentral()
        maven("https://central.sonatype.com/repository/maven-snapshots/")
    }
    ```

## 2. Write steps

A step is a method annotated with `@Step` that takes zero or one argument:

```kotlin
@Service
class OrderValidationService {
    @Step
    fun validateOrderCreation(request: OrderRequest): OrderRequest {
        require(request.amount > 0) { "Amount should be greater than zero." }
        return request
    }
}

@Service
class OrderPersistingService {
    @Step
    fun saveOrder(request: OrderRequest): OrderEntity = TODO("persist")
}

@Service
class OrderResponseMapperService {
    @Step
    fun mapOrderResponse(order: OrderEntity): OrderResponse = OrderResponse(order.id)
}
```

## 3. Define the use case

In `application.yaml`:

```yaml
enact:
  use-cases:
    - name: createOrder
      description: Validates and stores a new order
      trigger:
        rest:
          method: POST
          path: /api/v1/orders
          status: 201
      steps:
        - step: validateOrderCreation
        - step: saveOrder
        - step: mapOrderResponse
```

## 4. Run it

```sh
curl -X POST localhost:8080/api/v1/orders \
  -H 'Content-Type: application/json' \
  -d '{"amount": 1.23, "productCount": 2}'
```

The [`enact-demo`](https://github.com/enact-framework/enact/tree/main/enact-demo) module is a complete, runnable example.
