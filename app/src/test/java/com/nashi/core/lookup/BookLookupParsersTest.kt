package com.nashi.core.lookup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookLookupParsersTest {
    @Test
    fun `Google Books 取首条命中并拼接作者`() {
        val json = """
            {"totalItems":1,"items":[{"volumeInfo":{
              "title":"深入理解计算机系统",
              "authors":["Randal E. Bryant","David R. O'Hallaron"],
              "publisher":"机械工业出版社",
              "publishedDate":"2016-11"
            }}]}
        """.trimIndent()

        val meta = BookLookupParsers.googleBooks(json, "9787111544937")

        assertEquals("深入理解计算机系统", meta?.title)
        assertEquals("Randal E. Bryant / David R. O'Hallaron", meta?.authors)
        assertEquals("机械工业出版社", meta?.publisher)
        assertEquals("2016-11", meta?.publishedDate)
    }

    @Test
    fun `Google Books 无结果或缺书名时返回空`() {
        assertNull(BookLookupParsers.googleBooks("""{"totalItems":0}""", "9787111128069"))
        assertNull(
            BookLookupParsers.googleBooks(
                """{"items":[{"volumeInfo":{"authors":["无名"]}}]}""",
                "9787111128069",
            ),
        )
        assertNull(BookLookupParsers.googleBooks("<html>502</html>", "9787111128069"))
    }

    @Test
    fun `Open Library 解析作者与出版社数组`() {
        val json = """
            {"ISBN:9787111128069":{
              "title":"计算机组成原理",
              "authors":[{"name":"白中英"}],
              "publishers":[{"name":"科学出版社"}],
              "publish_date":"2019"
            }}
        """.trimIndent()

        val meta = BookLookupParsers.openLibrary(json, "9787111128069")

        assertEquals("计算机组成原理", meta?.title)
        assertEquals("白中英", meta?.authors)
        assertEquals("科学出版社", meta?.publisher)
        assertEquals("2019", meta?.publishedDate)
    }

    @Test
    fun `Open Library 空对象表示查不到`() {
        assertNull(BookLookupParsers.openLibrary("{}", "9787111128069"))
    }

    @Test
    fun `缺作者和出版社时只返回书名`() {
        val meta = BookLookupParsers.openLibrary(
            """{"ISBN:9787111128069":{"title":"  孤本  "}}""",
            "9787111128069",
        )

        assertEquals("孤本", meta?.title)
        assertEquals("", meta?.authors)
        assertEquals("", meta?.publisher)
    }
}
