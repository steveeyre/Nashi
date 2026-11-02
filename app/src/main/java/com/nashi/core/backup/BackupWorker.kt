package com.nashi.core.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nashi.core.data.NashiDao
import com.nashi.core.io.NashiIo
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackupEntryPoint {
    fun dao(): NashiDao
}

/** 自动备份: 导出含照片的 ZIP 到应用外部目录, 只保留最近 KEEP 份。 */
object BackupStore {
    const val KEEP = 5

    fun dir(ctx: Context): File =
        (ctx.getExternalFilesDir(null) ?: ctx.filesDir).resolve("backups").apply { mkdirs() }

    fun latest(ctx: Context): File? =
        dir(ctx).listFiles()
            ?.filter { it.isFile && it.extension == "zip" }
            ?.maxByOrNull { it.lastModified() }

    suspend fun run(ctx: Context): File {
        val dao = EntryPointAccessors.fromApplication(ctx, BackupEntryPoint::class.java).dao()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
        val target = File(dir(ctx), "nashi-" + stamp + ".zip")
        target.outputStream().use { out ->
            NashiIo.exportZip(dao, File(ctx.filesDir, "photos"), out)
        }
        dir(ctx).listFiles()
            ?.filter { it.isFile && it.extension == "zip" }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEEP)
            ?.forEach { it.delete() }
        return target
    }
}

class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        BackupStore.run(applicationContext)
        Result.success()
    } catch (e: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}

object BackupScheduler {
    const val WORK = "nashi-backup-weekly"

    fun schedule(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.DAYS)
                .build(),
        )
    }
}
