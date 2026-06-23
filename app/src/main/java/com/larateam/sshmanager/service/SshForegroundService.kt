package com.larateam.sshmanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.larateam.sshmanager.MainActivity
import com.larateam.sshmanager.R
import com.larateam.sshmanager.ssh.SshConnectionManager
import com.larateam.sshmanager.terminal.TerminalSessionStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps SSH sessions alive while the app is backgrounded / swiped from Recents.
 *
 * It does NOT own the connections — [SshConnectionManager] (a @Singleton) does. The service merely
 * runs as a foreground service while the manager's active-session count > 0 (it observes
 * [SshConnectionManager.activeCount]) and stops itself when the count reaches 0.
 *
 * FGS type = specialUse (no time cap, unlike dataSync). START_NOT_STICKY — we do not auto-recreate
 * connections the system can't restore (restore is Phase 11).
 */
@AndroidEntryPoint
class SshForegroundService : Service() {

    @Inject
    lateinit var manager: SshConnectionManager

    @Inject
    lateinit var terminalStore: TerminalSessionStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        scope.launch {
            manager.activeCount.collectLatest { count ->
                when (ForegroundPolicy.actionFor(count)) {
                    ForegroundPolicy.Action.GO_FOREGROUND -> goForeground(count)
                    ForegroundPolicy.Action.STOP -> {
                        ServiceCompat.stopForeground(this@SshForegroundService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT_ALL) {
            // Close all terminal tabs (the registry above the pager) AND any non-tab sessions.
            terminalStore.disconnectAll()
            scope.launch { manager.disconnectAll() } // count -> 0 -> the collector stops the service
        }
        // Promote to foreground promptly (must be within ~5s of startForegroundService()).
        goForeground(manager.activeCount.value.coerceAtLeast(1))
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun goForeground(count: Int) {
        val notification = buildNotification(count)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SSH sessions",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps SSH sessions alive while the app is in the background"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(count: Int): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PI_FLAGS,
        )
        val disconnectAll = PendingIntent.getService(
            this,
            1,
            Intent(this, SshForegroundService::class.java).setAction(ACTION_DISCONNECT_ALL),
            PI_FLAGS,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("SSH Manager")
            .setContentText(if (count == 1) "1 active session" else "$count active sessions")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Disconnect all", disconnectAll)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "ssh_sessions"
        const val NOTIF_ID = 1001
        const val ACTION_DISCONNECT_ALL = "com.larateam.sshmanager.action.DISCONNECT_ALL"
        private const val PI_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}
