package com.nashi.core.data

import com.nashi.core.model.Item

/** 墓碑保留策略: 过期墓碑可从本机清除, 同步靠 purged ids 抑制服务端残留墓碑复活。 */
object TombstonePolicy {
    const val RETENTION_DAYS = 90L
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun isExpired(item: Item, now: Long, retentionDays: Long = RETENTION_DAYS): Boolean =
        item.isDeleted && item.updatedAt < now - retentionDays * DAY_MS
}
