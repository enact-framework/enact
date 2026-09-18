# Steps

A step is one unit of work in a use case. It takes a single input and returns a single output.
Every step has a unique name; use cases refer to steps by that name.

## Method steps

Annotate a method of any Spring bean with `@Step`. The step name defaults to the method name.

```kotlin
@Service
class OrderPersistingService {
    @Step
    fun saveOrder(request: OrderRequest): OrderEntity { /* ... */ }

    @Step("findOrderById")
    fun findOrder(request: OrderIdRequest): OrderEntity { /* ... */ }
}
```

- The method must have **zero or one** parameter. A method with no parameter takes `Unit`, so it can only start a use case or follow a step that returns nothing.
- A method returning nothing (`Unit` or `void`) produces `Unit`.
- Exceptions thrown by the method reach the caller as they are.

## Functional steps

A bean implementing `Function`, `Supplier`, `Consumer`, `Predicate` or a Kotlin function type can be a step
when the class is annotated with `@Step`. The step name defaults to the bean name.

```kotlin
@Step("generateRandomUUID")
@Component
class RandomGeneration : () -> RandomGeneration.Response {
    override fun invoke() = Response(UUID.randomUUID())

    data class Response(val id: UUID)
}
```

## `Step` implementations

A bean implementing `io.enact.core.step.Step` is registered under its `name`:

```kotlin
@Component
class Normalize : Step.InOut<String, String> {
    override val name = "normalize"

    override fun execute(input: String) = input.trim().lowercase()
}
```

`Step.In<I>` (no output) and `Step.Out<O>` (no input) are available for steps at the start or end of a use case.

## Rules

- Step names must be unique. A duplicate fails startup.
- A step referenced in YAML but not found fails startup with `Step '<name>' not found`.
