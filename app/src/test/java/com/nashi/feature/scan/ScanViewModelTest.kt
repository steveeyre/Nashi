package com.nashi.feature.scan

import com.nashi.core.data.ItemRepository
import com.nashi.core.data.NashiDao
import com.nashi.core.model.Item
import com.nashi.core.scan.ReceiptLine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ScanViewModelTest {
    private val dao = mockk<NashiDao>(relaxed = true)
    private val repo = mockk<ItemRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun item(id: String, name: String, quantity: Double = 1.0) = Item(
        id = id, name = name, categoryId = "cat-food", quantity = quantity, unit = "盒",
    )

    @Test
    fun `小票同名行合并到已有物品并累加数量`() = runTest {
        coEvery { dao.allItems() } returns listOf(item("i1", "牛奶", 1.0))
        coEvery { dao.itemById("i1") } returns item("i1", "牛奶", 1.0)
        coEvery { dao.fieldValuesOf("i1") } returns emptyList()
        val vm = ScanViewModel(dao, repo)

        val result = vm.importReceiptLines(listOf(ReceiptLine("牛奶", 2.0, "盒")), "cat-food")

        assertEquals(0, result.created)
        assertEquals(1, result.merged)
        coVerify {
            repo.saveItem(
                existingId = "i1", name = "牛奶", categoryId = "cat-food", brand = "",
                quantity = 3.0, unit = "盒", barcode = "", photoPaths = emptyList(),
                roomId = "", spaceId = "", expiryDate = null, minStock = 0.0,
                dynValues = emptyMap(), width = 0f, height = 0f, depth = 0f,
            )
        }
    }

    @Test
    fun `小票新行按所选分类与单位新建`() = runTest {
        coEvery { dao.allItems() } returns emptyList()
        coEvery { repo.saveItem(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns "new-id"
        val vm = ScanViewModel(dao, repo)

        val result = vm.importReceiptLines(listOf(ReceiptLine("洗洁精", 2.0, "瓶")), "cat-daily")

        assertEquals(1, result.created)
        assertEquals(0, result.merged)
        coVerify {
            repo.saveItem(
                existingId = null, name = "洗洁精", categoryId = "cat-daily", brand = "",
                quantity = 2.0, unit = "瓶", barcode = "", photoPaths = emptyList(),
                roomId = "", spaceId = "", expiryDate = null, minStock = 0.0,
                dynValues = emptyMap(), width = 0f, height = 0f, depth = 0f,
            )
        }
    }

    @Test
    fun `同一张票里重复行第二次走合并`() = runTest {
        coEvery { dao.allItems() } returns emptyList()
        coEvery { repo.saveItem(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns "i9"
        coEvery { dao.itemById("i9") } returns item("i9", "纸巾", 1.0)
        coEvery { dao.fieldValuesOf("i9") } returns emptyList()
        val vm = ScanViewModel(dao, repo)

        val result = vm.importReceiptLines(
            listOf(ReceiptLine("纸巾", 1.0, "包"), ReceiptLine("纸巾", 2.0, "包")),
            "cat-daily",
        )

        assertEquals(1, result.created)
        assertEquals(1, result.merged)
    }
}
