package lt.lastweeknextday.cammask

import com.google.ar.core.AugmentedFace
import com.google.ar.core.Pose
import java.nio.FloatBuffer
import java.nio.ShortBuffer

class ModelHolder {
    data class FaceData(val uvs: FloatBuffer, val indices: ShortBuffer, val facePose: Pose, val meshVertices: FloatBuffer, val meshNormals: FloatBuffer)

    val facesData: MutableMap<AugmentedFace, FaceData> = mutableMapOf()

    fun updateFaceData(face: AugmentedFace, uvs: FloatBuffer, indices: ShortBuffer, facePose: Pose, meshVertices: FloatBuffer, meshNormals: FloatBuffer) {
        facesData[face] = FaceData(uvs, indices, facePose, meshVertices, meshNormals)
    }

    fun removeFaceData(face: AugmentedFace) {
        facesData.remove(face)
    }
}