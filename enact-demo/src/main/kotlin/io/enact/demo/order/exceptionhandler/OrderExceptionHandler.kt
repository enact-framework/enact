package io.enact.demo.order.exceptionhandler

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class OrderExceptionHandler {
    @ExceptionHandler(IllegalArgumentException::class)
    fun exceptionHandler(e: IllegalArgumentException): ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message)
}
