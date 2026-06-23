package com.larateam.sshmanager.ui.terminal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.larateam.sshmanager.terminal.ExtraKey
import com.larateam.sshmanager.terminal.TerminalModifiers
import com.larateam.sshmanager.ui.theme.Cyan
import com.larateam.sshmanager.ui.theme.InkContainerHigh
import com.larateam.sshmanager.ui.theme.InkOutline
import com.larateam.sshmanager.ui.theme.OnCyan
import com.larateam.sshmanager.ui.theme.OnInk

/**
 * The mobile extra-keys row, rendered from the user-editable [keys] layout. [Send][ExtraKey.Send] keys
 * write bytes straight to the shell via [onSend] (so macros like tmux's Ctrl-b `[` work regardless of
 * IME focus); modifiers arm sticky state for the next soft-keyboard key; the keyboard key pops the IME.
 */
@Composable
fun ExtraKeysRow(
    keys: List<ExtraKey>,
    modifiers: TerminalModifiers,
    onSend: (ByteArray) -> Unit,
    refocus: () -> Unit,
    showKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        keys.forEach { key ->
            when (key) {
                is ExtraKey.Kbd -> KeyButton(key.label) { showKeyboard() }
                is ExtraKey.Mod -> ModifierButton(key.label, armed = { modifiers.isArmed(key.kind) }) {
                    // Arm the modifier, then hand focus + keyboard back so the NEXT key reaches the terminal.
                    modifiers.toggle(key.kind)
                    showKeyboard()
                }
                is ExtraKey.Send -> KeyButton(key.label) {
                    onSend(key.sequence)
                    refocus()
                }
            }
        }
    }
}

// Non-focusable so tapping an extra key never pulls IME focus off the embedded terminal — the
// armed Ctrl/Alt must still apply to the NEXT soft-keyboard key.
private val noFocus = Modifier.focusProperties { canFocus = false }

// The terminal chrome is always dark (black canvas), so the keys use the dark "Ink" vocabulary
// directly — light glyphs on a raised slate chip — instead of theme colors that vanish on black.
private val keyShape = RoundedCornerShape(8.dp)
private val keyPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
private val keyTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp,
    letterSpacing = 0.5.sp,
)

@Composable
private fun KeyButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = noFocus,
        shape = keyShape,
        border = BorderStroke(1.dp, InkOutline),
        colors = ButtonDefaults.buttonColors(containerColor = InkContainerHigh, contentColor = OnInk),
        contentPadding = keyPadding,
    ) { Text(label, style = keyTextStyle) }
}

@Composable
private fun ModifierButton(label: String, armed: () -> Boolean, onClick: () -> Unit) {
    val isArmed = armed()
    // Armed → glows in the brand cyan so it's obvious the next key carries the modifier.
    Button(
        onClick = onClick,
        modifier = noFocus,
        shape = keyShape,
        border = BorderStroke(1.dp, if (isArmed) Cyan else InkOutline),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isArmed) Cyan else InkContainerHigh,
            contentColor = if (isArmed) OnCyan else OnInk,
        ),
        contentPadding = keyPadding,
    ) { Text(label, style = keyTextStyle) }
}
