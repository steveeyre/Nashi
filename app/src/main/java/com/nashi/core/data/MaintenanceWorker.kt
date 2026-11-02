package com.nashi.core.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MaintenanceEntryPoint {
    fun repo(): ItemRepository
}

/** 每周本机维护: 清理过期墓碑(含照片/字段/标签) 与孤儿关联行。 */
class MaintenanceWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        EntryPointAccessors.fromApplication(applicationContext, MaintenanceEntryPoint::class.java)
            .repo()
            .runMaintenance()
        Result.success()
    } catch (e: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}

object MaintenanceScheduler {
    const val WORK = "nashi-maintenance-weekly"

    fun schedule(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MaintenanceWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.DAYS)
                .build(),
        )
    }
}
