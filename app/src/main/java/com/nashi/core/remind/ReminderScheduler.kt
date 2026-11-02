package com.nashi.core.remind

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** 每日检查一次临期与低库存, 命中后发通知。 */
object ReminderScheduler {
    const val EXPIRY_WORK = "nashi-expiry-daily"
    const val LOW_STOCK_WORK = "nashi-lowstock-daily"

    fun schedule(context: Context) {
        ReminderNotifications.ensureChannel(context)
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            EXPIRY_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ExpiryWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build(),
        )
        wm.enqueueUniquePeriodicWork(
            LOW_STOCK_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<LowStockWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build(),
        )
    }
}
