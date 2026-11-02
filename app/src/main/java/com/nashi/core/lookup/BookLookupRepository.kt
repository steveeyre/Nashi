package com.nashi.core.lookup

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ISBN 元数据查询: 先用 Google Books(中文书覆盖较好), 失败再退回 Open Library。
 * 两个源都不需要 API Key; 任一源能连上但都没结果时返回 NotFound。
 */
@Singleton
class BookLookupRepository @Inject constructor() {
    private val http = HttpClient(Android) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 10_000
        }
    }

    suspend fun lookup(raw: String): BookLookupResult = withContext(Dispatchers.IO) {
        val isbn = Isbn.normalize(raw) ?: return@withContext BookLookupResult.NotFound
        var reachable = false

        fetch("$GOOGLE?q=isbn:$isbn")?.let { body ->
            reachable = true
            BookLookupParsers.googleBooks(body, isbn)?.let { return@withContext BookLookupResult.Found(it) }
        }
        fetch("$OPEN_LIBRARY?bibkeys=ISBN:$isbn&format=json&jscmd=data")?.let { body ->
            reachable = true
            BookLookupParsers.openLibrary(body, isbn)?.let { return@withContext BookLookupResult.Found(it) }
        }

        if (reachable) BookLookupResult.NotFound else BookLookupResult.Unavailable
    }

    private suspend fun fetch(url: String): String? = try {
        val resp = http.get(url)
        if (resp.status.isSuccess()) resp.bodyAsText() else null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val GOOGLE = "https://www.googleapis.com/books/v1/volumes"
        const val OPEN_LIBRARY = "https://openlibrary.org/api/books"
    }
}
