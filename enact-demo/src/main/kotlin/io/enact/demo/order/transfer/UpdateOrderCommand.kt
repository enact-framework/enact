package io.enact.demo.order.transfer

import java.util.UUID

/** `id` comes from the path, `updatedBy` from the `userId` attribute set by the `requireUser` filter and `changes` from the body (see application.yaml). */
data class UpdateOrderCommand(
    val id: UUID,
    val updatedBy: String,
    val changes: OrderRequest,
)
