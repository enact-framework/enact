package io.enact.demo.order.transfer

import java.util.UUID

data class OrderResponse(
    val id: UUID,
    val amount: Double,
    val productCount: Int,
    val updatedBy: String?,
)
