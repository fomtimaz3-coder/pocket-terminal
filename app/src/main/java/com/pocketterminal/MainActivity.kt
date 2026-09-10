package com.pocketterminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.pocketterminal.core.TerminalSessionManager
import com.pocketterminal.ui.PocketTerminalTheme
import com.pocketterminal.ui.TerminalScreen

class MainActivity : ComponentActivity() {
    private val terminalManager: TerminalSessionManager by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark by terminalManager.darkTheme.collectAsState()
            PocketTerminalTheme(dark = dark) {
                TerminalScreen(terminalManager)
            }
        }
    }
}