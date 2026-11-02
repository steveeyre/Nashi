package com.nashi

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nashi.ui.theme.NashiTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import com.nashi.core.backup.BackupScheduler
import com.nashi.core.data.MaintenanceScheduler
import com.nashi.core.remind.ReminderScheduler
import com.nashi.core.sync.SyncScheduler

@HiltAndroidApp
class NashiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncScheduler.schedulePeriodic(this)
        ReminderScheduler.schedule(this)
        BackupScheduler.schedule(this)
        MaintenanceScheduler.schedule(this)
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NashiTheme { NashiRoot() } }
    }
}
