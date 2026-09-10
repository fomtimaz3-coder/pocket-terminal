package com.pocketterminal.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.SelectionContainer
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketterminal.core.TerminalCell
import com.pocketterminal.core.TerminalEmulator
import com.pocketterminal.core.TerminalSessionManager
import com.pocketterminal.core.StorageAccess
import com.pocketterminal.core.StorageEntry
import androidx.compose.ui.text.withStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(manager: TerminalSessionManager) {
    val tabs by manager.tabs.collectAsState()
    val activeIndex by manager.activeIndex.collectAsState()
    val darkTheme by manager.darkTheme.collectAsState()
    val fontSize by manager.fontSize.collectAsState()
    val grantedTree by manager.grantedTree.collectAsState()
    val activeTab = tabs.getOrNull(activeIndex)
    val session = activeTab?.session
    val sessionState by (session?.state ?: kotlinx.coroutines.flow.flowOf(
        com.pocketterminal.core.SessionState()
    )).collectAsState(initial = com.pocketterminal.core.SessionState())
    var input by remember(session) { mutableStateOf("") }
    var history by remember(session) { mutableStateOf(emptyList<String>()) }
    var historyIndex by remember(session) { mutableStateOf(-1) }
    var ctrlActive by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showStorageInfo by remember { mutableStateOf(false) }
    val terminalScroll = rememberLazyListState()
    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) manager.saveGrantedTree(uri)
    }

    LaunchedEffect(sessionState.snapshot.lines.size, session) {
        if (sessionState.snapshot.lines.isNotEmpty()) {
            terminalScroll.animateScrollToItem(sessionState.snapshot.lines.lastIndex)
        }
    }

    val submit: () -> Unit = {
        val command = input
        if (command.isNotBlank()) {
            session?.write("$command\n")
            history = (history + command).takeLast(100)
            historyIndex = -1
            input = ""
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (darkTheme) Color(0xFF0B0D10) else Color(0xFFF4F6F2)
    ) {
        Column {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("POCKET / TERMINAL", fontSize = 14.sp, letterSpacing = 1.sp)
                            Text(
                                if (sessionState.running) "${sessionState.backend} • sandbox"
                                else "shell stopped",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { treeLauncher.launch(null) }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Choose a folder")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (darkTheme) Color(0xFF0B0D10) else Color(0xFFF4F6F2)
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    AssistChip(
                        onClick = { manager.select(index) },
                        label = { Text(tab.title, fontSize = 12.sp) },
                        border = if (index == activeIndex) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                    )
                }
                AssistChip(
                    onClick = { manager.addSession() },
                    label = { Text("new", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))

            SelectionContainer {
                LazyColumn(
                    state = terminalScroll,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(TerminalColor(darkTheme))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    itemsIndexed(sessionState.snapshot.lines) { _, line ->
                        val annotated = remember(line, fontSize) { lineText(line.cells) }
                        Text(
                            text = annotated,
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize + 5).sp
                        )
                    }
                    if (sessionState.snapshot.lines.isEmpty()) {
                        item {
                            Text(
                                "Starting a real shell in the app sandbox…",
                                color = Color(0xFF7F8A95),
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSize.sp
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (darkTheme) Color(0xFF12161B) else Color(0xFFE8ECE7))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$ ",
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize.sp
                )
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSize.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    decorationBox = { inner ->
                        if (input.isEmpty()) {
                            Text(
                                "type a command…",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .35f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSize.sp
                            )
                        }
                        inner()
                    }
                )
                Button(
                    onClick = submit,
                    enabled = sessionState.running,
                    contentPadding = ButtonDefaults.ContentPadding,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("run", color = MaterialTheme.colorScheme.onPrimary)
                }
            }

            TerminalKeyboard(
                ctrlActive = ctrlActive,
                onCtrlToggle = { ctrlActive = !ctrlActive },
                onSpecial = { key ->
                    when (key) {
                        "ESC" -> session?.write("\u001B")
                        "CTRL+C" -> session?.interrupt()
                        "CTRL+L" -> session?.clear()
                        "TAB" -> {
                            val completed = session?.complete(input)
                            if (completed != null) input = completed else session?.write("\t")
                        }
                        "↑" -> {
                            if (history.isNotEmpty()) {
                                historyIndex = (if (historyIndex < 0) history.lastIndex else historyIndex - 1)
                                    .coerceAtLeast(0)
                                input = history[historyIndex]
                            }
                        }
                        "↓" -> {
                            if (historyIndex >= 0) {
                                historyIndex++
                                if (historyIndex in history.indices) input = history[historyIndex]
                                else {
                                    historyIndex = -1
                                    input = ""
                                }
                            }
                        }
                        "←", "→" -> session?.write(if (key == "←") "\u001B[D" else "\u001B[C")
                        "ALT" -> session?.write("\u001B")
                        else -> {
                            if (ctrlActive && key.length == 1 && key[0].uppercaseChar() in 'A'..'Z') {
                                session?.write((key[0].uppercaseChar().code - 'A'.code + 1).toChar().toString())
                                ctrlActive = false
                            } else input += key
                        }
                    }
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (sessionState.running) MaterialTheme.colorScheme.primary else Color(0xFFE06C75))
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    if (sessionState.running) "Commands execute locally with app permissions"
                    else "Shell is not running",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                    fontSize = 11.sp
                )
                Spacer(Modifier.weight(1f))
                grantedTree?.let {
                    TextButton(onClick = { showStorageInfo = true }) {
                        Text("folder linked", fontSize = 11.sp)
                    }
                }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            SettingsContent(
                darkTheme = darkTheme,
                fontSize = fontSize,
                onDarkThemeChange = manager::setDarkTheme,
                onFontSizeChange = { manager.adjustFontSize(it) },
                onClose = { showSettings = false }
            )
        }
    }

    if (showStorageInfo && grantedTree != null) {
        ModalBottomSheet(onDismissRequest = { showStorageInfo = false }) {
            StorageBrowser(
                uri = grantedTree!!,
                destination = session?.homeDirectory,
                onClose = { showStorageInfo = false }
            )
        }
    }
}

@Composable
private fun StorageBrowser(
    uri: android.net.Uri,
    destination: java.io.File?,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var entries by remember(uri) { mutableStateOf(emptyList<StorageEntry>()) }
    var status by remember(uri) { mutableStateOf<String?>(null) }
    LaunchedEffect(uri) {
        entries = StorageAccess.list(context, uri)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .padding(bottom = 28.dp)
    ) {
        Text("Linked folder", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Import files into this session's sandbox home. The shell does not access content:// URIs directly.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
        )
        if (entries.isEmpty()) {
            Text("No readable entries found.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
        } else {
            entries.take(60).forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (entry.isDirectory) "DIR  ${entry.name}" else entry.name,
                        modifier = Modifier.weight(1f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                    if (!entry.isDirectory && destination != null) {
                        TextButton(onClick = {
                            status = StorageAccess.copyFileToSandbox(
                                context, entry.uri, destination
                            ).fold(
                                onSuccess = { "Imported ${it.name}" },
                                onFailure = { "Import failed: ${it.message}" }
                            )
                        }) {
                            Text("Import")
                        }
                    }
                }
            }
        }
        status?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

@Composable
private fun SettingsContent(
    darkTheme: Boolean,
    fontSize: Float,
    onDarkThemeChange: (Boolean) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .padding(bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Terminal settings", style = MaterialTheme.typography.headlineSmall)
        Text(
            "The shell runs as a real child process. These preferences only change the terminal UI.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f),
            fontSize = 13.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dark interface", modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(checked = darkTheme, onCheckedChange = onDarkThemeChange)
        }
        Column {
            Text("Font size  ${fontSize.toInt()}sp")
            Slider(
                value = fontSize,
                onValueChange = { onFontSizeChange(it - fontSize) },
                valueRange = 10f..22f,
                steps = 5
            )
        }
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

private fun TerminalColor(dark: Boolean): Color =
    if (dark) Color(TerminalEmulator.BLACK) else Color(0xFF111612)

private fun lineText(cells: List<TerminalCell>): AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        var index = 0
        while (index < cells.size) {
            val cell = cells[index]
            val style = SpanStyle(
                color = Color(cell.foreground),
                background = Color(cell.background),
                fontWeight = if (cell.bold) androidx.compose.ui.text.font.FontWeight.Bold else null,
                textDecoration = if (cell.underline) androidx.compose.ui.text.style.TextDecoration.Underline else null
            )
            val start = index
            while (index < cells.size &&
                cells[index].foreground == cell.foreground &&
                cells[index].background == cell.background &&
                cells[index].bold == cell.bold &&
                cells[index].underline == cell.underline
            ) index++
            withStyle(style) { append(cells.subList(start, index).joinToString("") { it.value.toString() }) }
        }
    }
}