package io.enact.demo.order

import io.enact.core.annotation.Step
import io.enact.demo.order.model.OrderEntity
import io.enact.demo.order.transfer.OrderResponse
import org.springframework.stereotype.Service

@Service
class OrderResponseMapperService {
    @Step
    fun mapOrderResponse(request: OrderEntity): OrderResponse =
        OrderResponse(
            id = request.id,
            amount = request.amount,
            productCount = request.productCount,
            updatedBy = request.updatedBy,
        )

    @Step
    fun mapOrderResponses(request: List<OrderEntity>): List<OrderResponse> = request.map(::mapOrderResponse)
}
