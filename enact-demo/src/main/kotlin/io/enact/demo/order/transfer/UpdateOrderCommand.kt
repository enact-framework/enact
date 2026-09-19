package io.enact.demo.order.transfer

import java.util.UUID

/** `id` comes from the path, `updatedBy` from the `X-User-Id` header and `changes` from the body (see application.yaml). */
data class UpdateOrderCommand(
    val id: UUID,
    val updatedBy: String,
    val changes: OrderRequest,
)
