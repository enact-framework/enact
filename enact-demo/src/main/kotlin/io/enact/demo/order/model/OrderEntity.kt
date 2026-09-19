package io.enact.demo.order.model

import java.util.UUID

data class OrderEntity(
    val id: UUID,
    val amount: Double,
    val productCount: Int,
    val updatedBy: String? = null,
)
