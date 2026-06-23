package com.larateam.sshmanager.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ConnectionEntity::class, SecretEntity::class, KnownHostEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun connectionDao(): ConnectionDao
    abstract fun secretDao(): SecretDao
    abstract fun knownHostDao(): KnownHostDao

    companion object {
        const val NAME = "sshmanager.db"
    }
}
