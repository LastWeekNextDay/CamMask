package lt.lastweeknextday.cammask

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.google.ar.core.ArCoreApk
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.core.exceptions.UnsupportedConfigurationException
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.rendering.Renderable


class MainActivity : FragmentActivity() {
    private lateinit var arWorker: ARWorker
    private lateinit var session: Session

    private lateinit var arFragment: CustomArFragment

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Log.e("MainActivity", "Camera permission not granted.")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!checkARCoreSupport()) {
            finish()
        }

        if (!checkCameraPermission()) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        if (!checkDeviceSupport(this)) {
            finish()
        }

        val fragMan: FragmentManager = supportFragmentManager

        setContentView(R.layout.arlayout)
        arFragment = fragMan.findFragmentById(R.id.arFragment) as CustomArFragment

        arFragment.arSceneView.cameraStreamRenderPriority = Renderable.RENDER_PRIORITY_FIRST

        session = Session(this)
        initializeARCore(session)
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("ObsoleteSdkInt")
    fun checkDeviceSupport(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < VERSION_CODES.N) {
            Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG)
                .show()
            return false
        }

        val openGlVersionString =
            (activity.getSystemService(ACTIVITY_SERVICE) as ActivityManager)
                .deviceConfigurationInfo
                .glEsVersion
        if (openGlVersionString.toDouble() < 3.0) {
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
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
            }
            else -> false
        }
    }

    private fun initializeARCore(session: Session) {
        try {
            val modelHolder = ModelHolder()
            arWorker = ARWorker(modelHolder)

            val config = Config(session)
            config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
            config.augmentedFaceMode = Config.AugmentedFaceMode.MESH3D
            config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY

            val cameraConfigFilter = CameraConfigFilter(session)
            cameraConfigFilter.setFacingDirection(CameraConfig.FacingDirection.FRONT)
            val cameraConfigList = session.getSupportedCameraConfigs(cameraConfigFilter)

            if (cameraConfigList.isNotEmpty()) {
                session.cameraConfig = cameraConfigList[0]
            } else {
                throw UnsupportedConfigurationException("No supported camera config for front-facing camera.")
            }

            session.configure(config)
            arWorker.setARSession(session)
        } catch (e: UnavailableException) {
            Log.e("MainActivity", "ARCore initialization failed: ${e.message}")
            finish()
        } catch (e: UnsupportedConfigurationException) {
            Log.e("MainActivity", "ARCore configuration is unsupported: ${e.message}")
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        arWorker.resumeARSession()
        arFragment.onResume()
        arFragment.arSceneView.resume()
    }

    override fun onPause() {
        super.onPause()
        arWorker.pauseARSession()
        arFragment.onPause()
        arFragment.arSceneView.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        arWorker.closeARSession()
        arFragment.arSceneView.destroy()
        arFragment.onDestroy()
    }
}