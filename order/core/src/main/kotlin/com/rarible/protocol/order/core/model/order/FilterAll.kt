package com.rarible.protocol.order.core.model.order

import com.rarible.protocol.dto.OrderStatusDto
import com.rarible.protocol.dto.PlatformDto
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import scalether.domain.Address

data class FilterAll(
    override val origin: Address? = null,
    override val platforms: List<PlatformDto>,
    override val sort: Sort,
    override val status: List<OrderStatusDto>? = null
) : Filter() {

    override fun toQuery(continuation: String?, limit: Int): Query {
        return Query(
            Criteria()
                .forPlatform(platforms.mapNotNull { convert(it) })
                .fromOrigin(origin)
                .forStatus(status)
                .scrollTo(continuation, sort)
        ).limit(limit).with(sort(sort))
    }
}
