package lt.lastweeknextday.cammask

import com.google.ar.core.AugmentedFace
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import java.nio.FloatBuffer
import java.nio.ShortBuffer

class ARWorker(){
    data class FaceData(
        val uvs: FloatBuffer,
        val indices: ShortBuffer,
        val facePose: Pose,
        val meshVertices: FloatBuffer,
        val meshNormals: FloatBuffer
    )
    private val facesData: MutableMap<AugmentedFace, FaceData> = mutableMapOf()

    private var uvs: FloatBuffer? = null
    private var indices: ShortBuffer? = null

    fun update(session: Session) {
        val faces = session.getAllTrackables(AugmentedFace::class.java)
        updateFacesMeshInfo(faces)
    }

    fun getFacesData(): MutableMap<AugmentedFace, FaceData> {
        return facesData
    }

    fun getFaces(): Array<AugmentedFace> {
        val faces = Array(facesData.size) {
            i -> facesData.keys.elementAt(i)
        }
        return faces
    }

    fun getAllFaceData(): Array<FaceData> {
        val facesData = Array(facesData.size) {
            i -> facesData.values.elementAt(i)
        }
        return facesData
    }

    private fun updateFaceData(face: AugmentedFace, uvs: FloatBuffer, indices: ShortBuffer, facePose: Pose, meshVertices: FloatBuffer, meshNormals: FloatBuffer) {
        facesData[face] = FaceData(uvs, indices, facePose, meshVertices, meshNormals)
    }

    private fun removeFaceData(face: AugmentedFace) {
        if (facesData.containsKey(face)){
            facesData.remove(face)
        }
    }

    private fun updateFacesMeshInfo(faces: Collection<AugmentedFace>) {
        for (face in faces) {
            if (face.trackingState == TrackingState.TRACKING) {
                if (uvs == null) {
                    uvs = face.meshTextureCoordinates
                }
                if (indices == null) {
                    indices = face.meshTriangleIndices
                }
                val facePose = face.centerPose
                val meshVertices = face.meshVertices
                val meshNormals = face.meshNormals

                updateFaceData(face, uvs!!, indices!!, facePose, meshVertices, meshNormals)
            } else if (face.trackingState == TrackingState.STOPPED) {
                removeFaceData(face)
            }
        }
    }
}
