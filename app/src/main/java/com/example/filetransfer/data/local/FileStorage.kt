package com.example.filetransfer.data.local

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.util.Log
import android.net.Uri
import java.io.IOException
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream

object FileStorage {

    fun saveToDownloads(
        context: Context,
        fileName: String,
        inputStream: InputStream
    ): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/FileTransfer")
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues
            ) ?: throw IOException("Cannot create save file")

            context.contentResolver.openOutputStream(uri)?.use { output ->
                inputStream.copyTo(output)
            } ?: throw IOException("Cannot open output stream")

            return fileName
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val folder = File(downloadsDir, "FileTransfer")
            if (!folder.exists()) folder.mkdirs()
            val file = File(folder, fileName)
            file.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            return fileName
        }
    }

    fun getFileNameFromUri(uri: Uri, contentResolver: ContentResolver): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex)
                    }
                }
            }
        }
        return name ?: uri.path?.substringAfterLast('/')
    }

    fun getFileSize(uri: Uri, contentResolver: ContentResolver): Long {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    cursor.getLong(sizeIndex)
                } else 0L
            } ?: 0L
        } catch (e: Exception) {
            Log.e("FileStorage", "getFileSize failed", e)
            0L
        }
    }
}
