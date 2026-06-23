package com.larateam.sshmanager.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ForegroundServiceChannelTest {

    @Test
    fun starting_service_creates_low_importance_channel_and_does_not_crash() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel(SshForegroundService.CHANNEL_ID)

        // Start as a foreground service (gives the 5s startForeground() window). The service
        // creates the channel in onCreate and promotes itself in onStartCommand; with no active
        // sessions it then stops itself. If startForeground() were not called in time, the OS would
        // crash the app and this test would fail.
        ContextCompat.startForegroundService(ctx, Intent(ctx, SshForegroundService::class.java))

        val deadline = System.currentTimeMillis() + 5_000
        var channel = nm.getNotificationChannel(SshForegroundService.CHANNEL_ID)
        while (channel == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
            channel = nm.getNotificationChannel(SshForegroundService.CHANNEL_ID)
        }

        assertNotNull("foreground service must create its notification channel", channel)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel!!.importance)

        ctx.stopService(Intent(ctx, SshForegroundService::class.java))
    }
}
