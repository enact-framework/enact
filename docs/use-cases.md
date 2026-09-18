# Use cases

A use case is an ordered list of steps, defined under `enact.use-cases`:

```yaml
enact:
  use-cases:
    - name: createOrder            # bean name; must be unique
      description: Validates and stores a new order
      trigger: { ... }             # optional, see "HTTP trigger"
      steps:
        - step: validateOrderCreation
        - step: saveOrder
          settings: { ... }        # optional, see "Retry and cache"
        - step: mapOrderResponse
```

The input of the use case is the input of its first step, and its output is the output of its last step.

## Validation

Enact checks every use case when it is created during startup:

- it has at least one step,
- every referenced step exists, and
- each step's output type equals the next step's input type.

If any check fails, the application does not start. The error names the use case and steps involved.

## Injecting a use case

Every use case is a Spring bean of type `UseCase<Input, Output>`. You can inject it by its type:

```kotlin
@RestController
class OrderController(
    private val createOrder: UseCase<OrderRequest, OrderResponse>,
) {
    @PostMapping("/orders")
    fun create(@RequestBody payload: OrderRequest): OrderResponse = createOrder.execute(payload)
}
```

Enact matches the generic types against the use case's actual input and output types. If several use cases
have the same types, pick one by parameter name (the use case name) or with `@Qualifier("createOrder")`.

A use case without a `trigger` is only available through injection.

## Disabling Enact

```yaml
enact:
  enabled: false
```
