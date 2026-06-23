package com.larateam.sshmanager.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.larateam.sshmanager.data.db.AppDatabase
import com.larateam.sshmanager.data.db.MIGRATION_1_2
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate1To2_adds_secrets_table_and_keeps_connections() {
        // Create the v1 database from the exported 1.json schema and seed a connection.
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO connections (name, host, port, username, auth_method, key_alias) " +
                    "VALUES ('Prod', 'example.com', 22, 'root', 'KEY', NULL)",
            )
            close()
        }

        // Migrate to v2 and validate the schema matches 2.json.
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        // The original row survived and the new secrets table is usable.
        db.query("SELECT COUNT(*) FROM connections").use { c ->
            c.moveToFirst()
            check(c.getInt(0) == 1) { "connections row should survive migration" }
        }
        db.execSQL("INSERT INTO secrets (ref, ciphertext, iv) VALUES ('r', X'0011', X'2233')")
        db.close()
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
