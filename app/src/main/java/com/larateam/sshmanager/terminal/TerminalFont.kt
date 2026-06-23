package com.larateam.sshmanager.terminal

import android.content.Context
import android.graphics.Typeface

/**
 * The terminal's monospace face — JetBrains Mono, bundled in assets — loaded once and cached.
 * Falls back to the system monospace if the asset is somehow unavailable.
 */
object TerminalFont {
    @Volatile private var cached: Typeface? = null

    fun get(context: Context): Typeface {
        cached?.let { return it }
        val tf = runCatching {
            Typeface.createFromAsset(context.applicationContext.assets, ASSET)
        }.getOrDefault(Typeface.MONOSPACE)
        cached = tf
        return tf
    }

    private const val ASSET = "fonts/JetBrainsMono-Regular.ttf"
}
