package com.larateam.sshmanager.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KnownHostDao {

    @Query("SELECT * FROM known_hosts WHERE host_port = :hostPort")
    suspend fun get(hostPort: String): KnownHostEntity?

    /** First-contact pin only — ABORTs if a pin already exists (the verifier never overwrites). */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: KnownHostEntity)

    /** Explicit, deliberate re-pin of a changed key (a user-driven security decision). */
    @Query("UPDATE known_hosts SET fingerprint = :fingerprint WHERE host_port = :hostPort")
    suspend fun updateFingerprint(hostPort: String, fingerprint: String)

    @Query("SELECT * FROM known_hosts")
    suspend fun all(): List<KnownHostEntity>

    @Query("SELECT * FROM known_hosts ORDER BY host_port")
    fun observeAll(): Flow<List<KnownHostEntity>>

    @Query("DELETE FROM known_hosts WHERE host_port = :hostPort")
    suspend fun deleteByHostPort(hostPort: String)
}
