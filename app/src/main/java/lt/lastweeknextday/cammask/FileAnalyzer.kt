package lt.lastweeknextday.cammask

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File

class FileAnalyzer {
    data class FileInfo(
        val name: String,
        val extension: String,
        val mimeType: String,
        val size: Long
    )

    fun analyze(context: Context, uri: Uri): FileInfo {
        if (uri.scheme == "content") {
            return analyzeContentUri(context, uri)
        }

        if (uri.scheme == "file" || uri.scheme == null) {
            return analyzeFileUri(uri)
        }

        throw IllegalArgumentException("Unsupported URI: ${uri.scheme}")
    }

    private fun analyzeContentUri(context: Context, uri: Uri): FileInfo {
        var fileName = "N/A"
        var fileSize = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }

                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1) {
                    fileSize = cursor.getLong(sizeIndex)
                }
            }
        }

        val extension = fileName.substringAfterLast('.', "")
        val mimeType = getMimeType(fileName, uri, context)

        return FileInfo(
            name = fileName,
            extension = extension,
            mimeType = mimeType,
            size = fileSize
        )
    }

    private fun analyzeFileUri(uri: Uri): FileInfo {
        val file = File(uri.path ?: throw IllegalArgumentException("Invalid file path"))

        val fileName = file.name
        val extension = fileName.substringAfterLast('.', "")
        val mimeType = getMimeType(fileName, uri, null)

        return FileInfo(
            name = fileName,
            extension = extension,
            mimeType = mimeType,
            size = file.length()
        )
    }

    private fun getMimeType(fileName: String, uri: Uri, context: Context?): String {
        val mimeType = when (val extension = fileName.substringAfterLast('.', "").lowercase()) {
            "glb" -> "model/gltf-binary"
            "gltf" -> "model/gltf+json"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            else -> {
                MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(extension)
                    ?: context?.contentResolver?.getType(uri)
                    ?: "application/octet-stream"
            }
        }
        return mimeType
    }
}