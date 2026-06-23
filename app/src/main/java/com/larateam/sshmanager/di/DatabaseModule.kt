package com.larateam.sshmanager.di

import android.content.Context
import androidx.room.Room
import com.larateam.sshmanager.data.db.AppDatabase
import com.larateam.sshmanager.data.db.ConnectionDao
import com.larateam.sshmanager.data.db.KnownHostDao
import com.larateam.sshmanager.data.db.MIGRATION_1_2
import com.larateam.sshmanager.data.db.MIGRATION_2_3
import com.larateam.sshmanager.data.db.SecretDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // Real migrations only — never fallbackToDestructiveMigration (CLAUDE.md §4).
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    fun provideConnectionDao(db: AppDatabase): ConnectionDao = db.connectionDao()

    @Provides
    fun provideSecretDao(db: AppDatabase): SecretDao = db.secretDao()

    @Provides
    fun provideKnownHostDao(db: AppDatabase): KnownHostDao = db.knownHostDao()
}
