package com.larateam.sshmanager.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Battery-optimization exemption helpers (§7.4). Sideloaded tool, so the direct
 * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS dialog is allowed. Used to keep long SSH sessions from being
 * doze-killed; always offered contextually + dismissibly, never nagged.
 */
object BatteryOptimization {

    /** True if the app is already exempt (or the OS can't report — treat as exempt to avoid nagging). */
    fun isIgnoring(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Direct exemption dialog ("Allow"/"Deny") for this package. */
    @SuppressLint("BatteryLife")
    fun requestExemptionIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    /** Fallback: the system battery-optimization list (if the direct dialog isn't available). */
    fun settingsListIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
