package com.larateam.sshmanager.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionDao {

    @Query("SELECT * FROM connections ORDER BY name COLLATE NOCASE ASC, host COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ConnectionEntity>>

    @Query("SELECT * FROM connections WHERE id = :id")
    suspend fun getById(id: Long): ConnectionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ConnectionEntity): Long

    @Update
    suspend fun update(entity: ConnectionEntity)

    @Delete
    suspend fun delete(entity: ConnectionEntity)

    @Query("DELETE FROM connections WHERE id = :id")
    suspend fun deleteById(id: Long)
}
