package io.enact.demo.order.transfer

import java.util.UUID

/** `id` comes from the path and `country` from a query parameter of the same name, e.g. `?country=FR`. */
data class ConfirmOrderCommand(
    val id: UUID,
    val country: String = "DE",
)
