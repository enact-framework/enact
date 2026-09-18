package io.enact.demo.order

import io.enact.core.annotation.Step
import io.enact.demo.order.model.OrderEntity
import io.enact.demo.order.transfer.OrderIdRequest
import io.enact.demo.order.transfer.OrderRequest
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

    @Step
    fun findOrder(request: OrderIdRequest): OrderEntity = requireNotNull(orders[request.id]) { "Order ${request.id} not found." }

    @Step
    fun cancelOrder(request: OrderIdRequest) {
        requireNotNull(orders.remove(request.id)) { "Order ${request.id} not found." }
        // Enact never evicts cached step results, so drop the order cached by findOrder
        cacheManager.getCache("orders")?.evict(request.id)
    }
}
