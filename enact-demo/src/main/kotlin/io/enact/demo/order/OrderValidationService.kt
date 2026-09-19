package io.enact.demo.order

import io.enact.core.annotation.Step
import io.enact.core.annotation.StepDefinition
import io.enact.demo.order.transfer.OrderRequest

@StepDefinition
class OrderValidationService {
    @Step
    fun validateOrderCreation(request: OrderRequest): OrderRequest {
        require(request.amount > 0) {
            "Amount should be greater than zero."
        }

        require(request.productCount > 0) {
            "Product count should be greater than zero."
        }

        return request
    }
}
