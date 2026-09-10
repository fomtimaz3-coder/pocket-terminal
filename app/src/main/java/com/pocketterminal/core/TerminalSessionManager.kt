package com.pocketterminal.core

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class TerminalTab(val session: TerminalSession, val title: String)

class TerminalSessionManager(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val settings = PersistentSettings(application)
    private val _tabs = MutableStateFlow<List<TerminalTab>>(emptyList())
    val tabs: StateFlow<List<TerminalTab>> = _tabs.asStateFlow()
    private val _activeIndex = MutableStateFlow(0)
    val activeIndex: StateFlow<Int> = _activeIndex.asStateFlow()
    private val _grantedTree = MutableStateFlow<Uri?>(null)
    val grantedTree: StateFlow<Uri?> = _grantedTree.asStateFlow()
    private val _darkTheme = MutableStateFlow(
        settings.darkTheme
    )
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()
    private val _fontSize = MutableStateFlow(
        settings.fontSize
    )
    val fontSize: StateFlow<Float> = _fontSize.asStateFlow()

    init {
        addSession()
        val saved = settings.grantedTree
        _grantedTree.value = saved?.let(Uri::parse)
    }

    fun current(): TerminalSession? = _tabs.value.getOrNull(_activeIndex.value)?.session

    fun addSession() {
        val index = _tabs.value.size + 1
        val session = TerminalSession(index, File(app.filesDir, "home/session-$index"))
        _tabs.value = _tabs.value + TerminalTab(session, "session $index")
        _activeIndex.value = _tabs.value.lastIndex
        session.start()
    }

    fun select(index: Int) {
        if (index in _tabs.value.indices) _activeIndex.value = index
    }

    fun saveGrantedTree(uri: Uri) {
        runCatching {
            app.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        settings.grantedTree = uri.toString()
        _grantedTree.value = uri
    }

    fun setDarkTheme(value: Boolean) {
        settings.darkTheme = value
        _darkTheme.value = value
    }

    fun adjustFontSize(delta: Float) {
        val next = (_fontSize.value + delta).coerceIn(10f, 22f)
        settings.fontSize = next
        _fontSize.value = next
    }

    override fun onCleared() {
        _tabs.value.forEach { it.session.stop() }
        super.onCleared()
    }
}