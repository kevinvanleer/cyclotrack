package com.kvl.cyclotrack.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

fun getFileName(contentResolver: ContentResolver, uri: Uri): String? {
    fun getUriFilePart(): String? {
        val result = uri.path
        val cut = result!!.lastIndexOf('/')
        return if (cut != -1) {
            result.substring(cut + 1)
        } else null
    }

    return if (uri.scheme == "content") {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else null
        }
    } else {
        getUriFilePart()
    }
}

