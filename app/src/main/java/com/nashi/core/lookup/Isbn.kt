package com.nashi.core.lookup

/**
 * 书籍的 ISBN 没有独立码制: 扫码拿到的是一串 EAN-13 数字, 书背常跟 5 位定价附加码。
 * 这里统一去掉前缀/附加码、校验校验位, 并归一化为 ISBN-13 供查询使用。
 */
object Isbn {
    fun normalize(raw: String): String? {
        val compact = raw.uppercase().filter { it.isDigit() || it == 'X' }
        val isbn13: String = when {
            compact.length >= 13 -> compact.take(13)
            compact.length == 10 -> from10(compact) ?: return null
            else -> return null
        }
        if (!isbn13.startsWith("978") && !isbn13.startsWith("979")) return null
        return isbn13.takeIf(::valid13)
    }

    fun isIsbn(raw: String): Boolean = normalize(raw) != null

    private fun valid13(s: String): Boolean {
        if (s.length != 13) return false
        val sum = s.take(12).mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()
        return (10 - sum % 10) % 10 == s[12] - '0'
    }

    private fun from10(s: String): String? {
        fun value(c: Char): Int = if (c == 'X') 10 else c - '0'
        val sum = s.mapIndexed { i, c -> value(c) * (10 - i) }.sum()
        if (sum % 11 != 0) return null
        if (!s.take(9).all(Char::isDigit)) return null
        val core = "978" + s.take(9)
        val sum13 = core.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()
        return core + ((10 - sum13 % 10) % 10)
    }
}
