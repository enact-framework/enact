# HTTP trigger

With `enact-starter-web` on the classpath, a use case can be exposed as a Spring MVC endpoint:

```yaml
- name: findOrder
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

The use case input is built from:

1. the JSON request body (if any),
2. query parameters, which override body fields,
3. path variables, which override both.

The result is converted to the use case's input type with the application's Jackson `JsonMapper`. For example,
`DELETE /api/v1/orders/42?reason=duplicate` into:

=== "Kotlin"

    ```kotlin
    data class CancelOrder(val id: Long, val reason: String)
    ```

=== "Java"

    ```java
    public record CancelOrder(long id, String reason) {}
    ```

If the use case has no input (its first step takes no parameter), the request is not read.
If the request cannot be converted to the input type, the response is `400 Bad Request`.

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
