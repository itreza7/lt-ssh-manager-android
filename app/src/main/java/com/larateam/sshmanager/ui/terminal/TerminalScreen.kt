package com.larateam.sshmanager.ui.terminal

import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.larateam.sshmanager.data.model.AppSettings
import com.larateam.sshmanager.terminal.SshTerminalSessionClient
import com.larateam.sshmanager.terminal.SshTerminalViewClient
import com.larateam.sshmanager.terminal.TerminalFont
import com.larateam.sshmanager.terminal.TerminalKeys
import com.larateam.sshmanager.terminal.TerminalModifiers
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView

/**
 * Renders ONE [TerminalSession] via the vendored Termux [TerminalView] (an Android View hosted in
 * Compose). The session's byte source is supplied by the caller — static demo bytes for the STEP 0
 * render proof, or the SSH shell channel for a live shell. Includes the mandatory extra-keys row,
 * IME-aware insets, and pinch-zoom font sizing (handled inside [SshTerminalViewClient.onScale]).
 */
@Composable
fun TerminalScreen(
    sessionFactory: (SshTerminalSessionClient) -> TerminalSession,
    modifier: Modifier = Modifier,
) {
    val modifiers = remember { TerminalModifiers() }
    val sessionClient = remember { SshTerminalSessionClient() }
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }
    val session = remember { sessionFactory(sessionClient) }

    Column(modifier = modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { ctx ->
                TerminalView(ctx, null).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    // Opaque dark background: the emulator's default bg (black) is not painted over
                    // the light Compose surface, so make the view itself black for white-on-black.
                    setBackgroundColor(android.graphics.Color.BLACK)
                    val density = ctx.resources.displayMetrics.density
                    var textSizePx = 16f * density
                    setTextSize(textSizePx.toInt())
                    runCatching { setTypeface(TerminalFont.get(ctx)) }
                    setTerminalViewClient(
                        SshTerminalViewClient(
                            modifiers = modifiers,
                            minTextSizePx = 8f * density,
                            maxTextSizePx = 40f * density,
                            currentTextSizePx = { textSizePx },
                            onTextSizeChanged = { newPx -> textSizePx = newPx; setTextSize(newPx.toInt()) },
                            onTap = { requestFocus() },
                        ),
                    )
                    sessionClient.view = this
                    attachSession(session)
                    isFocusableInTouchMode = true
                    requestFocus()
                    terminalView = this
                }
            },
        )

        ExtraKeysRow(
            keys = remember { TerminalKeys.parseLayout(AppSettings.DEFAULT_TERMINAL_KEYS) },
            modifiers = modifiers,
            onSend = { bytes -> session.write(bytes, 0, bytes.size) },
            refocus = { terminalView?.requestFocus() },
            showKeyboard = { terminalView?.requestFocus() },
        )
    }
}
