package io.enact.demo.order.transfer

data class OrderRequest(
    val amount: Double,
    val productCount: Int,
)
