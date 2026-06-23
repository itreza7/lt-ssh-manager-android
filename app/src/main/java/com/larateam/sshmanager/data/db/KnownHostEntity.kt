package com.larateam.sshmanager.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Pinned SHA256 host-key fingerprint per "host:port" (TOFU). Non-secret. */
@Entity(tableName = "known_hosts")
data class KnownHostEntity(
    @PrimaryKey @ColumnInfo(name = "host_port") val hostPort: String,
    @ColumnInfo(name = "fingerprint") val fingerprintSha256: String,
)
