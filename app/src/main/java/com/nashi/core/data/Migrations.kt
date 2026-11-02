package com.nashi.core.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2: 为物品/空间的常用过滤与排序列补索引（房间、空间、条码、更新时间）。
 * 索引名必须与 Room 依据 @Index 生成的默认名一致，否则启动时 schema 校验失败。
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_barcode` ON `items` (`barcode`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_roomId` ON `items` (`roomId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_spaceId` ON `items` (`spaceId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_updatedAt` ON `items` (`updatedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_spaces_roomId` ON `spaces` (`roomId`)")
    }
}
