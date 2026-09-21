# Steps

A step is one unit of work in a use case. It takes its inputs and returns a single output.
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

- A method with no parameter reads nothing, so a use case never binds anything to it.
- A method returning nothing (`Unit` or `void`) produces `Unit`.
- Exceptions thrown by the method reach the caller as they are.

### Several parameters

A method step may take several parameters. Each one is an input the use case binds by name, which is how two
steps feed a third:

=== "Kotlin"

    ```kotlin
    @StepDefinition
    class InvoicingService {
        @Step
        fun buildInvoice(order: OrderEntity, price: Money): Invoice { /* ... */ }
    }
    ```

=== "Java"

    ```java
    @StepDefinition
    public class InvoicingService {
        @Step
        public Invoice buildInvoice(OrderEntity order, Money price) { /* ... */ }
    }
    ```

```yaml
- step: buildInvoice
  in:
    order: $saveOrder # (1)!
    price: $applyDiscount
```

1.  The key is the parameter name, the value is the step whose output feeds it. See
    [Binding inputs](use-cases.md#binding-inputs).

Only a step written as an annotated method can take several inputs. A functional step and a `Step`
implementation take one, so they stay usable wherever a single value is passed along.

!!! warning "Parameter names must be readable"
    Enact reads them with Spring's `DefaultParameterNameDiscoverer`: from kotlin-reflect for a Kotlin method,
    from the class file for a method compiled with `-parameters` (`-java-parameters` for Kotlin). A
    multi-parameter step whose names cannot be read fails at startup saying so; a step with a single parameter
    is unaffected, since there is nothing to tell apart.

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

Its single input is named `input`, which is the name `in`, `when` and a cache `key` use for it.

## Rules

- Step names must be unique. A duplicate fails startup.
- A step referenced in YAML but not found fails startup with `Step '<name>' not found`.
- A step declares its inputs; the use case decides where each of them comes from. See
  [Use cases](use-cases.md).
