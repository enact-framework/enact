# Getting started

## 1. Add the dependency

For a Spring MVC application that exposes use cases over HTTP:

<!-- x-release-please-start-version -->
=== "Gradle (Kotlin)"

    ```kotlin title="build.gradle.kts"
    dependencies {
        implementation("io.github.enact-framework:enact-starter-web:0.0.1-alpha")
    }
    ```

=== "Maven"

    ```xml title="pom.xml"
    <dependency>
        <groupId>io.github.enact-framework</groupId>
        <artifactId>enact-starter-web</artifactId>
        <version>0.0.1-alpha</version>
    </dependency>
    ```

<!-- x-release-please-end -->

If you only need injectable use cases (no HTTP endpoints), use `io.github.enact-framework:enact-starter` instead.

??? tip "Snapshots"
    Every change merged to `main` is published as a snapshot (for example `0.0.2-SNAPSHOT`).
    To try unreleased changes, add the snapshot repository:

    === "Gradle (Kotlin)"

        ```kotlin
        repositories {
            mavenCentral()
            maven("https://central.sonatype.com/repository/maven-snapshots/")
        }
        ```

    === "Maven"

        ```xml
        <repositories>
            <repository>
                <id>central-snapshots</id>
                <url>https://central.sonatype.com/repository/maven-snapshots/</url>
                <snapshots><enabled>true</enabled></snapshots>
            </repository>
        </repositories>
        ```

## 2. Write steps

A step is a method annotated with `@Step` that takes zero or one argument:

=== "Kotlin"

    ```kotlin
    @StepDefinition
    class OrderValidationService {
        @Step
        fun validateOrderCreation(request: OrderRequest): OrderRequest {
            require(request.amount > 0) { "Amount should be greater than zero." }
            return request
        }
    }

    @StepDefinition
    class OrderPersistingService {
        @Step
        fun saveOrder(request: OrderRequest): OrderEntity = TODO("persist")
    }

    @StepDefinition
    class OrderResponseMapperService {
        @Step
        fun mapOrderResponse(order: OrderEntity): OrderResponse = OrderResponse(order.id)
    }
    ```

=== "Java"

    ```java
    @StepDefinition
    public class OrderValidationService {
        @Step
        public OrderRequest validateOrderCreation(OrderRequest request) {
            if (request.amount() <= 0) {
                throw new IllegalArgumentException("Amount should be greater than zero.");
            }
            return request;
        }
    }

    @StepDefinition
    public class OrderPersistingService {
        @Step
        public OrderEntity saveOrder(OrderRequest request) {
            throw new UnsupportedOperationException("TODO: persist");
        }
    }

    @StepDefinition
    public class OrderResponseMapperService {
        @Step
        public OrderResponse mapOrderResponse(OrderEntity order) {
            return new OrderResponse(order.id());
        }
    }
    ```

## 3. Define the use case

```yaml title="application.yaml"
enact:
  use-cases:
    - name: createOrder
      description: Validates and stores a new order
      trigger:
        rest: # (1)!
          method: POST
          path: /api/v1/orders
          status: 201
      steps:
        - step: validateOrderCreation # (2)!
        - step: saveOrder
        - step: mapOrderResponse
```

1.  Exposes the use case as `POST /api/v1/orders`. Leave `trigger` out to use it only through injection.
2.  Steps are referenced by name. `OrderRequest → OrderRequest → OrderEntity → OrderResponse` is checked at startup.

## 4. Run it

```sh
curl -X POST localhost:8080/api/v1/orders \
  -H 'Content-Type: application/json' \
  -d '{"amount": 1.23, "productCount": 2}'
```

The [`enact-demo`](https://github.com/enact-framework/enact/tree/main/enact-demo) module is a complete, runnable example.
