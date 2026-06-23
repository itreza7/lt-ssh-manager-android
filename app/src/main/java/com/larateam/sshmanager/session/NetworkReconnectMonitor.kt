package com.larateam.sshmanager.session

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.larateam.sshmanager.terminal.TerminalSessionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches the default network (CLAUDE.md §7.6). When connectivity returns after a drop (e.g. a
 * WiFi<->cellular switch), it asks the [TerminalSessionStore] to auto-reconnect tabs that were live
 * and then dropped — debounced so a flapping network doesn't trigger a storm. The actual drop is
 * DETECTED by sshj keepalive (the channel EOFs); this just kicks recovery when the network is back.
 */
@Singleton
class NetworkReconnectMonitor @Inject constructor(
    @ApplicationContext context: Context,
    private val store: TerminalSessionStore,
) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var debounceJob: Job? = null
    private var started = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = scheduleReconnect()
        // onLost: no action — keepalive + the channel reader mark affected tabs Disconnected.
    }

    fun start() {
        if (started) return
        started = true
        runCatching { cm?.registerDefaultNetworkCallback(callback) }
    }

    private fun scheduleReconnect() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS) // collapse network flapping into one reconnect sweep
            store.reconnectDropped()
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 1_500L
    }
}
