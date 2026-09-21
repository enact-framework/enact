package io.enact.demo.order.transfer

import java.util.UUID

data class OrderConfirmation(
    val id: UUID,
    val total: Double,
    val shippingDays: Int,
    val discounted: Boolean,
)
