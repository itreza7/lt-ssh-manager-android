package com.larateam.sshmanager.ui.sftp

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.larateam.sshmanager.data.crypto.BiometricGate
import com.larateam.sshmanager.sftp.SftpEntry
import com.larateam.sshmanager.sftp.TransferProgress
import com.larateam.sshmanager.sftp.permissionsString
import com.larateam.sshmanager.ui.components.ConsoleCard
import com.larateam.sshmanager.ui.components.Eyebrow
import com.larateam.sshmanager.ui.components.Tag
import com.larateam.sshmanager.ui.theme.StatusKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SftpScreen(onBack: () -> Unit, viewModel: SftpViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val gate = remember { BiometricGate() }

    var menuOpen by remember { mutableStateOf(false) }
    var showMkdir by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SftpEntry?>(null) }
    var chmodTarget by remember { mutableStateOf<SftpEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<SftpEntry?>(null) }
    var pendingDownload by remember { mutableStateOf<SftpEntry?>(null) }

    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            val (name, size) = queryNameSize(context, uri)
            val input = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull() ?: return@forEach
            viewModel.upload(name, size, input)
        }
    }
    val downloadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val entry = pendingDownload
        pendingDownload = null
        if (uri != null && entry != null) {
            val out = runCatching { context.contentResolver.openOutputStream(uri) }.getOrNull()
            if (out != null) viewModel.download(entry, out)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(ui.title.ifBlank { "SFTP" }) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = viewModel::refresh) { Icon(Icons.Filled.Refresh, "Refresh") }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Menu") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Upload file(s)") }, onClick = { menuOpen = false; uploadLauncher.launch(arrayOf("*/*")) })
                        DropdownMenuItem(text = { Text("New folder") }, onClick = { menuOpen = false; showMkdir = true })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Sort: name") }, onClick = { menuOpen = false; viewModel.setSort(SortMode.NAME) })
                        DropdownMenuItem(text = { Text("Sort: size") }, onClick = { menuOpen = false; viewModel.setSort(SortMode.SIZE) })
                        DropdownMenuItem(text = { Text("Sort: date") }, onClick = { menuOpen = false; viewModel.setSort(SortMode.DATE) })
                        DropdownMenuItem(
                            text = { Text(if (ui.showHidden) "Hide hidden files" else "Show hidden files") },
                            onClick = { menuOpen = false; viewModel.toggleHidden() },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = viewModel::up) { Text("⬆ Up") }
                Text(ui.path, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
            }
            HorizontalDivider()

            ui.transfers.forEach { t -> TransferRow(t, onCancel = { viewModel.cancelTransfer(t.id) }, onDismiss = { viewModel.dismissTransfer(t.id) }) }
            if (ui.transfers.isNotEmpty()) HorizontalDivider()

            ui.error?.let { err ->
                ConsoleCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    spine = StatusKind.DOWN,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(err, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = viewModel::clearError) { Text("Dismiss") }
                    }
                }
            }

            when {
                ui.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                ui.entries.isEmpty() && ui.error == null -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Eyebrow("empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Nothing here",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "Use the ⋮ menu to upload files or create a folder.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(ui.entries, key = { it.path }) { entry ->
                        EntryRow(
                            entry = entry,
                            onClick = {
                                if (entry.isDirectory || entry.isSymlink) viewModel.open(entry)
                                else { pendingDownload = entry; downloadLauncher.launch(entry.name) }
                            },
                            onDownload = { pendingDownload = entry; downloadLauncher.launch(entry.name) },
                            onRename = { renameTarget = entry },
                            onChmod = { chmodTarget = entry },
                            onDelete = { deleteTarget = entry },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showMkdir) {
        TextDialog("New folder", "Folder name", onConfirm = { showMkdir = false; viewModel.mkdir(it) }, onDismiss = { showMkdir = false })
    }
    renameTarget?.let { e ->
        TextDialog("Rename", "New name", initial = e.name, onConfirm = { renameTarget = null; viewModel.rename(e, it) }, onDismiss = { renameTarget = null })
    }
    chmodTarget?.let { e ->
        ChmodDialog(e, onConfirm = { mode -> chmodTarget = null; viewModel.chmod(e, mode) }, onDismiss = { chmodTarget = null })
    }
    deleteTarget?.let { e ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete?") },
            text = { Text("Delete \"${e.name}\"${if (e.isDirectory) " and everything inside it" else ""}? This can't be undone." + if (e.isSymlink) "\n\n(Symlink: only the link is removed, not its target.)" else "") },
            confirmButton = { TextButton(onClick = { deleteTarget = null; viewModel.delete(e) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }

    if (ui.needsPassword) {
        PasswordDialog(onConfirm = { pw, save -> viewModel.submitPassword(pw.toCharArray(), save) }, onDismiss = { viewModel.cancelPrompts(); onBack() })
    }
    if (ui.needsBiometric) {
        val act = activity
        if (act == null) { viewModel.cancelPrompts(); onBack() } else {
            LaunchedEffect(Unit) {
                when (gate.authenticate(act, "Unlock to connect", "Authenticate to use your saved credential")) {
                    BiometricGate.Outcome.Success -> viewModel.submitStoredKeyAfterAuth()
                    else -> { viewModel.cancelPrompts(); onBack() }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: SftpEntry,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onChmod: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            val (label, color) = when {
                entry.isDirectory -> "dir" to MaterialTheme.colorScheme.primary
                entry.isSymlink -> "lnk" to MaterialTheme.colorScheme.tertiary
                else -> "file" to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Tag(label, color = color)
        },
        headlineContent = { Text(entry.name + if (entry.isSymlink) "  →" else "") },
        supportingContent = {
            Text(
                "${if (entry.isDirectory) "dir" else humanBytes(entry.size)} · ${permissionsString(entry.mode)} · ${formatMtime(entry.mtimeEpochSec)}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Actions") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!entry.isDirectory) DropdownMenuItem(text = { Text("Download") }, onClick = { menu = false; onDownload() })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("chmod") }, onClick = { menu = false; onChmod() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                }
            }
        },
    )
}

@Composable
private fun TransferRow(t: TransferProgress, onCancel: () -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${if (t.isUpload) "↑" else "↓"} ${t.name}", style = MaterialTheme.typography.bodyMedium)
            val status = when {
                t.error != null -> "error: ${t.error}"
                t.cancelled -> "cancelled"
                t.done -> "done"
                t.total > 0 -> "${t.percent}%  ${humanBytes(t.bytes)}/${humanBytes(t.total)}"
                else -> humanBytes(t.bytes)
            }
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
        if (t.active) {
            if (t.total > 0) LinearProgressIndicator(progress = { t.percent / 100f }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            TextButton(onClick = onCancel) { Text("Cancel") }
        } else {
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
private fun TextDialog(title: String, label: String, initial: String = "", onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (value.isNotBlank()) onConfirm(value.trim()) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChmodDialog(entry: SftpEntry, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var octal by remember { mutableStateOf(String.format("%o", entry.mode and 0x1FF)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("chmod ${entry.name}") },
        text = {
            Column {
                Text("Current: ${permissionsString(entry.mode)}  (${String.format("0%o", entry.mode and 0x1FF)})", fontFamily = FontFamily.Monospace)
                OutlinedTextField(value = octal, onValueChange = { octal = it.filter { c -> c in '0'..'7' }.take(4) }, label = { Text("Octal mode (e.g. 755)") }, singleLine = true)
                val parsed = octal.toIntOrNull(8)
                if (parsed != null) Text("New: ${permissionsString(parsed)}", fontFamily = FontFamily.Monospace)
            }
        },
        confirmButton = {
            val parsed = octal.toIntOrNull(8)
            TextButton(enabled = parsed != null, onClick = { parsed?.let(onConfirm) }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PasswordDialog(onConfirm: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    var save by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Password") },
        text = {
            Column {
                OutlinedTextField(value = pw, onValueChange = { pw = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
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

private fun queryNameSize(context: Context, uri: Uri): Pair<String, Long> {
    var name = "upload.bin"
    var size = -1L
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val si = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst()) {
                if (ni >= 0) c.getString(ni)?.let { name = it }
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
    }
    return name to size
}

private fun humanBytes(bytes: Long): String {
    if (bytes < 0) return "?"
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = bytes.toDouble(); var i = -1
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return "%.1f %s".format(v, units[i])
}

private fun formatMtime(epochSec: Long): String =
    if (epochSec <= 0) "—" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(epochSec * 1000))

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
