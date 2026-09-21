package io.enact.demo.order

import io.enact.core.annotation.Step
import io.enact.core.annotation.StepDefinition
import io.enact.demo.order.model.OrderEntity
import io.enact.demo.order.model.ShippingEstimate
import io.enact.demo.order.transfer.ConfirmOrderCommand
import io.enact.demo.order.transfer.OrderConfirmation
import org.slf4j.LoggerFactory

/** Steps of the `confirmOrder` graph: see `enact/orders.yaml` for how they are wired together. */
@StepDefinition
class OrderConfirmationService {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Reads the use case input, as `loadOrder` does: neither reads the other, so `concurrent` runs them at once. */
    @Step
    fun estimateShipping(command: ConfirmOrderCommand): ShippingEstimate =
        if (command.country == "DE") ShippingEstimate(fee = 3.99, days = 2) else ShippingEstimate(fee = 9.99, days = 5)

    /** Two parameters, so the graph feeds it two edges, each bound by parameter name. */
    @Step
    fun quoteConfirmation(
        order: OrderEntity,
        shipping: ShippingEstimate,
    ): OrderConfirmation =
        OrderConfirmation(
            id = order.id,
            total = order.amount * order.productCount + shipping.fee,
            shippingDays = shipping.days,
            discounted = false,
        )

    /** Runs only when its `when` holds; otherwise the quote is passed through untouched. */
    @Step
    fun applyBulkDiscount(confirmation: OrderConfirmation): OrderConfirmation =
        confirmation.copy(total = confirmation.total * 0.9, discounted = true)

    /** Runs aside: the response is already on its way when this logs. */
    @Step
    fun notifyWarehouse(confirmation: OrderConfirmation) {
        log.info(
            "step notifyWarehouse: order {} ships in {} days (on {})",
            confirmation.id,
            confirmation.shippingDays,
            Thread.currentThread(),
        )
    }
}
