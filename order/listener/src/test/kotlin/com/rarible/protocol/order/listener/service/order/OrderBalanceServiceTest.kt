package com.rarible.protocol.order.listener.service.order

import com.rarible.core.test.wait.Wait
import com.rarible.ethereum.domain.EthUInt256
import com.rarible.protocol.dto.Erc20BalanceDto
import com.rarible.protocol.dto.Erc20BalanceUpdateEventDto
import com.rarible.protocol.dto.NftDeletedOwnershipDto
import com.rarible.protocol.dto.NftOwnershipDeleteEventDto
import com.rarible.protocol.dto.NftOwnershipDto
import com.rarible.protocol.dto.NftOwnershipUpdateEventDto
import com.rarible.protocol.order.core.model.Asset
import com.rarible.protocol.order.core.model.Erc1155AssetType
import com.rarible.protocol.order.core.model.Erc20AssetType
import com.rarible.protocol.order.core.model.Erc721AssetType
import com.rarible.protocol.order.core.model.EthAssetType
import com.rarible.protocol.order.listener.data.createOrderVersion
import com.rarible.protocol.order.listener.integration.AbstractIntegrationTest
import com.rarible.protocol.order.listener.integration.IntegrationTest
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import scalether.domain.AddressFactory

@IntegrationTest
class OrderBalanceServiceTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var orderBalanceService: OrderBalanceService

    @Test
    fun `should update all not canceled balance orders`() = runBlocking<Unit> {
        val targetMaker = AddressFactory.create()
        val targetToken = AddressFactory.create()

        val make = Asset(Erc20AssetType(targetToken), EthUInt256.TEN)
        val take = Asset(Erc1155AssetType(AddressFactory.create(), EthUInt256.TEN), EthUInt256.TEN)

        val oldStock = EthUInt256.of(2)
        val newStock = EthUInt256.of(5)

        clearMocks(assetBalanceProvider)
        coEvery { assetBalanceProvider.getAssetStock(any(), any()) } returns oldStock

        val order1 = createOrderVersion().copy(
            maker = targetMaker,
            make = make,
            take = take
        )
        val order2 = createOrderVersion().copy(
            maker = targetMaker,
            make = make,
            take = take
        )
        val order3 = createOrderVersion().copy(
            maker = targetMaker,
            make = make,
            take = take
        )
        val order4 = createOrderVersion().copy(
            maker = AddressFactory.create(),
            make = make,
            take = take
        )

        listOf(order1, order2, order3, order4).forEach { orderUpdateService.save(it) }
        cancelOrder(order3.hash)

        val updatedBalance = mockk<Erc20BalanceDto> {
            every { owner } returns targetMaker
            every { contract } returns targetToken
            every { balance } returns newStock.value
        }
        val event = mockk<Erc20BalanceUpdateEventDto> {
            every { balance } returns updatedBalance
        }

        // Background job might update makeStock before this event is handled => try until the event solely changes the makeStock.
        Wait.waitAssert {
            orderBalanceService.handle(event)

            assertThat(orderRepository.findById(order1.hash)?.makeStock).isEqualTo(newStock)
            assertThat(orderRepository.findById(order2.hash)?.makeStock).isEqualTo(newStock)
            assertThat(orderRepository.findById(order3.hash)?.makeStock).isEqualTo(EthUInt256.ZERO) // because order #3 is a cancelled order.
            assertThat(orderRepository.findById(order4.hash)?.makeStock).isEqualTo(oldStock)
        }
    }

    @Test
    fun `should update all not canceled nft orders`() = runBlocking<Unit> {
        val targetToken = AddressFactory.create()
        val targetTokenId = EthUInt256.of(2)

        val make = Asset(Erc1155AssetType(targetToken, targetTokenId), EthUInt256.TEN)
        val take = Asset(Erc20AssetType(AddressFactory.create()), EthUInt256.TEN)
        val maker = AddressFactory.create()

        val order1 = createOrderVersion().copy(
            maker = maker,
            make = make,
            take = take
        )
        val order2 = createOrderVersion().copy(
            maker = maker,
            make = make,
            take = take
        )
        val order3 = createOrderVersion().copy(
            maker = maker,
            make = make,
            take = take
            // cancelled
        )
        val order4 = createOrderVersion().copy(
            make = make
            // other maker => different hash
        )

        val oldStock = EthUInt256.ONE
        val newStock = EthUInt256.of(5)
        clearMocks(assetBalanceProvider)
        coEvery { assetBalanceProvider.getAssetStock(any(), any()) } returns oldStock

        listOf(order1, order2, order3, order4).forEach { orderUpdateService.save(it) }
        cancelOrder(order3.hash)

        val updatedOwnership = mockk<NftOwnershipDto> {
            every { owner } returns maker
            every { contract } returns targetToken
            every { tokenId } returns targetTokenId.value
            every { value } returns newStock.value
        }
        val event = mockk<NftOwnershipUpdateEventDto> {
            every { ownership } returns updatedOwnership
        }

        Wait.waitAssert {
            orderBalanceService.handle(event)

            assertThat(orderRepository.findById(order1.hash)?.makeStock).isEqualTo(newStock)
            assertThat(orderRepository.findById(order2.hash)?.makeStock).isEqualTo(newStock)
            assertThat(orderRepository.findById(order3.hash)?.makeStock).isEqualTo(EthUInt256.ZERO) // because order #3 is a cancelled order.
            assertThat(orderRepository.findById(order4.hash)?.makeStock).isEqualTo(oldStock)
        }
    }

    @Test
    fun `sell order makeStock becomes 0 when make NFT is transferred`() = runBlocking<Unit> {
        val targetToken = AddressFactory.create()
        val targetTokenId = EthUInt256.of(2)

        val make = Asset(Erc721AssetType(targetToken, targetTokenId), EthUInt256.ONE)
        val take = Asset(EthAssetType, EthUInt256.TEN)
        val oldOwner = AddressFactory.create()

        val initialStock = EthUInt256.ONE
        clearMocks(assetBalanceProvider)
        coEvery { assetBalanceProvider.getAssetStock(any(), any()) } returns initialStock
        val order = orderUpdateService.save(
            createOrderVersion().copy(
                maker = oldOwner,
                make = make,
                take = take
            )
        )
        assertThat(orderRepository.findById(order.hash)?.makeStock).isEqualTo(initialStock)
        val deletedOwnership = mockk<NftDeletedOwnershipDto> {
            every { owner } returns oldOwner
            every { token } returns targetToken
            every { tokenId } returns targetTokenId.value
        }
        val event = mockk<NftOwnershipDeleteEventDto> {
            every { ownership } returns deletedOwnership
        }
        orderBalanceService.handle(event)
        assertThat(orderRepository.findById(order.hash)?.makeStock).isEqualTo(EthUInt256.ZERO)
    }
}
