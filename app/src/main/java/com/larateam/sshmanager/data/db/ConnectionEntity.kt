package com.larateam.sshmanager.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.larateam.sshmanager.data.model.Connection

/**
 * Room row for a saved connection. Stores ONLY non-secret metadata (CLAUDE.md §4):
 * no password, passphrase, or private-key material is ever written to this table.
 */
@Entity(tableName = "connections")
data class ConnectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    @ColumnInfo(name = "auth_method") val authMethod: com.larateam.sshmanager.data.model.AuthMethod,
    @ColumnInfo(name = "key_alias") val keyAlias: String? = null,
)

fun ConnectionEntity.toDomain(): Connection = Connection(
    id = id,
    name = name,
    host = host,
    port = port,
    username = username,
    authMethod = authMethod,
    keyAlias = keyAlias,
)

fun Connection.toEntity(): ConnectionEntity = ConnectionEntity(
    id = id,
    name = name,
    host = host,
    port = port,
    username = username,
    authMethod = authMethod,
    keyAlias = keyAlias,
)
