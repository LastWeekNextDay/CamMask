package lt.lastweeknextday.cammask

import com.google.ar.core.AugmentedFace
import com.google.ar.core.TrackingState

class ARWorker(private val modelRenderer: ModelRenderer) {
    fun onAugmentedFaceTrackingUpdate(augmentedFace: AugmentedFace) {
        when (augmentedFace.trackingState) {
            TrackingState.TRACKING -> {
                if (augmentedFace.meshVertices != null &&
                    augmentedFace.meshVertices.limit() > 0) {
                    modelRenderer.onFaceDetected(augmentedFace)
                }
            }
            TrackingState.STOPPED -> {
                modelRenderer.onFaceDetected(augmentedFace)
            }
            else -> { }
        }
    }
}