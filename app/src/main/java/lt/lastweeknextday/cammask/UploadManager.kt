package lt.lastweeknextday.cammask

import android.content.Context
import android.net.Uri
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class UploadManager(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val fileAnalyzer = FileAnalyzer()

    fun uploadFile(uri: Uri, additionalFields: Map<String, String> = emptyMap()): String {
        try {
            val fileInfo = fileAnalyzer.analyze(context, uri)
            val tempFile = createTempFile(uri)

            try {
                val multipartBuilder = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        fileInfo.name,
                        tempFile.asRequestBody(fileInfo.mimeType.toMediaType())
                    )

                additionalFields.forEach { (key, value) ->
                    multipartBuilder.addFormDataPart(key, value)
                }

                val request = Request.Builder()
                    .url("https://uploadfile-${Constants.BASE_URL}")
                    .header("Content-Type", multipartBuilder.build().contentType().toString())
                    .header("Content-Length", multipartBuilder.build().contentLength().toString())
                    .post(multipartBuilder.build())
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw UploadException("Upload failed with code: ${response.code}")
                    }

                    val responseBody = response.body?.string()
                        ?: throw UploadException("Empty response body")

                    return try {
                        val jsonResponse = JSONObject(responseBody)
                        val files = jsonResponse.getJSONArray("files")
                        if (files.length() == 0) {
                            throw UploadException("No file URL in response")
                        }

                        val fileObject = files.getJSONObject(0)
                        fileObject.getString("url")
                    } catch (e: Exception) {
                        Log.e("UploadManager", "Error parsing response: $responseBody", e)
                        throw UploadException("Failed to parse upload response", e)
                    }
                }
            } finally {
                tempFile.delete()
            }
        } catch (e: IOException) {
            Log.e("UploadManager", "Upload failed", e)
            throw UploadException("Upload failed due to network error", e)
        } catch (e: Exception) {
            Log.e("UploadManager", "Unexpected error during upload", e)
            throw UploadException("Unexpected error during upload", e)
        }
    }

    private fun createTempFile(uri: Uri): File {
        val tempFile = File.createTempFile("upload_", null, context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Failed to open input stream for URI: $uri")
        return tempFile
    }

    class UploadException : Exception {
        constructor(message: String) : super(message)
        constructor(message: String, cause: Throwable) : super(message, cause)
    }
}