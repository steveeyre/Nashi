package com.nashi.core.lookup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class BookMeta(
    val isbn: String,
    val title: String,
    val authors: String,
    val publisher: String,
    val publishedDate: String,
)

sealed interface BookLookupResult {
    data class Found(val meta: BookMeta) : BookLookupResult

    /** 两个数据源都能访问, 但没有这本书。 */
    data object NotFound : BookLookupResult

    /** 网络不通或服务异常, 和「查不到」区分开以便提示重试。 */
    data object Unavailable : BookLookupResult
}

/**
 * 只做 JSON 到 BookMeta 的纯解析, 便于用真实响应样本做单元测试。
 * 字段全部按可选处理: 数据源缺作者/出版社时仍返回书名。
 */
object BookLookupParsers {
    fun googleBooks(json: String, isbn: String): BookMeta? {
        val root = parseObject(json) ?: return null
        val item = (root["items"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val info = item["volumeInfo"] as? JsonObject ?: return null
        val title = info.str("title") ?: return null
        return BookMeta(
            isbn = isbn,
            title = title,
            authors = info.arrayText("authors"),
            publisher = info.str("publisher").orEmpty(),
            publishedDate = info.str("publishedDate").orEmpty(),
        )
    }

    fun openLibrary(json: String, isbn: String): BookMeta? {
        val root = parseObject(json) ?: return null
        // 返回形如 {"ISBN:978...": {...}}, 找不到时是空对象
        val entry = root.values.firstOrNull() as? JsonObject ?: return null
        val title = entry.str("title") ?: return null
        val authors = (entry["authors"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.str("name") }
            .orEmpty()
        val publishers = (entry["publishers"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.str("name") }
            .orEmpty()
        return BookMeta(
            isbn = isbn,
            title = title,
            authors = authors.joinToString(" / "),
            publisher = publishers.joinToString(" / "),
            publishedDate = entry.str("publish_date").orEmpty(),
        )
    }

    private fun parseObject(json: String): JsonObject? =
        runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull()

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)

    private fun JsonObject.arrayText(key: String): String =
        (this[key] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
            .orEmpty()
            .joinToString(" / ")
}
