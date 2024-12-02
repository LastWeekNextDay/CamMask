package lt.lastweeknextday.cammask.managers.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaRecorder
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.PixelCopy
import android.widget.Toast
import com.google.ar.sceneform.ArSceneView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MediaCaptureManager(
    private val context: Context,
    private val arSceneView: ArSceneView
) {
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var videoFile: File? = null

    fun capturePhoto(onComplete: (Boolean) -> Unit) {
        try {
            val bitmap = Bitmap.createBitmap(
                arSceneView.width, arSceneView.height,
                Bitmap.Config.ARGB_8888
            )

            PixelCopy.request(
                arSceneView,
                bitmap,
                { copyResult ->
                    if (copyResult == PixelCopy.SUCCESS) {
                        savePhotoToGallery(bitmap, onComplete)
                    } else {
                        bitmap.recycle()
                        Toast.makeText(context, "Failed to capture photo", Toast.LENGTH_SHORT).show()
                        onComplete(false)
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (e: Exception) {
            Log.e("MediaCaptureManager", "Error capturing photo", e)
            Toast.makeText(context, "Failed to capture photo", Toast.LENGTH_SHORT).show()
            onComplete(false)
        }
    }

    private fun savePhotoToGallery(bitmap: Bitmap, onComplete: (Boolean) -> Unit) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "AR_PHOTO_$timestamp.jpg"

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    Toast.makeText(context, "Photo saved!", Toast.LENGTH_SHORT).show()
                    onComplete(true)
                }
            }
        } catch (e: Exception) {
            Log.e("MediaCaptureManager", "Error saving photo", e)
            Toast.makeText(context, "Failed to save photo", Toast.LENGTH_SHORT).show()
            onComplete(false)
        } finally {
            bitmap.recycle()
        }
    }


    fun isRecording(): Boolean {
        return isRecording
    }

    fun startVideoRecording(): Boolean {
        if (isRecording) return false

        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "AR_VIDEO_$timestamp.mp4"

            videoFile = File(
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                filename
            )

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                mediaRecorder = MediaRecorder(context).apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setVideoSource(MediaRecorder.VideoSource.SURFACE)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setOutputFile(videoFile?.absolutePath)
                    setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setVideoSize(arSceneView.width, arSceneView.height)
                    setVideoFrameRate(30)
                    setVideoEncodingBitRate(10000000)
                    setAudioEncodingBitRate(128000)
                    setAudioSamplingRate(44100)
                    prepare()
                }
            } else {
                mediaRecorder = MediaRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setVideoSource(MediaRecorder.VideoSource.SURFACE)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setOutputFile(videoFile?.absolutePath)
                    setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setVideoSize(arSceneView.width, arSceneView.height)
                    setVideoFrameRate(30)
                    setVideoEncodingBitRate(10000000)
                    setAudioEncodingBitRate(128000)
                    setAudioSamplingRate(44100)
                    prepare()
                }
            }

            arSceneView.startMirroringToSurface(
                mediaRecorder?.surface,
                0,
                0,
                arSceneView.width,
                arSceneView.height
            )

            mediaRecorder?.start()
            isRecording = true
            Toast.makeText(context, "Recording started", Toast.LENGTH_SHORT).show()
            return true
        } catch (e: Exception) {
            Log.e("MediaCaptureManager", "Error starting video recording", e)
            Toast.makeText(context, "Failed to start recording", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    fun stopVideoRecording() {
        if (!isRecording) return

        try {
            arSceneView.stopMirroringToSurface(mediaRecorder?.surface)
            isRecording = false

            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null

            videoFile?.let {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, it.name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
                }

                context.contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )?.let { uri ->
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        it.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    it.delete()
                }
            }

            Toast.makeText(context, "Video saved!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MediaCaptureManager", "Error stopping video recording", e)
            Toast.makeText(context, "Failed to save video", Toast.LENGTH_SHORT).show()
        }
    }

    fun cleanup() {
        if (isRecording) {
            stopVideoRecording()
        }
        mediaRecorder?.release()
        mediaRecorder = null
    }
}