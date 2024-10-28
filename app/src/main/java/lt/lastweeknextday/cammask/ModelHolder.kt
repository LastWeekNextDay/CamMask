package lt.lastweeknextday.cammask

import android.net.Uri
import android.util.Log
import com.google.android.filament.MaterialInstance
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.RenderableDefinition
import com.google.ar.sceneform.rendering.RenderableInstance
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.Model
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.model.getRenderableByName
import io.github.sceneview.model.renderableManager
import io.github.sceneview.model.renderableNames

class ModelHolder(private val arSceneView: ARSceneView) {
    private var loader: ModelLoader = ModelLoader(arSceneView.engine, arSceneView.context)

    private var model: Model? = null

    fun getModel(): Model? {
        return model
    }

    fun loadModel(path: String, callback: (Boolean) -> Unit = {}) {
        try {
            Log.d("ModelHolder", "Loading model from path: $path")

            loader.loadModelAsync(path, onResult = {
                if (it == null) {
                    Log.e("ModelHolder", "Error loading model: model instance is null")
                    callback(false)
                    return@loadModelAsync
                }

                Log.d("ModelHolder", "Model loaded successfully")
                Log.d("ModelHolder", "Model renderable names:")
                for (name in it.renderableNames) {
                    Log.d("ModelHolder", name)
                }
                Log.d("ModelHolder", "Model material instances:")
                for (materialInstance in it.instance.materialInstances) {
                    Log.d("ModelHolder", materialInstance.name)
                }

                model = it
                callback(true)
            })
        } catch (e: Exception) {
            Log.e("ModelHolder", "Error loading model", e)
            callback(false)
        }
    }

    fun unloadModel() {
        model = null
    }
}