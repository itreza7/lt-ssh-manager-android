package com.larateam.sshmanager.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.larateam.sshmanager.data.db.AppDatabase
import com.larateam.sshmanager.data.model.AuthMethod
import com.larateam.sshmanager.data.model.Connection
import com.larateam.sshmanager.data.repo.ConnectionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectionRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ConnectionRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ConnectionRepository(db.connectionDao(), db.secretDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun save_inserts_new_and_emits_via_flow() = runTest {
        val id = repository.save(
            Connection(name = "Prod", host = "h", port = 22, username = "u", authMethod = AuthMethod.PASSWORD),
        )
        assertTrue(id > 0)
        val list = repository.connections.first()
        assertEquals(1, list.size)
        assertEquals(id, list.first().id)
        assertEquals("u@h:22", list.first().endpoint)
        assertEquals(AuthMethod.PASSWORD, list.first().authMethod)
    }

    @Test
    fun save_updates_existing_without_adding_a_row() = runTest {
        val id = repository.save(Connection(name = "Prod", host = "h", port = 22, username = "u"))
        repository.save(repository.getConnection(id)!!.copy(host = "h2", port = 2200))
        val updated = repository.getConnection(id)!!
        assertEquals("h2", updated.host)
        assertEquals(2200, updated.port)
        assertEquals(1, repository.connections.first().size)
    }

    @Test
    fun delete_removes_connection() = runTest {
        val id = repository.save(Connection(name = "Prod", host = "h", port = 22, username = "u"))
        repository.delete(repository.getConnection(id)!!)
        assertTrue(repository.connections.first().isEmpty())
    }
}
