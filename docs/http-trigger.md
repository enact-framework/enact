# HTTP trigger

With `enact-starter-web` on the classpath, a use case can be exposed as a Spring MVC endpoint:

```yaml
- name: findOrder
  trigger:
    rest:
      method: GET               # GET, POST, PUT, PATCH or DELETE
      path: /api/v1/orders/{id}
      status: 200               # optional, default 200
      produces: application/json  # optional
  steps:
    - step: findOrder
```

## Request binding

The use case input is built from:

1. the JSON request body (if any),
2. query parameters, which override body fields,
3. path variables, which override both.

The result is converted to the use case's input type with the application's Jackson `JsonMapper`. For example,
`DELETE /api/v1/orders/42?reason=duplicate` into:

```kotlin
data class CancelOrder(val id: Long, val reason: String)
```

If the use case has no input (its first step takes no parameter), the request is not read.
If the request cannot be converted to the input type, the response is `400 Bad Request`.

## Response

- The use case output is written as the response body with the configured `status`.
- If the use case returns nothing (`Unit`/`void`), the response has no body.

## Errors

Exceptions thrown by steps are handled by Spring MVC like exceptions from controllers, so
`@RestControllerAdvice` / `@ExceptionHandler` apply:

```kotlin
@RestControllerAdvice
class ErrorHandler {
    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(e: IllegalArgumentException) =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message)
}
```
