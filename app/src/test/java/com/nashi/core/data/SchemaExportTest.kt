package com.nashi.core.data

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * exportSchema 打开后, Room 把库里真实结构写到 app/schemas/.../2.json。
 * 这个测试防止两种情况: schema 忘了提交(CI 里文件不存在), 或索引与 Migration 断言脱节。
 */
class SchemaExportTest {

    private val schemaFile = File("schemas/com.nashi.core.data.NashiDatabase/2.json")

    private val entities by lazy {
        Json { ignoreUnknownKeys = true }
            .parseToJsonElement(schemaFile.readText())
            .jsonObject.getValue("database").jsonObject
            .getValue("entities").jsonArray
            .map { it.jsonObject }
    }

    @Test
    fun `导出的 schema 覆盖全部九张表且版本为 2`() {
        assertTrue("schema 未导出: ${schemaFile.absolutePath}", schemaFile.isFile)
        val version = Json.parseToJsonElement(schemaFile.readText())
            .jsonObject.getValue("database").jsonObject
            .getValue("version").jsonPrimitive.int
        assertEquals(2, version)

        val tables = entities.map { it.getValue("tableName").jsonPrimitive.content }.toSet()
        assertEquals(
            setOf(
                "categories", "categoryFields", "items", "fieldValues",
                "tags", "itemTags", "rooms", "spaces", "transactions",
            ),
            tables,
        )
    }

    @Test
    fun `items 的四个索引与 1 到 2 迁移一致`() {
        val items = entities.single { it.getValue("tableName").jsonPrimitive.content == "items" }
        val indices = items.getValue("indices").jsonArray
            .map { it.jsonObject.getValue("name").jsonPrimitive.content }
            .toSet()

        assertEquals(
            setOf(
                "index_items_barcode", "index_items_roomId",
                "index_items_spaceId", "index_items_updatedAt",
            ),
            indices,
        )
    }
}
