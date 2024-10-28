package lt.lastweeknextday.cammask

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.pm.PackageManager
import android.graphics.Camera
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.ar.core.ArCoreApk
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.core.exceptions.UnsupportedConfigurationException
import io.github.sceneview.ar.ARSceneView
import kotlinx.coroutines.launch
import java.util.EnumSet


class MainActivity : AppCompatActivity(R.layout.arlayout) {
    private lateinit var sceneView: ARSceneView
    private lateinit var progressBar: ProgressBar

    private lateinit var arWorker: ARWorker
    private lateinit var modelHolder: ModelHolder
    private lateinit var modelRenderer: ModelRenderer


    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Log.e("MainActivity", "Camera permission not granted.")
            finish()
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("ObsoleteSdkInt")
    fun checkDeviceSupport(activity: Activity): Boolean {
        val openGlVersionString =
            (activity.getSystemService(ACTIVITY_SERVICE) as ActivityManager)
                .deviceConfigurationInfo
                .glEsVersion
        if (openGlVersionString.toDouble() < 3.0) {
            Toast.makeText(activity, "OpenGL ES 3.0 or later required", Toast.LENGTH_LONG)
                .show()
            return false
        }
        return true
    }

    private fun checkARCoreSupport(): Boolean {
        return when (ArCoreApk.getInstance().checkAvailability(this)) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> true
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> {
                try {
                    val installStatus = ArCoreApk.getInstance().requestInstall(this, true)
                    installStatus == ArCoreApk.InstallStatus.INSTALLED
                } catch (e: UnavailableException) {
                    false
                }
            } else -> false
        }
    }

    private fun getFrontCameraConfig(session: Session): CameraConfig {
        val cameraConfigFilter = CameraConfigFilter(session).apply {
            setFacingDirection(CameraConfig.FacingDirection.FRONT)
            setTargetFps(EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30, CameraConfig.TargetFps.TARGET_FPS_60))
        }

        val cameraConfigList = session.getSupportedCameraConfigs(cameraConfigFilter)

        return if (cameraConfigList.isNotEmpty()) {
            Log.d("MainActivity", "Selected front camera config: ${cameraConfigList[0].facingDirection}")
            cameraConfigList[0]
        } else {
            throw UnsupportedConfigurationException("No camera configs found.")
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setup() {
        if (!checkARCoreSupport()) {
            finish()
        }

        if (!checkDeviceSupport(this)) {
            finish()
        }

        if (!checkCameraPermission()) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED
            ) {
                progressBar.visibility = ProgressBar.VISIBLE

                sceneView.apply {
                    configureSession { session, config ->
                        session.cameraConfig = getFrontCameraConfig(session)

                        config.apply {
                            augmentedFaceMode = Config.AugmentedFaceMode.MESH3D
                            cloudAnchorMode = Config.CloudAnchorMode.DISABLED
                            depthMode = Config.DepthMode.DISABLED
                            flashMode = Config.FlashMode.OFF
                            focusMode = Config.FocusMode.AUTO
                            geospatialMode = Config.GeospatialMode.DISABLED
                            instantPlacementMode = Config.InstantPlacementMode.DISABLED
                            lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
                            planeFindingMode = Config.PlaneFindingMode.DISABLED
                            textureUpdateMode = Config.TextureUpdateMode.BIND_TO_TEXTURE_EXTERNAL_OES
                            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        }

                        session.configure(config)
                    }
                    onSessionUpdated = { _, _ ->
                        if (session != null) {
                            arWorker.update(session!!)
                            val faces = arWorker.getFaces()
                            modelRenderer.render(faces)
                        }
                    }
                    onSessionCreated = {
                        Log.d("MainActivity", "Session created")
                        modelHolder = ModelHolder(sceneView)
                        modelRenderer = ModelRenderer(sceneView, modelHolder)
                        arWorker = ARWorker()
                        progressBar.visibility = ProgressBar.GONE
                    }
                    onSessionConfigChanged = { _, _ ->
                        onSessionResumed = {
                            it.setCameraTextureNames(
                                sceneView.cameraStream?.cameraTextureIds ?: intArrayOf()
                            )
                        }
                    }
                }

                sceneView.setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        v.performClick()
                        Log.d("MainActivity", "Touch event detected")
                        toggleModel()
                    }
                    true
                }
            }
        }
    }

    private fun toggleModel() {
        if (modelHolder.getModel() != null) {
            modelRenderer.clear()
            modelHolder.unloadModel()
        } else {
            progressBar.visibility = ProgressBar.VISIBLE
            modelHolder.loadModel("fox.glb") { success ->
                runOnUiThread {
                    progressBar.visibility = ProgressBar.GONE
                    if (!success) {
                        Toast.makeText(this, "Failed to load model", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sceneView = findViewById(R.id.sceneView)
        progressBar = findViewById(R.id.progressBar)
        arWorker = ARWorker()
        modelHolder = ModelHolder(sceneView)
        modelRenderer = ModelRenderer(sceneView, modelHolder)
        setup()
    }
}
