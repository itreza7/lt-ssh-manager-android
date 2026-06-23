package com.larateam.sshmanager.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

/**
 * Armed Ctrl/Alt/Fn modifiers from the extra-keys row, applied to the next key. Compose-backed so the
 * row's buttons visibly light up when armed (the toggles are read both by Compose and the input path).
 */
class TerminalModifiers {
    var ctrl by mutableStateOf(false)
    var alt by mutableStateOf(false)
    var fn by mutableStateOf(false)

    /** Read-and-consume: the modifier applies to a single following key, then disarms. */
    fun consumeCtrl(): Boolean = ctrl.also { ctrl = false }
    fun consumeAlt(): Boolean = alt.also { alt = false }
    fun consumeFn(): Boolean = fn.also { fn = false }

    fun isArmed(kind: ModKind): Boolean = when (kind) {
        ModKind.CTRL -> ctrl
        ModKind.ALT -> alt
        ModKind.FN -> fn
    }

    fun toggle(kind: ModKind) {
        when (kind) {
            ModKind.CTRL -> ctrl = !ctrl
            ModKind.ALT -> alt = !alt
            ModKind.FN -> fn = !fn
        }
    }
}

/**
 * Session callbacks. Crucially logging is a no-op — terminal bytes can contain secrets, so we never
 * log them (CLAUDE.md §4 #5). [view] is wired after construction to drive redraws.
 */
class SshTerminalSessionClient(
    private val onFinished: () -> Unit = {},
) : TerminalSessionClient {
    var view: TerminalView? = null

    override fun onTextChanged(changedSession: TerminalSession) { view?.onScreenUpdated() }
    override fun onTitleChanged(changedSession: TerminalSession) {}
    override fun onSessionFinished(finishedSession: TerminalSession) { onFinished() }

    // Selection "Copy": push the selected text into the Android clipboard (the Termux callback is a
    // no-op by default, so without this the Copy menu item does nothing).
    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
        if (text.isNullOrEmpty()) return
        val context = view?.context ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("Terminal", text))
    }

    // Selection "Paste": write the clipboard text to the remote shell's stdin (UTF-8 bytes).
    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val context = view?.context ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
        if (text.isEmpty()) return
        val bytes = text.toByteArray(Charsets.UTF_8)
        session?.write(bytes, 0, bytes.size)
    }
    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) { view?.onScreenUpdated() }
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
    override fun getTerminalCursorStyle(): Int? = null

    // Never log terminal I/O.
    override fun logError(tag: String?, message: String?) {}
    override fun logWarn(tag: String?, message: String?) {}
    override fun logInfo(tag: String?, message: String?) {}
    override fun logDebug(tag: String?, message: String?) {}
    override fun logVerbose(tag: String?, message: String?) {}
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
    override fun logStackTrace(tag: String?, e: Exception?) {}
}

/**
 * View callbacks. Pinch-zoom adjusts the font size (via [onTextSizeChanged]); Ctrl/Alt/Fn are read
 * from the extra-keys [modifiers] and consumed per key.
 */
class SshTerminalViewClient(
    private val modifiers: TerminalModifiers,
    private val minTextSizePx: Float,
    private val maxTextSizePx: Float,
    private val currentTextSizePx: () -> Float,
    private val onTextSizeChanged: (Float) -> Unit,
    private val onTap: () -> Unit,
) : TerminalViewClient {

    override fun onScale(scale: Float): Float {
        if (scale < 0.9f || scale > 1.1f) {
            val next = (currentTextSizePx() * scale).coerceIn(minTextSizePx, maxTextSizePx)
            onTextSizeChanged(next)
            return next
        }
        return currentTextSizePx()
    }

    override fun onSingleTapUp(e: MotionEvent?) { onTap() }
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = true // disables IME autocorrect/suggestions
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}

    // Volume Up/Down grow/shrink the terminal font (consumed here so the system volume doesn't change).
    override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> { adjustFont(+1); true }
        KeyEvent.KEYCODE_VOLUME_DOWN -> { adjustFont(-1); true }
        else -> false
    }

    private fun adjustFont(direction: Int) {
        val current = currentTextSizePx()
        val step = (current * 0.12f).coerceAtLeast(2f)
        onTextSizeChanged((current + direction * step).coerceIn(minTextSizePx, maxTextSizePx))
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean =
        // Swallow the matching VOLUME up-events too, so the system never sees a complete press.
        keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
    override fun onLongPress(event: MotionEvent?): Boolean = false

    override fun readControlKey(): Boolean = modifiers.consumeCtrl()
    override fun readAltKey(): Boolean = modifiers.consumeAlt()
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = modifiers.consumeFn()

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean = false
    override fun onEmulatorSet() {}

    override fun logError(tag: String?, message: String?) {}
    override fun logWarn(tag: String?, message: String?) {}
    override fun logInfo(tag: String?, message: String?) {}
    override fun logDebug(tag: String?, message: String?) {}
    override fun logVerbose(tag: String?, message: String?) {}
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
    override fun logStackTrace(tag: String?, e: Exception?) {}
}
