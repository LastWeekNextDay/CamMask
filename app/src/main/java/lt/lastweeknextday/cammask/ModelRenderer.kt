package lt.lastweeknextday.cammask

import android.util.Log
import com.google.ar.core.AugmentedFace
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.RenderableInstance
import com.google.ar.sceneform.ux.AugmentedFaceNode

class ModelRenderer(private val arSceneView: ArSceneView) {
    private var currentModel: ModelRenderable? = null
    private val faceNodes = mutableMapOf<AugmentedFace, AugmentedFaceNode>()

    fun setModel(model: ModelRenderable?) {
        currentModel = model
    }

    fun onFaceDetected(face: AugmentedFace) {
        val model = currentModel ?: return
        val existingFaceNode = faceNodes[face]

        try {
            when (face.trackingState) {
                TrackingState.TRACKING -> {
                    if (existingFaceNode == null) {
                        val faceNode = AugmentedFaceNode(face)
                        val modelInstance: RenderableInstance =
                            faceNode.setFaceRegionsRenderable(model)

                        modelInstance.apply {
                            isShadowCaster = false
                            isShadowReceiver = true
                        }

                        arSceneView.scene.addChild(faceNode)
                        faceNodes[face] = faceNode
                    }
                }
                TrackingState.STOPPED -> {
                    existingFaceNode?.let {
                        arSceneView.scene.removeChild(it)
                    }
                    faceNodes.remove(face)
                }
                else -> {

                }
            }
        } catch (e: Exception) {
            Log.e("ModelRenderer", "Error rendering face model", e)
        }
    }

    fun cleanup() {
        faceNodes.values.forEach { node ->
            arSceneView.scene.removeChild(node)
        }
        faceNodes.clear()
    }
}