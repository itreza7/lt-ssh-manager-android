package com.larateam.sshmanager.data.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v1 -> v2: add the `secrets` table (encrypted private-key vault).
 *
 * Real migration — NO destructive fallback (CLAUDE.md §4): once opt-in secrets are stored,
 * a schema bump must never wipe them. This establishes the migration pattern for later phases.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `secrets` " +
                "(`ref` TEXT NOT NULL, `ciphertext` BLOB NOT NULL, `iv` BLOB NOT NULL, " +
                "PRIMARY KEY(`ref`))",
        )
    }
}

/** v2 -> v3: add the `known_hosts` table (pinned TOFU host-key fingerprints). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `known_hosts` " +
                "(`host_port` TEXT NOT NULL, `fingerprint` TEXT NOT NULL, " +
                "PRIMARY KEY(`host_port`))",
        )
    }
}
