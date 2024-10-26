package lt.lastweeknextday.cammask

import android.net.Uri
import android.util.Log
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.model.ModelInstance

class ModelHolder(private val arSceneView: ARSceneView) {
    private var model: ModelInstance? = null

    fun getModel(): ModelInstance? {
        return model
    }

    fun loadModel(path: String, callback: (Boolean) -> Unit = {}) {
        try {
            Log.d("ModelHolder", "Loading model from path: $path")

            arSceneView.modelLoader.loadModelInstanceAsync(
                path
            ) { modelInstance ->
                model = modelInstance
                Log.d("ModelHolder", "Model loading completed successfully: ${modelInstance != null}")
                callback(modelInstance != null)
            }
        } catch (e: Exception) {
            Log.e("ModelHolder", "Error loading model", e)
            callback(false)
        }
    }

    fun unloadModel() {
        model = null
    }
}