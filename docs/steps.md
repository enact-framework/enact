# Steps

A step is one unit of work in a use case. It takes a single input and returns a single output.
Every step has a unique name; use cases refer to steps by that name.

## Where steps live

`@StepDefinition` marks a class that defines steps. It is a `@Component`, so the class is picked up by
component scanning exactly like `@Service` — it just makes step definitions easy to find in a codebase, and
takes an optional bean name (`@StepDefinition("orders")`).

Any Spring bean still works: `@Service`, `@Component`, `@Bean` methods and manually registered beans are all
scanned for steps. `@StepDefinition` is the recommended stereotype, not a requirement.

## Method steps

Annotate a method of any Spring bean with `@Step`. The step name defaults to the method name.

=== "Kotlin"

    ```kotlin
    @StepDefinition
    class OrderPersistingService {
        @Step
        fun saveOrder(request: OrderRequest): OrderEntity { /* ... */ }

        @Step("findOrderById")
        fun findOrder(id: UUID): OrderEntity { /* ... */ }
    }
    ```

=== "Java"

    ```java
    @StepDefinition
    public class OrderPersistingService {
        @Step
        public OrderEntity saveOrder(OrderRequest request) { /* ... */ }

        @Step(name = "findOrderById")
        public OrderEntity findOrder(UUID id) { /* ... */ }
    }
    ```

- The method must have **zero or one** parameter. A method with no parameter takes `Unit`, so it can only start a use case or follow a step that returns nothing.
- A method returning nothing (`Unit` or `void`) produces `Unit`.
- Exceptions thrown by the method reach the caller as they are.

## Functional steps

A bean implementing `Function`, `Supplier`, `Consumer`, `Predicate` or a Kotlin function type can be a step
when the class is annotated with `@Step`. The step name defaults to the bean name.

=== "Kotlin"

    ```kotlin
    @Step("generateRandomUUID")
    @StepDefinition
    class RandomGeneration : () -> RandomGeneration.Response {
        override fun invoke() = Response(UUID.randomUUID())

        data class Response(val id: UUID)
    }
    ```

=== "Java"

    ```java
    @Step(name = "generateRandomUUID")
    @StepDefinition
    public class RandomGeneration implements Supplier<RandomGeneration.Response> {
        @Override
        public Response get() {
            return new Response(UUID.randomUUID());
        }

        public record Response(UUID id) {}
    }
    ```

## `Step` implementations

A bean implementing `io.enact.core.step.Step` is registered under its `name`:

=== "Kotlin"

    ```kotlin
    @StepDefinition
    class Normalize : Step.InOut<String, String> {
        override val name = "normalize"

        override fun execute(input: String) = input.trim().lowercase()
    }
    ```

=== "Java"

    ```java
    @StepDefinition
    public class Normalize implements Step.InOut<String, String> {
        @Override
        public String getName() {
            return "normalize";
        }

        @Override
        public String execute(String input) {
            return input.trim().toLowerCase();
        }
    }
    ```

`Step.In<I>` (no output) and `Step.Out<O>` (no input) are available for steps at the start or end of a use case.

## Rules

- Step names must be unique. A duplicate fails startup.
- A step referenced in YAML but not found fails startup with `Step '<name>' not found`.
