package com.offgrid.mesh

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

class SelfApkProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "application/vnd.android.package-archive"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val file = sharedApk()
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(columns)
        val row = cursor.newRow()
        columns.forEach { column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> row.add(SHARED_NAME)
                OpenableColumns.SIZE -> row.add(if (file.exists()) file.length() else 0L)
                else -> row.add(null)
            }
        }
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r" && mode != "rt") throw FileNotFoundException("Read only")
        if (uri.lastPathSegment != "offgrid.apk") throw FileNotFoundException("Unknown installer")
        val file = sharedApk()
        if (!file.exists()) throw FileNotFoundException("Installer not prepared")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private fun sharedApk(): File {
        val appContext = context ?: throw FileNotFoundException("Provider unavailable")
        return File(File(appContext.cacheDir, "shared"), SHARED_NAME)
    }

    companion object {
        private const val SHARED_NAME = "OFFGRID-Alpha-v1.apk"
    }
}
