package com.rarible.protocol.order.api.service.order

import com.rarible.core.apm.CaptureSpan
import com.rarible.core.apm.SpanType
import com.rarible.protocol.dto.Continuation
import com.rarible.protocol.order.core.model.BidStatus
import com.rarible.protocol.order.core.model.CompositeBid
import com.rarible.protocol.order.core.model.Order
import com.rarible.protocol.order.core.model.OrderStatus
import com.rarible.protocol.order.core.model.OrderVersion
import com.rarible.protocol.order.core.repository.order.OrderRepository
import com.rarible.protocol.order.core.repository.order.OrderVersionRepository
import com.rarible.protocol.order.core.repository.order.PriceOrderVersionFilter
import io.daonomic.rpc.domain.Word
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.awaitFirst
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
@CaptureSpan(type = SpanType.APP)
class OrderBidsService(
    private val orderRepository: OrderRepository,
    private val orderVersionRepository: OrderVersionRepository
) {
    suspend fun findOrderBids(
        filter: PriceOrderVersionFilter,
        statuses: List<BidStatus>
    ): List<CompositeBid> {
        val totalResult = mutableListOf<CompositeBid>()
        var versions = emptyList<OrderVersion>()

        val foundOrders = hashSetOf<Word>()
        do {
            val next = if (versions.isNotEmpty()) filter.withContinuation(toContinuation(versions.last(), filter)) else filter
            versions = orderVersionRepository.search(next).collectList().awaitFirst()

            val orderHashes = versions.map { it.hash }.toHashSet()
            val orders = orderRepository.findAll(orderHashes).toList().associateBy { it.hash }

            val result = convert(versions, orders, foundOrders).filter { it.status in statuses }.toList()
            totalResult.addAll(result)
        } while (versions.isNotEmpty() && totalResult.size < filter.limit)

        return totalResult.take(filter.limit)
    }

    private fun convert(
        versions: List<OrderVersion>,
        orders: Map<Word, Order>,
        foundOrders: MutableSet<Word>
    ): Sequence<CompositeBid> {

        return sequence {
            versions.forEach { version ->
                val order = orders[version.hash]
                if (order != null) {
                    val status = convert(order)

                    val calculatedStatus = if (foundOrders.add(version.hash) && status == BidStatus.HISTORICAL) {
                        if (order.status == OrderStatus.FILLED) BidStatus.FILLED else BidStatus.ACTIVE
                    } else {
                        status
                    }
                    yield(CompositeBid(calculatedStatus, version, order))
                }
            }
        }
    }

    private fun convert(source: Order): BidStatus {
        return when (source.status) {
            OrderStatus.CANCELLED -> BidStatus.CANCELLED
            OrderStatus.INACTIVE, OrderStatus.NOT_STARTED, OrderStatus.ENDED -> BidStatus.INACTIVE
            else -> BidStatus.HISTORICAL
        }
    }

    private fun toContinuation(orderVersion: OrderVersion, filter: PriceOrderVersionFilter): Continuation.Price {
        if (filter is PriceOrderVersionFilter.BidByItem && filter.currencyId != null) {
            return Continuation.Price(orderVersion.takePrice ?: BigDecimal.ZERO, orderVersion.hash)
        } else {
            return Continuation.Price(orderVersion.takePriceUsd ?: BigDecimal.ZERO, orderVersion.hash)
        }
    }
}
