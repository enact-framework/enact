package io.enact.demo.order

import io.enact.core.annotation.Cached
import io.enact.core.annotation.Step
import io.enact.demo.order.model.OrderEntity
import io.enact.demo.order.transfer.OrderRequest
import io.enact.demo.order.transfer.OrderSearch
import io.enact.demo.order.transfer.UpdateOrderCommand
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class OrderPersistingService(
    private val cacheManager: CacheManager,
) {
    // Stands in for a database
    private val orders = ConcurrentHashMap<UUID, OrderEntity>()

    @Step
    fun saveOrder(request: OrderRequest): OrderEntity {
        val orderEntity =
            OrderEntity(
                id = UUID.randomUUID(),
                amount = request.amount,
                productCount = request.productCount,
            )
        orders[orderEntity.id] = orderEntity
        return orderEntity
    }

    @Step(cache = Cached(name = "orders"))
    fun findOrder(id: UUID): OrderEntity = requireNotNull(orders[id]) { "Order $id not found." }

    @Step
    fun searchOrders(search: OrderSearch): List<OrderEntity> = orders.values.filter { it.amount >= search.minAmount }.take(search.limit)

    @Step
    fun updateOrder(command: UpdateOrderCommand): OrderEntity {
        val order = requireNotNull(orders[command.id]) { "Order ${command.id} not found." }
        val updated =
            order.copy(
                amount = command.changes.amount,
                productCount = command.changes.productCount,
                updatedBy = command.updatedBy,
            )
        orders[updated.id] = updated
        // Enact never evicts cached step results, so drop the order cached by findOrder
        cacheManager.getCache("orders")?.evict(updated.id)
        return updated
    }

    @Step
    fun cancelOrder(id: UUID) {
        requireNotNull(orders.remove(id)) { "Order $id not found." }
        // Enact never evicts cached step results, so drop the order cached by findOrder
        cacheManager.getCache("orders")?.evict(id)
    }
}
