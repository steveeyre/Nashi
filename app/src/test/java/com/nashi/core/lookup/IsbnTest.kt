package com.nashi.core.lookup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsbnTest {
    @Test
    fun `标准 ISBN-13 直接归一化`() {
        assertEquals("9787111128069", Isbn.normalize("9787111128069"))
    }

    @Test
    fun `带五位定价附加码时取前十三位`() {
        assertEquals("9787111128069", Isbn.normalize("978711112806951299"))
    }

    @Test
    fun `ISBN-10 含连字符或前缀也能转成 ISBN-13`() {
        assertEquals("9787111128069", Isbn.normalize("7111128060"))
        assertEquals("9787111128069", Isbn.normalize("ISBN 7-111-12806-0"))
    }

    @Test
    fun `校验位错误不当作 ISBN`() {
        assertNull(Isbn.normalize("9787111128068"))
    }

    @Test
    fun `非 978 979 前缀的 EAN 不当作书`() {
        assertNull(Isbn.normalize("6901234567892"))
        assertFalse(Isbn.isIsbn("https://example.com/item/1"))
        assertTrue(Isbn.isIsbn("9791090636071"))
    }

    @Test
    fun `过短或空字符串返回空`() {
        assertNull(Isbn.normalize(""))
        assertNull(Isbn.normalize("12345"))
    }
}
