package com.larateam.sshmanager.ssh

import com.larateam.sshmanager.data.model.AuthCredentials
import com.larateam.sshmanager.data.model.ConnState
import com.larateam.sshmanager.data.model.ConnectionTarget
import com.larateam.sshmanager.data.model.HostKeyInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger

class SshConnectionManagerTest {

    private class FakeStore : KnownHostStore {
        override fun pinnedFingerprint(host: String, port: Int): String? = null
        override fun pin(host: String, port: Int, fingerprintSha256: String) {}
    }

    private class FakeShellIo : ShellIo {
        var closed = false
        override val inputStream = ByteArrayInputStream(ByteArray(0))
        override val outputStream = ByteArrayOutputStream()
        override fun resize(columns: Int, rows: Int, widthPx: Int, heightPx: Int) {}
        override fun close() { closed = true }
    }

    private class FakeSession : SshSession {
        val _state = MutableStateFlow<ConnState>(ConnState.Disconnected)
        override val state = _state
        override val isConnected get() = _state.value is ConnState.Connected
        val connectCount = AtomicInteger(0)
        val openShellCount = AtomicInteger(0)
        var disconnectCalled = false
        override suspend fun connect(credentials: AuthCredentials) {
            connectCount.incrementAndGet()
            credentials.clear()
            _state.value = ConnState.Connecting
            _state.value = ConnState.Connected(HostKeyInfo("SHA256:fake", true))
        }
        override suspend fun openShell(columns: Int, rows: Int): ShellIo {
            openShellCount.incrementAndGet()
            return FakeShellIo()
        }
        override suspend fun exec(command: String) = "ok"
        override suspend fun execCapturing(command: String) = ExecResult("ok", "", 0)
        override suspend fun execStream(command: String, onLine: (String) -> Unit) {}
        override suspend fun disconnect() {
            disconnectCalled = true
            _state.value = ConnState.Disconnected
        }
    }

    private val target = ConnectionTarget("h", 22, "u")
    private fun pw() = AuthCredentials.Password("x".toCharArray())
    private fun noController() = object : ForegroundServiceController { override fun start() {} }

    @Test
    fun connect_goes_foreground_on_0_to_1_and_not_again() = runTest {
        var starts = 0
        val manager = SshConnectionManager(
            knownHosts = FakeStore(),
            serviceController = object : ForegroundServiceController { override fun start() { starts++ } },
            appScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            sessionFactory = { FakeSession() },
        )

        manager.connect(1L, target, pw())
        advanceUntilIdle()
        assertEquals(1, manager.activeCount.value)
        assertEquals(1, starts)

        manager.connect(2L, target, pw())
        advanceUntilIdle()
        assertEquals(2, manager.activeCount.value)
        assertEquals(1, starts) // service NOT started again on 1 -> 2
    }

    @Test
    fun reaching_zero_active_when_a_session_errors() = runTest {
        val session = FakeSession()
        val manager = SshConnectionManager(
            knownHosts = FakeStore(),
            serviceController = noController(),
            appScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            sessionFactory = { session },
        )
        manager.connect(1L, target, pw())
        advanceUntilIdle()
        assertEquals(1, manager.activeCount.value)

        session._state.value = ConnState.Error(com.larateam.sshmanager.data.model.ConnError.NETWORK, "drop")
        advanceUntilIdle()
        assertEquals(0, manager.activeCount.value) // service would stop
    }

    @Test
    fun disconnectAll_closes_sessions_and_stops_service() = runTest {
        val created = mutableListOf<FakeSession>()
        val manager = SshConnectionManager(
            knownHosts = FakeStore(),
            serviceController = noController(),
            appScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            sessionFactory = { FakeSession().also { created += it } },
        )
        manager.connect(1L, target, pw())
        manager.connect(2L, target, pw())
        advanceUntilIdle()
        assertEquals(2, manager.activeCount.value)

        manager.disconnectAll()
        advanceUntilIdle()

        assertEquals(0, manager.activeCount.value) // -> ForegroundPolicy.STOP
        assertEquals(2, created.size)
        assertTrue(created.all { it.disconnectCalled })
    }

    // --- Consolidated connection model (one client per host, channels multiplexed, ref-counted) ---

    @Test
    fun one_host_many_channels_authenticates_once_and_closes_on_last_release() = runTest {
        var factoryCalls = 0
        val session = FakeSession()
        val manager = SshConnectionManager(
            knownHosts = FakeStore(),
            serviceController = noController(),
            appScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            sessionFactory = { factoryCalls++; session },
        )

        // Three tabs to ONE host.
        val r1 = manager.openShell(1L, target, pw(), 80, 24)
        val r2 = manager.openShell(1L, target, pw(), 80, 24)   // creds present but should be ignored (reuse)
        val r3 = manager.openShell(1L, target, credentials = null, columns = 80, rows = 24) // explicit no-auth reuse
        advanceUntilIdle()

        assertEquals(1, factoryCalls)                 // ONE connection/SSHClient
        assertEquals(1, session.connectCount.get())   // authenticated ONCE
        assertEquals(3, session.openShellCount.get())  // THREE channels multiplexed over it
        assertEquals(3, manager.activeCount.value)
        assertTrue(manager.isConnected(1L))

        val ids = listOf(r1, r2, r3).map { (it as ShellOpenResult.Opened).channelId }
        assertEquals(3, ids.toSet().size)             // distinct channel ids

        // Closing channels decrements; the connection stays up while >= 1 channel remains.
        manager.releaseChannel(ids[0]); manager.releaseChannel(ids[1]); advanceUntilIdle()
        assertEquals(1, manager.activeCount.value)
        assertFalse(session.disconnectCalled)

        // Last channel released -> the shared connection is disconnected.
        manager.releaseChannel(ids[2]); advanceUntilIdle()
        assertEquals(0, manager.activeCount.value)
        assertTrue(session.disconnectCalled)
        assertFalse(manager.isConnected(1L))
    }

    @Test
    fun second_channel_to_connected_host_does_not_reauthenticate() = runTest {
        val session = FakeSession()
        val manager = SshConnectionManager(
            knownHosts = FakeStore(),
            serviceController = noController(),
            appScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            sessionFactory = { session },
        )
        manager.openShell(1L, target, pw(), 80, 24); advanceUntilIdle()
        assertEquals(1, session.connectCount.get())

        // isConnected -> caller passes null credentials; manager must NOT call connect() again.
        assertTrue(manager.isConnected(1L))
        manager.openShell(1L, target, credentials = null, columns = 80, rows = 24); advanceUntilIdle()
        assertEquals(1, session.connectCount.get()) // STILL one auth
        assertEquals(2, session.openShellCount.get())
    }
}
