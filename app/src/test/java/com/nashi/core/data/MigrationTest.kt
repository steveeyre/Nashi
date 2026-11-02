package com.nashi.core.data

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationTest {
    @Test
    fun `1 到 2 为物品与空间的过滤列建索引`() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        val statements = mutableListOf<String>()
        MIGRATION_1_2.migrate(db)
        verify(exactly = 5) { db.execSQL(capture(statements)) }

        val expected = listOf(
            "index_items_barcode" to "`items` (`barcode`)",
            "index_items_roomId" to "`items` (`roomId`)",
            "index_items_spaceId" to "`items` (`spaceId`)",
            "index_items_updatedAt" to "`items` (`updatedAt`)",
            "index_spaces_roomId" to "`spaces` (`roomId`)",
        )
        expected.forEach { (name, tail) ->
            val sql = statements.singleOrNull { it.contains("`$name`") }
            assertTrue("缺少 $name", sql != null)
            assertTrue("索引定义不符 $name: $sql", sql!!.endsWith(tail))
        }
        assertEquals(1, MIGRATION_1_2.startVersion)
        assertEquals(2, MIGRATION_1_2.endVersion)
    }
}
