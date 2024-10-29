package lt.lastweeknextday.cammask

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.google.ar.sceneform.rendering.ModelRenderable
import java.util.concurrent.CompletableFuture

class ModelHolder(private val context: Context) {
    private val loaders = mutableSetOf<CompletableFuture<*>>()
    private var currentModel: ModelRenderable? = null

    fun getModel(): ModelRenderable? = currentModel

    fun loadModel(modelPath: String, callback: (Boolean) -> Unit) {
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

    fun cleanup() {
        loaders.forEach { loader ->
            if (!loader.isDone) {
                loader.cancel(true)
            }
        }
        currentModel = null
    }
}