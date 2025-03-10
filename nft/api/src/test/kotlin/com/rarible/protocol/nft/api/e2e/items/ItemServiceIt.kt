package com.rarible.protocol.nft.api.e2e.items

import com.rarible.core.common.nowMillis
import com.rarible.core.test.data.randomString
import com.rarible.protocol.nft.api.domain.ItemContinuation
import com.rarible.protocol.nft.api.e2e.End2EndTest
import com.rarible.protocol.nft.api.e2e.SpringContainerBaseTest
import com.rarible.protocol.nft.api.e2e.data.createItem
import com.rarible.protocol.nft.api.service.item.ItemService
import com.rarible.protocol.nft.core.model.ExtendedItem
import com.rarible.protocol.nft.core.model.Item
import com.rarible.protocol.nft.core.model.ItemFilter
import com.rarible.protocol.nft.core.model.ItemFilterAll
import com.rarible.protocol.nft.core.model.ItemFilterByCollection
import com.rarible.protocol.nft.core.model.ItemFilterByCreator
import com.rarible.protocol.nft.core.model.ItemFilterByOwner
import com.rarible.protocol.nft.core.model.ItemId
import com.rarible.protocol.nft.core.model.ItemProperties
import com.rarible.protocol.nft.core.repository.item.ItemRepository
import io.mockk.coEvery
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import scalether.domain.Address
import java.time.Duration
import java.time.Instant

@End2EndTest
class ItemServiceIt : SpringContainerBaseTest() {
    @Autowired
    private lateinit var itemService: ItemService

    @Autowired
    private lateinit var itemRepository: ItemRepository

    private val defaultSort = ItemFilter.Sort.LAST_UPDATE

    @Test
    fun `should find all items`() = runBlocking<Unit> {
        saveItem(Address.ONE(), Address.TWO(), listOf(Address.THREE(), Address.FOUR()))
        saveItem(Address.TWO(), Address.TWO(), listOf(Address.THREE(), Address.FOUR()))
        saveItem(Address.THREE(), Address.TWO(), listOf(Address.THREE(), Address.FOUR()), deleted = true)

        var result = itemService.search(ItemFilterAll(defaultSort, true, null), null, 10)
        assertSizeAndMeta(result, 3)

        result = itemService.search(ItemFilterAll(defaultSort, true, null), null, 1)
        assertSizeAndMeta(result, 1)
    }

    @Test
    fun `should only find not deleted items`() = runBlocking<Unit> {
        saveItem(Address.ONE(), Address.TWO(), listOf(Address.THREE(), Address.FOUR()))
        saveItem(Address.TWO(), Address.TWO(), listOf(Address.THREE(), Address.FOUR()))
        saveItem(Address.THREE(), Address.TWO(), listOf(Address.THREE(), Address.FOUR()), deleted = true)

        var result = itemService.search(ItemFilterAll(defaultSort, false, null), null, 10)
        assertSizeAndMeta(result, 2)

        result = itemService.search(ItemFilterAll(defaultSort, false, null), null, 1)
        assertSizeAndMeta(result, 1)
    }

    @Test
    fun `should only find item changed in target period`() = runBlocking<Unit> {
        val now = nowMillis()
        val item1 = createItem().copy(date = now - Duration.ofHours(1))
        val item2 = createItem().copy(date = now + Duration.ofHours(1))
        val item3 = createItem().copy(date = now + Duration.ofHours(2))
        val item4 = createItem().copy(date = now + Duration.ofHours(3), deleted = true)
        val item5 = createItem().copy(date = now + Duration.ofHours(10))
        saveItem(item1, item2, item3, item4, item5)

        val result = itemService.search(
            ItemFilterAll(
                defaultSort,
                true,
                now
            ),
            ItemContinuation(now + Duration.ofHours(5), ItemId.MAX_ID),
            10
        )
        assertSizeAndMeta(result, 3)
        assertThat(result.any { it.item.id == item2.id }).isTrue()
        assertThat(result.any { it.item.id == item3.id }).isTrue()
        assertThat(result.any { it.item.id == item4.id }).isTrue()
    }

    @Test
    fun `should find all items by collection`() = runBlocking<Unit> {
        saveItem(Address.ONE(), Address.TWO(), listOf(Address.THREE(), Address.FOUR()))
        saveItem(Address.TWO(), Address.TWO(), listOf(Address.THREE(), Address.FOUR()))
        saveItem(Address.THREE(), Address.TWO(), listOf(Address.THREE(), Address.FOUR()), deleted = true)

        var result = itemService.search(ItemFilterByCollection(defaultSort, Address.ONE()), null, 10)
        assertSizeAndMeta(result, 1)

        result = itemService.search(ItemFilterByCollection(defaultSort, Address.TWO()), null, 10)
        assertSizeAndMeta(result, 1)
    }

    @Test
    fun `should find all items by owner`() = runBlocking<Unit> {
        saveItem(Address.ONE(), Address.TWO(), listOf(Address.THREE()))
        saveItem(Address.TWO(), Address.TWO(), listOf(Address.THREE(), Address.FOUR()))
        saveItem(Address.THREE(), Address.TWO(), listOf(Address.THREE(), Address.FOUR()), deleted = true)

        var result = itemService.search(ItemFilterByOwner(defaultSort, Address.ONE()), null, 10)
        assertSizeAndMeta(result, 0)

        result = itemService.search(ItemFilterByOwner(defaultSort, Address.THREE()), null, 10)
        assertSizeAndMeta(result, 2)

        result = itemService.search(ItemFilterByOwner(defaultSort, Address.FOUR()), null, 10)
        assertSizeAndMeta(result, 1)
    }

    @Test
    fun `should find all items by creator`() = runBlocking<Unit> {
        saveItem(Address.ONE(), Address.TWO(), listOf(Address.THREE()))
        saveItem(Address.TWO(), Address.TWO(), listOf(Address.THREE(), Address.FOUR()))
        saveItem(Address.THREE(), Address.TWO(), listOf(Address.THREE(), Address.FOUR()), deleted = true)

        var result = itemService.search(ItemFilterByCreator(defaultSort, Address.ONE()), null, 10)
        assertSizeAndMeta(result, 0)

        result = itemService.search(ItemFilterByCreator(defaultSort, Address.TWO()), null, 10)
        assertSizeAndMeta(result, 2)
    }

    private fun assertSizeAndMeta(result: List<ExtendedItem>, expectedSize: Int) {
        assertThat(result).hasSize(expectedSize)
        assertThat(result).allSatisfy {
            assertThat(it.itemMeta.properties).isEqualToIgnoringGivenFields(
                itemProperties,
                ItemProperties::rawJsonContent.name
            )
        }
    }

    private suspend fun saveItem(
        token: Address,
        creator: Address,
        owners: List<Address>,
        deleted: Boolean = false,
        date: Instant = nowMillis()
    ) {
        val item = createItem(token, creator, owners, deleted, date)
        saveItem(item)
    }

    private suspend fun saveItem(vararg items: Item) {
        items.forEach {
            itemRepository.save(it).awaitFirst()
            coEvery { mockItemPropertiesResolver.resolve(it.id) } returns itemProperties
        }
    }

    private val itemProperties = ItemProperties(
        name = randomString(),
        description = randomString(),
        image = "https://" + randomString(),
        imagePreview = "https://" + randomString(),
        imageBig = "https://" + randomString(),
        animationUrl = "https://" + randomString(),
        attributes = emptyList(),
        rawJsonContent = null
    )
}
