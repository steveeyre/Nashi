package com.nashi.core.remind

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nashi.core.data.NashiDao
import com.nashi.core.data.StatsAggregator
import com.nashi.core.sync.SyncResult
import com.nashi.core.sync.SyncWorkerEntryPoint
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** 供未注入的提醒 Worker 获取 DAO 的 Hilt 入口。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun dao(): NashiDao
}

private suspend fun daoOf(context: Context): NashiDao =
    EntryPointAccessors.fromApplication(context, ReminderEntryPoint::class.java).dao()

private const val THREE_DAYS_MS = 3L * 24 * 60 * 60 * 1000

/** 保质期提醒: 3 天内到期(含已过期)的物品汇总成一条通知。 */
class ExpiryWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result = try {
        val items = daoOf(applicationContext).allItems()
        val soon = StatsAggregator.expiringBefore(items, System.currentTimeMillis() + THREE_DAYS_MS)
        if (soon.isNotEmpty()) {
            val names = soon.take(5).joinToString("、") { it.name }
            val more = if (soon.size > 5) " 等 ${soon.size} 件" else ""
            ReminderNotifications.notify(
                applicationContext,
                ReminderNotifications.EXPIRY_NOTIFICATION_ID,
                "有 ${soon.size} 件物品即将过期",
                "$names$more, 记得尽快使用",
            )
        }
        Result.success()
    } catch (e: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}

/** 低库存提醒: quantity <= minStock 的物品汇总成一条通知。 */
class LowStockWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result = try {
        val items = daoOf(applicationContext).allItems()
        val low = StatsAggregator.lowStock(items)
        if (low.isNotEmpty()) {
            val detail = low.take(5).joinToString("、") { "${it.name}(${fmtQty(it.quantity)}/${fmtQty(it.minStock)})" }
            val more = if (low.size > 5) " 等 ${low.size} 件" else ""
            ReminderNotifications.notify(
                applicationContext,
                ReminderNotifications.LOW_STOCK_NOTIFICATION_ID,
                "有 ${low.size} 件物品需要补货",
                "$detail$more",
            )
        }
        Result.success()
    } catch (e: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}

private fun fmtQty(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else String.format("%.1f", v)

class SyncWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    // 先 push dirty(transactions+items since lastSince), 再 pull, LWW 合并, transactions 按 id 去重求和
    override suspend fun doWork(): Result {
        return try {
            val repo = EntryPointAccessors.fromApplication(
                applicationContext, SyncWorkerEntryPoint::class.java
            ).syncRepository()
            when (val r = repo.syncOnce()) {
                is SyncResult.Success -> Result.success()
                is SyncResult.Skipped -> Result.success()
                is SyncResult.Failed -> if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
