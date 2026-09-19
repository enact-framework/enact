package io.enact.demo.order.transfer

/** Bound from query parameters of the same name, e.g. `?minAmount=10&limit=5`. */
data class OrderSearch(
    val minAmount: Double = 0.0,
    val limit: Int = 20,
)
