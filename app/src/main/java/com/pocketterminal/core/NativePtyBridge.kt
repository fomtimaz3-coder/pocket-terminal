package com.pocketterminal.core

/**
 * Small JNI surface around forkpty(3). The child is a real Android /system/bin/sh
 * process. No command parsing or command output is implemented in Kotlin.
 */
object NativePtyBridge {
    private val loaded: Boolean = runCatching {
        System.loadLibrary("pocket_terminal")
        true
    }.getOrDefault(false)

    fun isAvailable(): Boolean = loaded

    external fun createSubprocess(
        shell: String,
        cwd: String,
        environment: Array<String>,
        processId: IntArray,
        rows: Int,
        cols: Int
    ): Int

    external fun read(fd: Int, buffer: ByteArray, length: Int): Int
    external fun write(fd: Int, buffer: ByteArray, length: Int): Int
    external fun setWindowSize(fd: Int, rows: Int, cols: Int)
    external fun sendSignal(pid: Int, signal: Int)
    external fun closePty(fd: Int)
}