package com.rarible.protocol.nft.core.service.item.meta

import com.rarible.protocol.nft.core.model.ItemId
import com.rarible.protocol.nft.core.model.ItemProperties

interface ItemPropertiesResolver {
    val name: String
    val canBeCached: Boolean get() = true
    suspend fun resolve(itemId: ItemId): ItemProperties?
    suspend fun reset(itemId: ItemId) = Unit
}
