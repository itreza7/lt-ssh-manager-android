package com.larateam.sshmanager.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.larateam.sshmanager.data.db.AppDatabase
import com.larateam.sshmanager.data.db.ConnectionDao
import com.larateam.sshmanager.data.db.ConnectionEntity
import com.larateam.sshmanager.data.model.AuthMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ConnectionDao

    private fun sample(name: String = "Prod", host: String = "example.com") = ConnectionEntity(
        name = name,
        host = host,
        port = 22,
        username = "root",
        authMethod = AuthMethod.KEY,
    )

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.connectionDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insert_then_query() = runTest {
        val id = dao.insert(sample())
        assertTrue(id > 0)
        val all = dao.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("example.com", all.first().host)
        assertEquals(AuthMethod.KEY, all.first().authMethod)
    }

    @Test
    fun getById_returns_row_or_null() = runTest {
        val id = dao.insert(sample(host = "h1"))
        assertEquals("h1", dao.getById(id)?.host)
        assertNull(dao.getById(99_999L))
    }

    @Test
    fun update_changes_row() = runTest {
        val id = dao.insert(sample(name = "Old"))
        dao.update(dao.getById(id)!!.copy(name = "New", port = 2222))
        val row = dao.getById(id)
        assertEquals("New", row?.name)
        assertEquals(2222, row?.port)
    }

    @Test
    fun delete_removes_row() = runTest {
        val id = dao.insert(sample())
        dao.delete(dao.getById(id)!!)
        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test
    fun observeAll_orders_by_name_case_insensitively() = runTest {
        dao.insert(sample(name = "Zeta", host = "z"))
        dao.insert(sample(name = "alpha", host = "a"))
        assertEquals(listOf("alpha", "Zeta"), dao.observeAll().first().map { it.name })
    }
}
