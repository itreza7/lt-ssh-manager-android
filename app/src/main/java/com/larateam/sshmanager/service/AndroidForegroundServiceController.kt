package com.larateam.sshmanager.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.larateam.sshmanager.ssh.ForegroundServiceController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Starts [SshForegroundService] as a foreground service (used on the 0 -> 1 active transition). */
@Singleton
class AndroidForegroundServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
) : ForegroundServiceController {
    override fun start() {
        ContextCompat.startForegroundService(context, Intent(context, SshForegroundService::class.java))
    }
}
