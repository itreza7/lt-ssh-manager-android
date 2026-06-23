package com.larateam.sshmanager.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SecretDao {

    @Upsert
    suspend fun upsert(secret: SecretEntity)

    @Query("SELECT * FROM secrets WHERE ref = :ref")
    suspend fun getByRef(ref: String): SecretEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM secrets WHERE ref = :ref)")
    suspend fun exists(ref: String): Boolean

    @Query("DELETE FROM secrets WHERE ref = :ref")
    suspend fun deleteByRef(ref: String)
}
