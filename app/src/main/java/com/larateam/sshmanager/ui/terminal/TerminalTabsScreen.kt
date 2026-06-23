package com.larateam.sshmanager.ui.terminal

import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.larateam.sshmanager.data.crypto.BiometricGate
import com.larateam.sshmanager.data.model.AppSettings
import com.larateam.sshmanager.data.model.ConnState
import com.larateam.sshmanager.data.model.Connection
import com.larateam.sshmanager.data.model.userMessage
import com.larateam.sshmanager.terminal.SshTerminalViewClient
import com.larateam.sshmanager.terminal.TerminalFont
import com.larateam.sshmanager.terminal.TerminalKeys
import com.larateam.sshmanager.terminal.TerminalModifiers
import com.larateam.sshmanager.terminal.TerminalSessionStore
import com.larateam.sshmanager.ui.theme.Cyan
import com.larateam.sshmanager.ui.theme.InkContainerHigh
import com.larateam.sshmanager.ui.theme.InkOutline
import com.larateam.sshmanager.ui.theme.InkSurface
import com.larateam.sshmanager.ui.theme.OnInk
import com.larateam.sshmanager.ui.theme.OnInkVariant
import com.larateam.sshmanager.ui.theme.StatusBusyDark
import com.larateam.sshmanager.ui.theme.StatusDownDark
import com.larateam.sshmanager.ui.theme.StatusIdleDark
import com.larateam.sshmanager.ui.theme.StatusLiveDark
import com.larateam.sshmanager.util.BatteryOptimization
import com.termux.view.TerminalView
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Multiple concurrent terminal sessions in tabs. The sessions themselves live in
 * [TerminalSessionStore] (above this composition); pages here only BIND/UNBIND a [TerminalView] to an
 * already-alive session, so swiping a tab off-screen (page disposal) never closes it.
 */
@Composable
fun TerminalTabsScreen(
    onAllClosed: () -> Unit,
    viewModel: TerminalTabsViewModel = hiltViewModel(),
) {
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val connections by viewModel.connections.collectAsStateWithLifecycle()
    val showPicker by viewModel.showPicker.collectAsStateWithLifecycle()
    val pendingPassword by viewModel.pendingPassword.collectAsStateWithLifecycle()
    val pendingBiometric by viewModel.pendingBiometric.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val modifiers = remember { TerminalModifiers() }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val gate = remember { BiometricGate() }
    val extraKeys = remember(settings.terminalKeys) { TerminalKeys.parseLayout(settings.terminalKeys) }

    // Focus the visible terminal once it's shown so hardware/volume keys reach it WITHOUT the user
    // having to tap or open the keyboard first (Volume Up/Down resize the font).
    LaunchedEffect(pagerState.currentPage, tabs.size) {
        kotlinx.coroutines.delay(60)
        tabs.getOrNull(pagerState.currentPage)?.client?.view?.requestFocus()
    }

    // Contextual, one-time battery-optimization prompt (§7.4): the first time a (long) session opens,
    // if not already exempt and not previously dismissed. Never re-nags.
    var showBatteryPrompt by remember { mutableStateOf(false) }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.markBatteryPromptDismissed()
    }
    LaunchedEffect(tabs.isNotEmpty(), settings.batteryPromptDismissed) {
        if (tabs.isNotEmpty() && !settings.batteryPromptDismissed && !BatteryOptimization.isIgnoring(context)) {
            showBatteryPrompt = true
        }
    }

    var hadTabs by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(tabs.isNotEmpty()) { if (tabs.isNotEmpty()) hadTabs = true }
    LaunchedEffect(Unit) { if (tabs.isEmpty()) viewModel.openPicker() }
    // Last tab closed (or notification "Disconnect all") -> back to the connections list, FGS stops.
    LaunchedEffect(tabs.isEmpty(), showPicker, pendingPassword, pendingBiometric, hadTabs) {
        if (hadTabs && tabs.isEmpty() && !showPicker && pendingPassword == null && pendingBiometric == null) {
            onAllClosed()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .imePadding(),
    ) {
        TabStrip(
            tabs = tabs,
            currentPage = pagerState.currentPage,
            onSelect = { i -> scope.launch { pagerState.animateScrollToPage(i) } },
            onClose = viewModel::closeTab,
            onAdd = viewModel::openPicker,
            onDisconnectAll = viewModel::disconnectAll,
        )
        HorizontalDivider(color = InkOutline)

        if (tabs.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                Text("No open terminals", color = OnInkVariant)
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                beyondViewportPageCount = 0, // dispose off-screen pages -> exercises bind/unbind-without-close
                key = { index -> tabs.getOrNull(index)?.tabId ?: index.toLong() },
            ) { page ->
                tabs.getOrNull(page)?.let { tab ->
                    TerminalPage(
                        tab,
                        modifiers,
                        settings.terminalFontSizeSp,
                        onFontSizeChanged = viewModel::setTerminalFontSize,
                        onReconnect = { viewModel.reconnect(tab) },
                    )
                }
            }
        }

        ExtraKeysRow(
            keys = extraKeys,
            modifiers = modifiers,
            onSend = { bytes -> tabs.getOrNull(pagerState.currentPage)?.terminalSession?.write(bytes, 0, bytes.size) },
            // A bare re-focus after a send key (so typing continues); arming a modifier or the ⌨ key also
            // pops the keyboard so the NEXT key (e.g. the `b` in tmux's Ctrl-b) reaches the terminal.
            refocus = { tabs.getOrNull(pagerState.currentPage)?.client?.view?.requestFocus() },
            showKeyboard = { focusTerminal(tabs.getOrNull(pagerState.currentPage)?.client?.view, context) },
        )
    }

    if (showPicker) {
        ConnectionPickerDialog(
            connections = connections,
            onPick = viewModel::pickConnection,
            onDismiss = {
                viewModel.dismissPicker()
                if (tabs.isEmpty() && !hadTabs) onAllClosed()
            },
        )
    }

    pendingPassword?.let { c ->
        PasswordDialog(
            title = "Password for ${c.endpoint}",
            onConfirm = { pw, save -> viewModel.addWithPassword(c, pw.toCharArray(), save) },
            onDismiss = viewModel::cancelPrompts,
        )
    }

    pendingBiometric?.let { c ->
        if (activity == null) {
            viewModel.cancelPrompts()
        } else {
            LaunchedEffect(c.id) {
                when (gate.authenticate(activity, "Unlock to connect", "Authenticate to use your saved credential")) {
                    BiometricGate.Outcome.Success -> viewModel.addWithStoredKeyAfterAuth(c)
                    else -> viewModel.cancelPrompts()
                }
            }
        }
    }

    if (showBatteryPrompt) {
        AlertDialog(
            onDismissRequest = { showBatteryPrompt = false; viewModel.markBatteryPromptDismissed() },
            title = { Text("Keep sessions alive?") },
            text = {
                Text(
                    "Android may kill background apps to save battery, which would drop your SSH sessions. " +
                        "Exempt SSH Manager from battery optimization so long sessions (tmux, tail -f, builds) keep " +
                        "running while the app is in the background. You can change this later in Settings.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBatteryPrompt = false
                    runCatching { batteryLauncher.launch(BatteryOptimization.requestExemptionIntent(context)) }
                }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryPrompt = false; viewModel.markBatteryPromptDismissed() }) { Text("Not now") }
            },
        )
    }
}

@Composable
private fun TabStrip(
    tabs: List<TerminalSessionStore.Tab>,
    currentPage: Int,
    onSelect: (Int) -> Unit,
    onClose: (Long) -> Unit,
    onAdd: () -> Unit,
    onDisconnectAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            TabChip(tab = tab, selected = index == currentPage, onSelect = { onSelect(index) }, onClose = { onClose(tab.tabId) })
        }
        IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "New terminal", tint = Cyan) }
        if (tabs.isNotEmpty()) {
            TextButton(onClick = onDisconnectAll) { Text("Disconnect all", color = StatusDownDark) }
        }
    }
}

@Composable
private fun TabChip(
    tab: TerminalSessionStore.Tab,
    selected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val state by tab.state.collectAsStateWithLifecycle()
    Row(
        modifier = Modifier
            .background(
                if (selected) InkContainerHigh else InkSurface,
                RoundedCornerShape(8.dp),
            )
            .border(
                width = 1.dp,
                color = if (selected) Cyan.copy(alpha = 0.6f) else InkOutline,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onSelect)
            .padding(start = 10.dp, end = 2.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(8.dp).background(statusColor(state), CircleShape))
        Text(
            tab.title,
            color = if (selected) OnInk else OnInkVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Close tab", tint = OnInkVariant, modifier = Modifier.size(16.dp))
        }
    }
}

// The terminal chrome is always dark (the canvas is black), so it uses the dark status
// vocabulary directly rather than the theme-resolved palette.
private fun statusColor(state: ConnState): Color = when (state) {
    is ConnState.Connected -> StatusLiveDark
    ConnState.Connecting -> StatusBusyDark
    ConnState.Disconnected -> StatusIdleDark
    is ConnState.Error -> StatusDownDark
}

@Composable
private fun TerminalPage(
    tab: TerminalSessionStore.Tab,
    modifiers: TerminalModifiers,
    fontSizeSp: Int,
    onFontSizeChanged: (Int) -> Unit,
    onReconnect: () -> Unit,
) {
    val state by tab.state.collectAsStateWithLifecycle()
    val session = tab.terminalSession

    // Restored or dropped tab → offer Reconnect (re-resolves auth; tmux re-attaches on success).
    if (state is ConnState.Disconnected || state is ConnState.Error) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val msg = (state as? ConnState.Error)?.error?.userMessage() ?: "Disconnected"
                Text(msg, color = StatusDownDark)
                Button(onClick = onReconnect) { Text("Reconnect") }
            }
        }
        return
    }

    if (session == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val density = LocalContext.current.resources.displayMetrics.density
    // Live font size in px. Seeded from the persisted sp and re-seeded by the update block below when
    // the persisted value changes (Settings screen, or the value loading after first composition).
    val textSizePx = remember(tab.tabId) { mutableStateOf(fontSizeSp * density) }

    var boundView by remember(tab.tabId) { mutableStateOf<TerminalView?>(null) }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            TerminalView(ctx, null).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(android.graphics.Color.BLACK)
                setTextSize(textSizePx.value.toInt())
                // A real programmer's monospace instead of the system default (the "ugly" font).
                runCatching { setTypeface(TerminalFont.get(ctx)) }
                // Tapping the terminal must pop the soft keyboard — requestFocus() alone doesn't.
                val showKeyboard = {
                    requestFocus()
                    imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                    Unit
                }
                setTerminalViewClient(
                    SshTerminalViewClient(
                        modifiers = modifiers,
                        minTextSizePx = AppSettings.MIN_TERMINAL_FONT_SP * density,
                        maxTextSizePx = AppSettings.MAX_TERMINAL_FONT_SP * density,
                        currentTextSizePx = { textSizePx.value },
                        // Pinch / volume-key resize: update the live view AND persist (in sp) so it's
                        // remembered and seeds the next session.
                        onTextSizeChanged = { newPx ->
                            textSizePx.value = newPx
                            setTextSize(newPx.toInt())
                            onFontSizeChanged((newPx / density).roundToInt())
                        },
                        onTap = { showKeyboard() },
                    ),
                )
                // BIND: point the (above-the-pager) session's client at this view + attach. attachSession
                // rebinds to the EXISTING emulator, restoring the full screen + scrollback.
                tab.client.view = this
                attachSession(session)
                isFocusableInTouchMode = true
                requestFocus()
                boundView = this
            }
        },
        update = { view ->
            // Apply a persisted font change (Settings screen, or the value arriving after the factory
            // already ran) to the already-bound view. Skip when it matches a just-applied local resize.
            val targetPx = fontSizeSp * density
            if (kotlin.math.abs(targetPx - textSizePx.value) >= 0.5f) {
                textSizePx.value = targetPx
                view.setTextSize(targetPx.toInt())
            }
        },
    )
    androidx.compose.runtime.DisposableEffect(tab.tabId) {
        onDispose {
            // UNBIND only — the session keeps running in the store (off-screen tabs stay alive).
            if (tab.client.view === boundView) tab.client.view = null
        }
    }

    // Take focus the moment the view is created (i.e. once the session connects), so hardware Volume
    // Up/Down (font resize) work immediately without the user first tapping. requestFocus() at factory
    // time is too early — the view isn't attached/laid out yet — so wait for boundView, then post it.
    LaunchedEffect(boundView) {
        val v = boundView ?: return@LaunchedEffect
        kotlinx.coroutines.delay(50)
        v.post { v.requestFocus() }
    }
}

@Composable
private fun ConnectionPickerDialog(
    connections: List<Connection>,
    onPick: (Connection) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New terminal") },
        text = {
            Column {
                if (connections.isEmpty()) {
                    Text("No saved connections — add one first.")
                } else {
                    connections.forEach { c ->
                        Text(
                            "${c.displayName}  (${c.endpoint})",
                            modifier = Modifier.fillMaxWidth().clickable { onPick(c) }.padding(vertical = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PasswordDialog(title: String, onConfirm: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    var save by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = pw,
                    onValueChange = { pw = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Row(
                    modifier = Modifier.padding(top = 6.dp).clickable { save = !save },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = save, onCheckedChange = { save = it })
                    Text("Save password (encrypted; unlock to reuse)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pw, save) }) { Text("Connect") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Give IME focus to [view] and pop the soft keyboard — used after extra-key taps steal focus. */
private fun focusTerminal(view: TerminalView?, context: Context) {
    if (view == null) return
    view.requestFocus()
    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
        ?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
