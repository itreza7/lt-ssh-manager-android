package com.larateam.sshmanager.terminal

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.larateam.sshmanager.data.model.AuthCredentials
import com.larateam.sshmanager.data.model.ConnState
import com.larateam.sshmanager.data.model.ConnectionTarget
import com.larateam.sshmanager.data.model.HostKeyInfo
import com.larateam.sshmanager.ssh.ShellIo
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Session-registry lifecycle (channels) + the disposal-safety / off-screen-keeps-receiving guard.
 * Uses a fake gateway + in-memory pipe (no real SSH), but the REAL com.termux TerminalSession (needs
 * an Android Looper, hence instrumented). The fake gateway models the consolidated model: it tracks
 * distinct hosts ("connections") and per-channel handles.
 */
@RunWith(AndroidJUnit4::class)
class TerminalSessionStoreTest {

    private class FakeShell : ShellIo {
        private val pipeOut = PipedOutputStream()
        override val inputStream = PipedInputStream(pipeOut, 64 * 1024)
        override val outputStream = ByteArrayOutputStream()
        fun feed(text: String) { pipeOut.write(text.toByteArray()); pipeOut.flush() }
        fun eof() { runCatching { pipeOut.close() } }
        override fun resize(columns: Int, rows: Int, widthPx: Int, heightPx: Int) {}
        override fun close() { eof() }
    }

    private class FakeGateway : TerminalGateway {
        val shellsByChannel = ConcurrentHashMap<Long, FakeShell>()
        val connectedHosts = ConcurrentHashMap.newKeySet<Long>()
        private val nextChannel = AtomicLong(1)
        private val _count = MutableStateFlow(0)
        override val activeCount: StateFlow<Int> = _count
        override fun isConnected(connectionId: Long): Boolean = connectedHosts.contains(connectionId)
        override suspend fun open(connectionId: Long, target: ConnectionTarget, credentials: AuthCredentials?, columns: Int, rows: Int): GatewayResult {
            connectedHosts.add(connectionId) // one "connection" per distinct host
            val channelId = nextChannel.getAndIncrement()
            val shell = FakeShell()
            shellsByChannel[channelId] = shell
            _count.value += 1
            return GatewayResult.Ok(channelId, shell, HostKeyInfo("SHA256:fake", true))
        }
        override fun release(channelId: Long) {
            if (shellsByChannel.remove(channelId) != null) _count.value = (_count.value - 1).coerceAtLeast(0)
        }
    }

    private fun newStore(gateway: FakeGateway): TerminalSessionStore = TerminalSessionStore(
        gateway = gateway,
        terminalFactory = { ch, client ->
            TerminalSession(ch.inputStream, ch.outputStream, 2000, client) { _, _, _, _ -> }
        },
        mainDispatcher = Dispatchers.Main,
        scope = CoroutineScope(Dispatchers.Main),
    )

    private fun target() = ConnectionTarget("h", 22, "u")
    private fun creds() = AuthCredentials.Password("x".toCharArray())

    private fun awaitUntil(timeoutMs: Long = 5000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (cond()) return
            Thread.sleep(40)
        }
        throw AssertionError("condition not met within $timeoutMs ms")
    }

    @Test
    fun N_tabs_to_one_host_yield_one_connection_N_channels_and_closing_decrements() {
        val gateway = FakeGateway()
        val store = newStore(gateway)

        // Three tabs to the SAME host (connectionId = 1).
        repeat(3) { store.openTab(connectionId = 1L, target = target(), credentials = creds(), title = "t$it") }
        awaitUntil { store.tabs.value.size == 3 && store.tabs.value.all { it.terminalSession != null && it.channelId != null } }
        assertEquals(1, gateway.connectedHosts.size)   // ONE connection
        assertEquals(3, gateway.shellsByChannel.size)  // THREE channels
        assertEquals(3, store.activeCount.value)

        val first = store.tabs.value.first().tabId
        store.closeTab(first)
        awaitUntil { store.tabs.value.size == 2 && store.activeCount.value == 2 }

        store.disconnectAll()
        awaitUntil { store.tabs.value.isEmpty() && store.activeCount.value == 0 }
        assertTrue(gateway.shellsByChannel.isEmpty()) // every channel released (last release closes the connection)
    }

    @Test
    fun offscreen_session_keeps_receiving_without_a_bound_view() {
        val gateway = FakeGateway()
        val store = newStore(gateway)
        store.openTab(connectionId = 1L, target = target(), credentials = creds(), title = "bg")
        awaitUntil { store.tabs.value.firstOrNull()?.terminalSession != null }

        val tabId = store.tabs.value.first().tabId
        gateway.shellsByChannel.values.first().feed("OFFSCREEN-MARKER-42\r\n")

        awaitUntil {
            store.terminalSession(tabId)?.emulator?.screen?.transcriptText?.contains("OFFSCREEN-MARKER-42") == true
        }
        assertEquals(null, store.tabs.value.first().client.view) // never bound a view
        assertTrue(store.terminalSession(tabId)!!.emulator.screen.transcriptText.contains("OFFSCREEN-MARKER-42"))
    }

    @Test
    fun server_closing_the_channel_marks_disconnected_decrements_but_keeps_the_tab() {
        val gateway = FakeGateway()
        val store = newStore(gateway)
        store.openTab(connectionId = 1L, target = target(), credentials = creds(), title = "exit")
        awaitUntil { store.tabs.value.firstOrNull()?.terminalSession != null }
        val tab = store.tabs.value.first()
        assertEquals(1, store.activeCount.value)

        gateway.shellsByChannel.values.first().eof() // server drops the channel (e.g. `exit`)

        awaitUntil { tab.state.value is ConnState.Disconnected }
        awaitUntil { store.activeCount.value == 0 }
        assertTrue(store.tabs.value.any { it.tabId == tab.tabId }) // tab retained until explicitly closed
    }
}
