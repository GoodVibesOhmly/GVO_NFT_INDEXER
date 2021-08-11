package com.rarible.protocol.nftorder.core.converter

import com.rarible.protocol.dto.ItemHistoryDto
import com.rarible.protocol.dto.ItemTransferDto
import com.rarible.protocol.nftorder.core.model.ItemTransfer
import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component

@Component
object ItemHistoryDtoToTransferConverter : Converter<List<ItemHistoryDto>, List<ItemTransfer>> {

    override fun convert(list: List<ItemHistoryDto>): List<ItemTransfer> {
        val transfers = list.map { it as ItemTransferDto }
        return ItemTransferDtoConverter.convert(transfers)
    }
}