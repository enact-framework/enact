package io.enact.demo.order.entrypoint

import io.enact.core.usecase.UseCase
import io.enact.demo.order.transfer.OrderRequest
import io.enact.demo.order.transfer.OrderResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class OrderController(
    val createOrder: UseCase<OrderRequest, OrderResponse>,
) {
    @PostMapping("/orders", consumes = ["application/json"], produces = ["application/json"])
    fun createOrderEntrypoint(
        @RequestBody payload: OrderRequest,
    ): OrderResponse = createOrder.execute(payload)
}
