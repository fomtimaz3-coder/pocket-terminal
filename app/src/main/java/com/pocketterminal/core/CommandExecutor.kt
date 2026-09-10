package com.pocketterminal.core

interface CommandExecutor {
    fun start()
    fun write(text: String)
    fun interrupt()
    fun clear()
    fun resize(newRows: Int, newColumns: Int)
    fun stop()
}