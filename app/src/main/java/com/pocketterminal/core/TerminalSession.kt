package com.pocketterminal.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

data class SessionState(
    val snapshot: TerminalSnapshot = TerminalSnapshot(emptyList(), 0, 0),
    val running: Boolean = false,
    val backend: String = "starting",
    val exitCode: Int? = null
)

class TerminalSession(
    val id: Int,
    private val home: File,
    private val rows: Int = 32,
    private val columns: Int = 120
) : CommandExecutor {
    val homeDirectory: File get() = home
    private val emulator = TerminalEmulator(columns = columns)
    private val processManager = ProcessManager()
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val running = AtomicBoolean(false)
    private var nativeFd = -1
    private var nativePid = -1
    private var input: OutputStream? = null

    override
    fun start() {
        if (!running.compareAndSet(false, true)) return
        home.mkdirs()
        val shell = if (File("/system/bin/sh").canExecute()) "/system/bin/sh" else "/bin/sh"
        val environment = buildEnvironment()

        if (NativePtyBridge.isAvailable()) {
            val pid = IntArray(1)
            nativeFd = NativePtyBridge.createSubprocess(
                shell, home.absolutePath, environment, pid, rows, columns
            )
            nativePid = pid[0]
            if (nativeFd >= 0) {
                _state.value = SessionState(emulator.snapshot(), true, "native PTY")
                startNativeReader()
                return
            }
        }

        startProcessFallback(shell, environment)
    }

    override fun write(text: String) {
        if (!running.get()) return
        val bytes = text.toByteArray(Charsets.UTF_8)
        try {
            if (nativeFd >= 0) {
                var offset = 0
                while (offset < bytes.size) {
                    val written = NativePtyBridge.write(nativeFd, bytes, bytes.size - offset)
                    if (written <= 0) break
                    offset += written
                }
            } else {
                input?.write(bytes)
                input?.flush()
            }
        } catch (_: Exception) {
            // The reader publishes process termination; a closed pipe is expected during stop().
        }
    }

    fun interrupt() {
        write("\u0003")
        if (nativePid > 0) {
            NativePtyBridge.sendSignal(nativePid, 2)
        } else {
            // A plain ProcessBuilder pipe has no tty line discipline. Destroying
            // the fallback shell is safer than pretending that ^C became SIGINT.
            processManager.destroy()
        }
    }

    override fun clear() {
        write("\u000C")
        emulator.clear()
        publish()
    }

    override fun resize(newRows: Int, newColumns: Int) {
        if (nativeFd >= 0) NativePtyBridge.setWindowSize(nativeFd, newRows, newColumns)
    }

    fun complete(text: String): String? {
        val beforeCursor = text.substringBeforeLast(' ')
        val token = text.substringAfterLast(' ')
        val candidates = if (beforeCursor.isBlank()) {
            COMMANDS
        } else {
            home.listFiles()?.map { it.name }?.toSet().orEmpty()
        }
        val matches = candidates.filter { it.startsWith(token) }.sorted()
        return matches.singleOrNull()?.let { text.dropLast(token.length) + it }
    }

    override fun stop() {
        if (!running.getAndSet(false)) return
        if (nativeFd >= 0) {
            if (nativePid > 0) NativePtyBridge.sendSignal(nativePid, 15)
            NativePtyBridge.closePty(nativeFd)
            nativeFd = -1
        }
        processManager.destroy()
        input = null
        _state.value = _state.value.copy(running = false)
    }

    private fun startNativeReader() {
        Thread({
            val buffer = ByteArray(16 * 1024)
            while (running.get()) {
                val count = NativePtyBridge.read(nativeFd, buffer, buffer.size)
                if (count <= 0) break
                emulator.processBytes(buffer, count)
                publish()
            }
            if (running.getAndSet(false)) publish(exitCode = null)
        }, "pty-reader-$id").start()
    }

    private fun startProcessFallback(shell: String, environment: Array<String>) {
        try {
            val child = processManager.start(shell, home, environment)
            input = child.outputStream
            _state.value = SessionState(emulator.snapshot(), true, "shell process")
            Thread({
                child.inputStream.use { readLoop(it) }
                val code = runCatching { child.waitFor() }.getOrNull()
                if (running.getAndSet(false)) publish(code)
            }, "shell-reader-$id").start()
        } catch (error: Exception) {
            running.set(false)
            emulator.processBytes(
                "Pocket Terminal: unable to start shell: ${error.message}\n".toByteArray(),
                "Pocket Terminal: unable to start shell: ${error.message}\n".length
            )
            publish(exitCode = 127)
        }
    }

    private fun readLoop(stream: InputStream) {
        val buffer = ByteArray(16 * 1024)
        while (running.get()) {
            val count = stream.read(buffer)
            if (count <= 0) break
            emulator.processBytes(buffer, count)
            publish()
        }
    }

    private fun publish(exitCode: Int? = _state.value.exitCode) {
        _state.value = _state.value.copy(
            snapshot = emulator.snapshot(),
            running = running.get(),
            exitCode = exitCode
        )
    }

    private fun buildEnvironment(): Array<String> {
        val values = linkedMapOf<String, String>()
        System.getenv().forEach { (key, value) -> values[key] = value }
        values["TERM"] = "xterm-256color"
        values["COLORTERM"] = "truecolor"
        values["HOME"] = home.absolutePath
        values["PWD"] = home.absolutePath
        values["SHELL"] = if (File("/system/bin/sh").canExecute()) "/system/bin/sh" else "/bin/sh"
        values["PATH"] = "/system/bin:/system/xbin:/vendor/bin:/usr/bin:/bin"
        values["PS1"] = "\\[\\033[32m\\]\\w\\[\\033[0m\\] $ "
        values["HISTFILE"] = File(home, ".sh_history").absolutePath
        values["LC_ALL"] = "C.UTF-8"
        return values.map { "${it.key}=${it.value}" }.toTypedArray()
    }

    companion object {
        private val COMMANDS = setOf(
            "pwd", "ls", "cd", "mkdir", "touch", "cp", "mv", "rm", "cat", "head",
            "tail", "grep", "find", "echo", "printf", "clear", "env", "export",
            "unset", "which", "whoami", "uname", "date", "ps", "sh", "exit"
        )
    }
}