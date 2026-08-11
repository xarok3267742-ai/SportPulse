package ru.sportpulse.info

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

class PreflightCalendarProvider : ContentProvider() {
    override fun attachInfo(context: Context, info: ProviderInfo) {
        require(!info.exported) {
            "PreflightCalendarProvider must not be exported"
        }
        require(info.grantUriPermissions) {
            "PreflightCalendarProvider requires temporary URI grants"
        }
        super.attachInfo(context, info)
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String {
        fileForUri(uri)
        return MIME_TYPE
    }

    override fun openFile(
        uri: Uri,
        mode: String
    ): ParcelFileDescriptor {
        if (!mode.startsWith("r") ||
            mode.contains('w') ||
            mode.contains('+')
        ) {
            throw FileNotFoundException("Provider is read-only")
        }
        return ParcelFileDescriptor.open(
            fileForUri(uri),
            ParcelFileDescriptor.MODE_READ_ONLY
        )
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val file = fileForUri(uri)
        val columns = projection ?: arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE
        )
        return MatrixCursor(columns, 1).apply {
            val row = newRow()
            columns.forEach { column ->
                row.add(
                    when (column) {
                        OpenableColumns.DISPLAY_NAME -> file.name
                        OpenableColumns.SIZE -> file.length()
                        else -> null
                    }
                )
            }
        }
    }

    override fun insert(
        uri: Uri,
        values: ContentValues?
    ): Uri {
        throw UnsupportedOperationException("Provider is read-only")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException("Provider is read-only")
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException("Provider is read-only")
    }

    private fun fileForUri(uri: Uri): File {
        val providerContext = context
            ?: throw FileNotFoundException("Provider unavailable")
        if (uri.scheme != "content" ||
            uri.authority != authority(providerContext) ||
            uri.pathSegments.size != 1
        ) {
            throw FileNotFoundException("Invalid calendar URI")
        }
        val fileName = uri.pathSegments.single()
        if (!FILE_NAME.matches(fileName)) {
            throw FileNotFoundException("Invalid calendar file name")
        }
        val directory = File(
            providerContext.cacheDir,
            SHARE_DIRECTORY
        ).canonicalFile
        val candidate = File(directory, fileName).canonicalFile
        if (candidate.parentFile != directory || !candidate.isFile) {
            throw FileNotFoundException("Preflight calendar not found")
        }
        return candidate
    }

    companion object {
        internal const val SHARE_DIRECTORY = "preflight_calendars"
        private const val MIME_TYPE = "text/calendar"
        private val FILE_NAME = Regex(
            "sport-pulse-preflight-[a-f0-9]{12}\\.ics"
        )

        fun uriFor(context: Context, file: File): Uri {
            val directory = File(
                context.cacheDir,
                SHARE_DIRECTORY
            ).canonicalFile
            val candidate = file.canonicalFile
            require(
                candidate.parentFile == directory &&
                    candidate.isFile &&
                    FILE_NAME.matches(candidate.name)
            ) {
                "Only generated preflight calendars can be shared"
            }
            return Uri.Builder()
                .scheme("content")
                .authority(authority(context))
                .appendPath(candidate.name)
                .build()
        }

        private fun authority(context: Context): String {
            return "${context.packageName}.preflight-calendars"
        }
    }
}
