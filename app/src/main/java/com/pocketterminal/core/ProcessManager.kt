package com.pocketterminal.core

import java.io.File

/**
 * Owns the non-PTY fallback process. The preferred native path remains in
 * NativePtyBridge; this class exists so lifecycle and cleanup are not mixed
 * into the UI or command input code.
 */
class ProcessManager {
    var process: Process? = null
        private set

    fun start(shell: String, home: File, environment: Array<String>): Process {
        val child = ProcessBuilder(shell, "-i")
            .directory(home)
            .redirectErrorStream(true)
            .apply {
                environment().clear()
                environment().putAll(environment.associate {
                    val separator = it.indexOf('=')
                    it.substring(0, separator) to it.substring(separator + 1)
                })
            }
            .start()
        process = child
        return child
    }

    fun destroy() {
        process?.destroy()
        process = null
    }
}