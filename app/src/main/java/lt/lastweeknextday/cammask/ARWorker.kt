package lt.lastweeknextday.cammask

import com.google.ar.core.AugmentedFace
import com.google.ar.core.Session
import com.google.ar.core.TrackingState

class ARWorker(private val modelHolder: ModelHolder){
    private var arSession : Session? = null

    fun setARSession(session: Session) {
        arSession = session
    }

    fun getARSession() : Session? {
        return arSession
    }

    fun resumeARSession() {
        arSession?.resume()
    }

    fun pauseARSession() {
        arSession?.pause()
    }

    fun closeARSession() {
        arSession?.close()
    }

    fun update(session: Session) {
        val faces = session.getAllTrackables(AugmentedFace::class.java)
        updateFacesMeshInfo(faces)
    }

    private fun updateFacesMeshInfo(faces: Collection<AugmentedFace>) {
        for (face in faces) {
            if (face.trackingState == TrackingState.TRACKING) {
                val uvs = face.meshTextureCoordinates
                val indices = face.meshTriangleIndices
                val facePose = face.centerPose
                val meshVertices = face.meshVertices
                val meshNormals = face.meshNormals

                modelHolder.updateFaceData(face, uvs, indices, facePose, meshVertices, meshNormals)
            } else if (face.trackingState == TrackingState.STOPPED) {
                modelHolder.removeFaceData(face)
            }
        }
    }
}
