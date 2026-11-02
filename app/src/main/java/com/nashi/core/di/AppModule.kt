package com.nashi.core.di

import android.content.Context
import androidx.room.Room
import com.nashi.core.data.MIGRATION_1_2
import com.nashi.core.data.NashiDao
import com.nashi.core.data.NashiDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun db(@ApplicationContext ctx: Context): NashiDatabase =
        Room.databaseBuilder(ctx, NashiDatabase::class.java, "nashi.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun dao(db: NashiDatabase): NashiDao = db.dao()
}
