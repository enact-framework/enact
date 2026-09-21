# HTTP trigger

With `enact-starter-web` on the classpath, a use case can be exposed as a Spring MVC endpoint:

```yaml
findOrder:
  trigger:
    rest:
      method: GET # (1)!
      path: /api/v1/orders/{id} # (2)!
      status: 200 # (3)!
      produces: application/json # (4)!
  steps:
    - step: findOrder
```

1.  `GET`, `POST`, `PUT`, `PATCH` or `DELETE`.
2.  Path variables such as `{id}` are bound to the use case input.
3.  Optional, defaults to `200`.
4.  Optional.

## Request binding

The use case input (the first step's parameter) is built from the request. Simple endpoints need no binding
configuration at all; `bind` covers everything else.

### Defaults

| Source | Bound to |
|---|---|
| Path variable `{name}` | the input property `name` |
| Query parameter `name` | the input property `name`, if the input has one; other parameters are ignored |
| JSON body | the input itself |
| Header | nothing; headers are only bound through `bind` |

For example, `POST /api/v1/customers/c-1/orders?express=true` with body `{"amount": 10}` and no `bind`:

=== "Kotlin"

    ```kotlin
    data class CreateOrder(
        val customerId: String,       // path variable
        val express: Boolean = false, // query parameter
        val amount: Double,           // body
    )
    ```

=== "Java"

    ```java
    public record CreateOrder(
        String customerId,  // path variable
        Boolean express,    // query parameter
        double amount       // body
    ) {}
    ```

A search endpoint needs no body at all: `GET /api/v1/orders?minAmount=10&limit=5` binds to
`data class OrderSearch(val minAmount: Double = 0.0, val limit: Int = 20)`.

### Declaring bindings

`bind` maps input properties to request sources:

```yaml
updateOrder:
  trigger:
    rest:
      method: PUT
      path: /api/v1/orders/{id} # (1)!
      bind:
        updatedBy: header:X-User-Id # (2)!
        notify: query:send-notification # (3)!
        changes: body # (4)!
        firstItem: body:/items/0 # (5)!
  steps:
    - step: updateOrder
```

1.  `id` still binds to the property `id` by name.
2.  A header. Header names are case-insensitive.
3.  A query parameter under a different name.
4.  The whole JSON body, bound to one property instead of the input itself.
5.  Part of the body, selected with a [JSON Pointer](https://datatracker.ietf.org/doc/html/rfc6901).

=== "Kotlin"

    ```kotlin
    data class UpdateOrderCommand(
        val id: UUID,
        val updatedBy: String,
        val notify: Boolean = false,
        val changes: OrderRequest,
        val firstItem: Item? = null,
    )
    ```

=== "Java"

    ```java
    public record UpdateOrderCommand(
        UUID id,
        String updatedBy,
        Boolean notify,
        OrderRequest changes,
        Item firstItem
    ) {}
    ```

| Source | Meaning |
|---|---|
| `header:<name>` | request header |
| `query:<name>` | query parameter |
| `path:<name>` | path variable, e.g. `reference: path:id` to bind `{id}` to a property named differently |
| `attribute:<name>` | request attribute, typically set by a [filter](#filters-and-groups) |
| `body` | the whole JSON body |
| `body:<pointer>` | part of the JSON body, e.g. `body:/customer/address` |

Once a property is declared in `bind`, the defaults no longer apply to it. Once any property binds from `body`,
the body is no longer bound to the input itself.

### Types, required values and lists

The collected values are converted to the input type with the application's Jackson `JsonMapper`, so strings
become numbers, booleans, `UUID`s, dates or enums, and custom deserializers apply.

- **Required or optional** comes from the input type. A non-null Kotlin property without a default is required.
  A nullable property or one with a default is optional. For Java records, use wrapper types (`Boolean`, `Long`)
  for optional values: a missing primitive silently becomes `0`/`false`.
- **Several values** (`?tag=a&tag=b`, repeated headers) bind to `List`, `Set` or array properties. A single-valued
  property receiving several values is rejected.

### Scalar input

If the first step takes a single value such as `UUID`, `Long`, `String` or `Duration` (anything Jackson does not
read as an object), it gets the path variable when the path has exactly one, and the body otherwise. In the first
case the body is not read:

=== "Kotlin"

    ```kotlin
    @Step
    fun findOrder(id: UUID): OrderEntity { /* ... */ } // GET /api/v1/orders/{id}
    ```

=== "Java"

    ```java
    @Step
    public OrderEntity findOrder(UUID id) { /* ... */ } // GET /api/v1/orders/{id}
    ```

To bind more than one value, use a data class or record.

### Errors

The request is answered with `400 Bad Request` when:

- a value cannot be converted, or a required value is missing,
- a property is bound from the path, a query parameter or `bind`, and the body also contains it. Values are
  never silently overwritten: a client cannot replace the `{customerId}` of the URL through the body,
- a single-valued property receives several values,
- the body is not a JSON object while the input is bound from it.

Enable `spring.mvc.problemdetails.enabled=true` to answer these with an RFC 9457 problem detail that explains the
cause.

Configuration mistakes fail at startup instead:

- a `bind` key that is not a property of the input,
- an unknown source, an invalid JSON Pointer, or `path:<name>` not present in the path,
- a path variable that matches no input property,
- the whole body bound to more than one property,
- `bind` or several path variables for a scalar input, or `bind` for a use case without input.

If the use case has no input (its first step takes no parameter), the request is not read.

## Filters and groups

Cross-cutting concerns such as authentication, tenant checks or auditing are handled by filters: plain Spring
`HandlerFilterFunction` beans that wrap the endpoint. Use cases belong to a `group`, and each group lists the
filters applied to its endpoints:

```yaml
enact:
  groups:
    default: # (1)!
      filters: [bearerAuth]
    admin:
      filters: [bearerAuth, requireAdmin] # (2)!
    public:
      filters: []
  use-cases:
    createOrder: # (3)!
      trigger:
        rest: { method: POST, path: /api/v1/orders }
      steps: [...]
    cancelOrder:
      group: admin
      trigger:
        rest:
          method: DELETE
          path: /api/v1/orders/{id}
          filters: [audit] # (4)!
          bind:
            cancelledBy: attribute:userId # (5)!
      steps: [...]
    generateId:
      group: public
      ...
```

1.  Applies to every use case without a `group`. Without a `default` group, such use cases have no filters.
2.  Filters run in list order; the first one is the outermost.
3.  No `group`, so the `default` group applies.
4.  Appended after the group's filters: `bearerAuth` → `requireAdmin` → `audit`.
5.  A value a filter stored as a request attribute.

A filter either rejects the request by returning a response, or passes it on with `next.handle(request)`. Filters
run before the request is bound to the use case input, so a rejected request is never read:

=== "Kotlin"

    ```kotlin
    @Bean
    fun bearerAuth() =
        HandlerFilterFunction<ServerResponse, ServerResponse> { request, next ->
            val userId = tokens.verify(request.headers().firstHeader(HttpHeaders.AUTHORIZATION))
                ?: return@HandlerFilterFunction ServerResponse.status(HttpStatus.UNAUTHORIZED).build()
            request.attributes()["userId"] = userId
            next.handle(request)
        }
    ```

=== "Java"

    ```java
    @Bean
    HandlerFilterFunction<ServerResponse, ServerResponse> bearerAuth() {
        return (request, next) -> {
            var userId = tokens.verify(request.headers().firstHeader(HttpHeaders.AUTHORIZATION));
            if (userId == null) return ServerResponse.status(HttpStatus.UNAUTHORIZED).build();
            request.attributes().put("userId", userId);
            return next.handle(request);
        };
    }
    ```

Configuration mistakes fail at startup: a use case referencing an unknown group, or a filter name that is not a
`HandlerFilterFunction` bean.

Enact endpoints are ordinary Spring MVC routes, so Spring Security (a `SecurityFilterChain` matching their paths)
works as well, either instead of filters or alongside them.

## Response

- The use case output is written as the response body with the configured `status`.
- If the use case returns nothing (`Unit`/`void`), the response has no body.

## Errors

Exceptions thrown by steps are handled by Spring MVC like exceptions from controllers, so
`@RestControllerAdvice` / `@ExceptionHandler` apply:

=== "Kotlin"

    ```kotlin
    @RestControllerAdvice
    class ErrorHandler {
        @ExceptionHandler(IllegalArgumentException::class)
        fun badRequest(e: IllegalArgumentException) =
            ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message)
    }
    ```

=== "Java"

    ```java
    @RestControllerAdvice
    public class ErrorHandler {
        @ExceptionHandler(IllegalArgumentException.class)
        public ProblemDetail badRequest(IllegalArgumentException e) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
    ```
