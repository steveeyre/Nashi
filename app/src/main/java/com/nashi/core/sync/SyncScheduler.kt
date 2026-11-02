package com.nashi.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nashi.core.remind.SyncWorker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/** 供未注入的 SyncWorker 获取 SyncRepository 的 Hilt 入口。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncWorkerEntryPoint {
    fun syncRepository(): SyncRepository
}

/** 周期同步调度：每 15 分钟 + 仅在联网时触发 SyncWorker。 */
object SyncScheduler {
    const val WORK_NAME = "nashi-sync-periodic"

    fun schedulePeriodic(context: Context) {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
