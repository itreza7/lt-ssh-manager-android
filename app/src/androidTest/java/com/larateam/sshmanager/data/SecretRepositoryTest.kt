package com.larateam.sshmanager.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.larateam.sshmanager.data.crypto.KeystoreCrypto
import com.larateam.sshmanager.data.db.AppDatabase
import com.larateam.sshmanager.data.model.AuthMethod
import com.larateam.sshmanager.data.model.Connection
import com.larateam.sshmanager.data.repo.ConnectionRepository
import com.larateam.sshmanager.data.repo.SecretRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecretRepositoryTest {

    private val alias = "larateam.test.secretrepo.${System.nanoTime()}"
    private lateinit var db: AppDatabase
    private lateinit var crypto: KeystoreCrypto
    private lateinit var secrets: SecretRepository
    private lateinit var connections: ConnectionRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        crypto = KeystoreCrypto(alias)
        secrets = SecretRepository(crypto, db.secretDao())
        connections = ConnectionRepository(db.connectionDao(), db.secretDao())
    }

    @After
    fun tearDown() {
        db.close()
        crypto.deleteKey()
    }

    @Test
    fun store_then_reveal_yields_original_and_persists_only_ciphertext() = runTest {
        val key = "BEGIN OPENSSH PRIVATE KEY\nabc123\nEND".encodeToByteArray()
        secrets.store("my-key", key)

        val row = db.secretDao().getByRef("my-key")
        assertNotNull(row)
        assertFalse("must store ciphertext, not plaintext", row!!.ciphertext.contentEquals(key))

        assertArrayEquals(key, secrets.reveal("my-key"))
    }

    @Test
    fun saved_password_round_trips_and_persists_only_ciphertext() = runTest {
        val connId = 42L
        val password = "s3cr3t-päss".toCharArray() // includes a non-ASCII char to exercise UTF-8
        secrets.storePassword(connId, password.copyOf())

        val row = db.secretDao().getByRef(SecretRepository.passwordRef(connId))
        assertNotNull(row)
        assertFalse(
            "must store ciphertext, not the password bytes",
            row!!.ciphertext.contentEquals("s3cr3t-päss".encodeToByteArray()),
        )

        assertTrue(secrets.hasPassword(connId))
        assertArrayEquals(password, secrets.revealPassword(connId))

        secrets.deletePassword(connId)
        assertFalse(secrets.hasPassword(connId))
        assertNull(secrets.revealPassword(connId))
    }

    @Test
    fun deleting_connection_removes_its_saved_password() = runTest {
        val id = connections.save(
            Connection(name = "Box", host = "h", port = 22, username = "u", authMethod = AuthMethod.PASSWORD),
        )
        secrets.storePassword(id, "hunter2".toCharArray())
        assertTrue(secrets.hasPassword(id))

        connections.delete(connections.getConnection(id)!!)

        assertFalse(secrets.hasPassword(id))
    }

    @Test
    fun deleting_connection_removes_its_secret() = runTest {
        val id = connections.save(
            Connection(
                name = "Vault",
                host = "h",
                port = 22,
                username = "u",
                authMethod = AuthMethod.IN_APP_KEY,
                keyAlias = "vault-key",
            ),
        )
        secrets.store("vault-key", "secret".encodeToByteArray())
        assertTrue(secrets.has("vault-key"))

        connections.delete(connections.getConnection(id)!!)

        assertFalse(secrets.has("vault-key"))
    }
}
