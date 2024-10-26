package lt.lastweeknextday.cammask

import android.util.Log
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.VertexBuffer
import com.google.android.filament.VertexBuffer.AttributeType
import com.google.android.filament.VertexBuffer.VertexAttribute.*
import com.google.ar.core.AugmentedFace
import com.google.ar.core.TrackingState
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AugmentedFaceNode
import io.github.sceneview.node.MeshNode

class ModelRenderer(private val sceneView: ARSceneView, private val modelHolder: ModelHolder) {
    private val faceNodes = mutableMapOf<AugmentedFace, AugmentedFaceNode>()

    private fun onFaceTrackingStopped(face: AugmentedFace, node: AugmentedFaceNode) {
        faceNodes.remove(face)
        sceneView.removeChildNode(node)
    }

    fun render(faces: Array<AugmentedFace>) {
        if (faces.isEmpty()) {
            return
        }

        if (modelHolder.getModel() == null) {
            return
        }

        val model = modelHolder.getModel()!!

        faces.forEach { face ->
            if (!faceNodes.containsKey(face) && face.meshVertices != null && face.meshVertices.limit() > 0) {
                try {
                    Log.d("ModelRenderer", "Creating face node with vertex count: ${face.meshVertices.limit() / 3}")
                    Log.d("ModelRenderer", "Face mesh stats:" +
                            "\nVertices: ${face.meshVertices?.limit() ?: -1}" +
                            "\nIndices: ${face.meshTriangleIndices?.limit() ?: -1}" +
                            "\nUV coords: ${face.meshTextureCoordinates?.limit() ?: -1}" +
                            "\nNormals: ${face.meshNormals?.limit() ?: -1}")

                    Log.d("ModelRenderer", "MaterialInstances count: ${model.materialInstances.size}")
                    for (i in 0 until model.materialInstances.size) {
                        Log.d("ModelRenderer", "MaterialInstance $i: ${model.materialInstances[i].name}")
                    }

                    val faceNode = AugmentedFaceNode(
                        engine = sceneView.engine,
                        augmentedFace = face
                    )

                    sceneView.addChildNode(faceNode)
                    faceNodes[face] = faceNode

                    Log.d("ModelRenderer", "Face node created successfully")
                } catch (e: Exception) {
                    Log.e("ModelRenderer", "Error creating face node", e)
                    e.printStackTrace()
                }
            }
        }

        faceNodes.forEach { (face, faceNode) ->
            if (face.trackingState == TrackingState.TRACKING) {
                faceNode.update(face)
            }
        }

        val trackedFaces = faces.filter { it.trackingState == TrackingState.TRACKING }.toSet()
        faceNodes.entries.removeAll { (face, node) ->
            if (!trackedFaces.contains(face)) {
                sceneView.removeChildNode(node)
                true
            } else {
                false
            }
        }
    }

    fun clear() {
        faceNodes.forEach { (_, faceNode) ->
            sceneView.removeChildNode(faceNode)
        }
        faceNodes.clear()
    }
}