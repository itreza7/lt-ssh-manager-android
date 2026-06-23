package com.larateam.sshmanager.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.larateam.sshmanager.data.db.AppDatabase
import com.larateam.sshmanager.data.db.MIGRATION_1_2
import com.larateam.sshmanager.data.db.MIGRATION_2_3
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration2to3Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate2To3_adds_known_hosts_and_preserves_connections_and_secrets() {
        // Seed v2 with a connection and an encrypted secret.
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO connections (name, host, port, username, auth_method, key_alias) " +
                    "VALUES ('Vault', 'vault.example.com', 22, 'deploy', 'IN_APP_KEY', 'deploy-key')",
            )
            execSQL("INSERT INTO secrets (ref, ciphertext, iv) VALUES ('deploy-key', X'0011', X'2233')")
            close()
        }

        // Migrate straight to v3 (validates against 3.json).
        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        db.query("SELECT COUNT(*) FROM connections").use { c -> c.moveToFirst(); check(c.getInt(0) == 1) }
        db.query("SELECT COUNT(*) FROM secrets").use { c -> c.moveToFirst(); check(c.getInt(0) == 1) }
        db.execSQL("INSERT INTO known_hosts (host_port, fingerprint) VALUES ('vault.example.com:22', 'SHA256:abc')")
        db.close()
    }

    @Test
    fun migrate1To3_runs_all_migrations_in_order() {
        helper.createDatabase(TEST_DB_ALL, 1).apply {
            execSQL(
                "INSERT INTO connections (name, host, port, username, auth_method, key_alias) " +
                    "VALUES ('P', 'h', 22, 'u', 'KEY', NULL)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB_ALL, 3, true, MIGRATION_1_2, MIGRATION_2_3)
        db.query("SELECT COUNT(*) FROM connections").use { c -> c.moveToFirst(); check(c.getInt(0) == 1) }
        db.close()
    }

    companion object {
        private const val TEST_DB = "migration-2to3-test"
        private const val TEST_DB_ALL = "migration-1to3-test"
    }
}
