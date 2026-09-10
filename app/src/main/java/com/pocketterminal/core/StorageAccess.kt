package com.pocketterminal.core

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

data class StorageEntry(val name: String, val isDirectory: Boolean, val uri: Uri)

object StorageAccess {
    fun list(context: Context, treeUri: Uri): List<StorageEntry> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        return root.listFiles().mapNotNull { item ->
            item.name?.let { StorageEntry(it, item.isDirectory, item.uri) }
        }.sortedWith(compareByDescending<StorageEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    fun copyFileToSandbox(context: Context, uri: Uri, destination: File): Result<File> =
        runCatching {
            val source = DocumentFile.fromSingleUri(context, uri)
                ?: error("The selected file is no longer available")
            val name = source.name ?: error("The selected file has no name")
            destination.mkdirs()
            val target = File(destination, name)
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to open the selected file" }
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target
        }
}