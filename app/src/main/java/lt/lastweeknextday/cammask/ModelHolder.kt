package lt.lastweeknextday.cammask

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.google.ar.sceneform.rendering.ModelRenderable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CompletableFuture

class ModelHolder(private val context: Context) {
    private val loaders = mutableSetOf<CompletableFuture<*>>()
    private var currentModel: ModelRenderable? = null
    private val client = OkHttpClient()
    private var tempFile: File? = null

    fun getModel(): ModelRenderable? = currentModel

    fun loadModelLocal(modelPath: String, callback: (Boolean) -> Unit) {
        ModelRenderable.builder()
            .setSource(context, Uri.parse(modelPath))
            .setIsFilamentGltf(true)
            .build()
            .thenAccept { model ->
                currentModel = model
                callback(true)
            }
            .exceptionally { throwable ->
                Toast.makeText(context, "Unable to load model: ${throwable.message}",
                    Toast.LENGTH_LONG).show()
                callback(false)
                null
            }.also { loaders.add(it) }
    }

    suspend fun loadModelRemote(modelUrl: String, callback: (Boolean) -> Unit) = withContext(Dispatchers.IO) {
        try {
            Log.d("ModelHolder", "Downloading model from: $modelUrl")

            tempFile?.delete()

            tempFile = File.createTempFile("model", ".glb", context.cacheDir)

            val request = Request.Builder()
                .url(modelUrl)
                .build()

            CoroutineScope(Dispatchers.IO).async {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("Failed to download model: ${response.code}")
                    }

                    response.body?.let { body ->
                        FileOutputStream(tempFile).use { output ->
                            body.byteStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                    } ?: throw Exception("Empty response body")
                }
            }.await()

            withContext(Dispatchers.Main) {
                loadModelLocal(tempFile!!.absolutePath, callback)
            }
        } catch (e: Exception) {
            Log.e("ModelHolder", "Error loading model", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Error loading model: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                callback(false)
            }
        }
    }

    fun cleanup() {
        loaders.forEach { loader ->
            if (!loader.isDone) {
                loader.cancel(true)
            }
        }
        currentModel = null
    }
}