package com.larateam.sshmanager

import android.app.Application
import com.larateam.sshmanager.session.NetworkReconnectMonitor
import com.larateam.sshmanager.ssh.SshProvider
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** Application entry point. Hosts the Hilt dependency graph for the whole process. */
@HiltAndroidApp
class SshManagerApp : Application() {

    @Inject lateinit var networkReconnectMonitor: NetworkReconnectMonitor

    override fun onCreate() {
        super.onCreate()
        // Replace Android's stripped BouncyCastle with the full provider before any SSH crypto.
        SshProvider.ensureRegistered()
        // Start watching the network so dropped sessions auto-reconnect on a WiFi<->cellular switch.
        networkReconnectMonitor.start()
    }
}
