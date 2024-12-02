package lt.lastweeknextday.cammask.activities

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.sceneform.Sceneform
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.ux.ArFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lt.lastweeknextday.cammask.misc.Constants
import lt.lastweeknextday.cammask.misc.FileAnalyzer
import lt.lastweeknextday.cammask.managers.camera.GalleryManager
import lt.lastweeknextday.cammask.ui.objects.LoadingDialog
import lt.lastweeknextday.cammask.adapters.MaskListAdapter
import lt.lastweeknextday.cammask.managers.MaskLoadManager
import lt.lastweeknextday.cammask.managers.camera.MediaCaptureManager
import lt.lastweeknextday.cammask.R
import lt.lastweeknextday.cammask.ar.ARWorker
import lt.lastweeknextday.cammask.ar.ModelHolder
import lt.lastweeknextday.cammask.ar.ModelRenderer
import lt.lastweeknextday.cammask.managers.auth.GoogleAuthManager
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class MainActivity : BaseActivity() {
    private lateinit var arFragment: ArFragment
    private lateinit var modelHolder: ModelHolder
    private lateinit var modelRenderer: ModelRenderer
    private lateinit var arWorker: ARWorker
    private lateinit var loadingDialog: LoadingDialog

    private lateinit var mediaCaptureManager: MediaCaptureManager
    private lateinit var flashOverlay: View

    private lateinit var galleryManager: GalleryManager
    private lateinit var galleryButton: ImageButton

    private lateinit var fileAnalyzer: FileAnalyzer

    private lateinit var maskListAdapter: MaskListAdapter

    private lateinit var maskLoadManager: MaskLoadManager

    private var refreshTimer: CountDownTimer? = null
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                Log.d("MainActivity", "Selected file: $uri")
                val fileInfo = fileAnalyzer.analyze(applicationContext, uri)
                Log.d("MainActivity", "File info: $fileInfo")
                if (fileInfo.extension == "glb") {
                    handleModelFile(uri)
                } else {
                    Toast.makeText(this, "Please select a GLB file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val maskActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getIntExtra("updatedMaskId", -1)?.let { maskId ->
                CoroutineScope(Dispatchers.Main).launch {
                    refreshMaskInList(maskId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.arlayout)

        loadingDialog = LoadingDialog(this)
        galleryManager = GalleryManager(this)
        fileAnalyzer = FileAnalyzer()

        if (savedInstanceState == null) {
            if (Sceneform.isSupported(this)) {
                checkAndRequestPermissions()
                setupAR()
                setupUI()
                setupSwipeRefresh()
            } else {
                finish()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        var permissions = arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        )

        if (android.os.Build.VERSION.SDK_INT < 33) {
            permissions += android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            permissions += arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.READ_MEDIA_AUDIO
            )
        }

        if (android.os.Build.VERSION.SDK_INT > 33) {
            permissions += android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        }

        val notGrantedPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGrantedPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGrantedPermissions.toTypedArray(), 0)
        }
    }

    private fun setupAR() {
        arFragment = (supportFragmentManager.findFragmentById(R.id.arFragment) as ArFragment).apply {
            setOnSessionConfigurationListener{ session, config ->
                val filter = CameraConfigFilter(session)
                filter.setFacingDirection(CameraConfig.FacingDirection.FRONT)

                session.setCameraConfig(session.getSupportedCameraConfigs(filter)[0])

                config.setPlaneFindingMode(Config.PlaneFindingMode.DISABLED)
                config.setAugmentedFaceMode(Config.AugmentedFaceMode.MESH3D)
                config.setLightEstimationMode(Config.LightEstimationMode.DISABLED)

                session.configure(config)
            }
            setOnViewCreatedListener {
                arSceneView.setCameraStreamRenderPriority(Renderable.RENDER_PRIORITY_FIRST)

                modelHolder = ModelHolder(this@MainActivity)
                modelRenderer = ModelRenderer(arSceneView)
                arWorker = ARWorker(modelRenderer)
                mediaCaptureManager = MediaCaptureManager(this@MainActivity, arSceneView)

                arFragment.setOnAugmentedFaceUpdateListener(arWorker::onAugmentedFaceTrackingUpdate)

                /*
                loadingDialog.show("Loading model...", transparentBackground = true)
                modelHolder.loadModel("fox.glb") { success ->
                    loadingDialog.hide()
                    if (success) {
                        modelRenderer.setModel(modelHolder.getModel())
                    }
                }
                */
            }
        }
    }

    private fun setupUI() {
        flashOverlay = findViewById(R.id.flashOverlay)

        galleryButton = findViewById(R.id.galleryButton)
        galleryButton.setOnClickListener {
            galleryManager.openGallery(this)
        }

        findViewById<ImageButton>(R.id.capturePhotoButton).setOnClickListener {
            showCaptureAnimation()
            mediaCaptureManager.capturePhoto { success ->
                if (success) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        refreshGalleryThumbnail()
                    }, 500)
                }
            }
        }

        val videoButton = findViewById<ImageButton>(R.id.captureVideoButton)
        videoButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO), 0)
                return@setOnClickListener
            }

            if (!mediaCaptureManager.isRecording()) {
                if (mediaCaptureManager.startVideoRecording()) {
                    videoButton.setBackgroundResource(R.drawable.video_button_recording)
                }
            } else {
                mediaCaptureManager.stopVideoRecording()
                videoButton.setBackgroundResource(R.drawable.video_button_background)
                Handler(Looper.getMainLooper()).postDelayed({
                    refreshGalleryThumbnail()
                }, 500)
            }
        }

        maskLoadManager = MaskLoadManager()
        maskListAdapter = MaskListAdapter(
            onMaskSelected = { selectedMask ->
                CoroutineScope(Dispatchers.Main).launch {
                    loadSelectedMask(selectedMask)
                }
            },
            onMaskClicked = { mask ->
                val intent = Intent(this, MaskActivity::class.java)
                intent.putExtra("maskData", mask.toString())
                maskActivityLauncher.launch(intent)
            }
        )

        findViewById<RecyclerView>(R.id.maskList).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = maskListAdapter

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    val layoutManager = recyclerView.layoutManager as GridLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()

                    if (!maskListAdapter.isLoading() && maskListAdapter.hasMore()) {
                        if ((visibleItemCount + firstVisibleItem) >= totalItemCount - 4) {
                            loadMoreMasks()
                        }
                    }
                }
            })
        }

        loadMoreMasks()

        findViewById<Button>(R.id.uploadButton).setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                if (GoogleAuthManager.checkIfLoggedIn() && !GoogleAuthManager.checkIfCanUpload()) {
                    Toast.makeText(this@MainActivity, "You are banned from uploading", Toast.LENGTH_LONG).show()
                    return@launch
                }
                startActivity(Intent(this@MainActivity, UploadActivity::class.java))
            }

        }

        findViewById<Button>(R.id.filterButton).setOnClickListener {
            // TODO: Filter func
            Toast.makeText(this, "Filter functionality coming soon", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.testButton).setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            filePickerLauncher.launch(intent)
        }
    }

    private suspend fun loadSelectedMask(mask: JSONObject) {
        try {
            val maskUrl = mask.getString("maskUrl")
            Log.d("MainActivity","Loading mask: $maskUrl")
            loadingDialog.show("Loading mask...", transparentBackground = true)

            modelHolder.loadModelRemote(maskUrl) { success ->
                loadingDialog.hide()
                if (success) {
                    modelRenderer.setModel(modelHolder.getModel())
                } else {
                    maskListAdapter.clearSelection()
                    Toast.makeText(this, "Failed to load mask", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            loadingDialog.hide()
            maskListAdapter.clearSelection()
            Toast.makeText(this, "Error loading mask: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleModelFile(uri: Uri) {
        loadingDialog.show("Loading model...", transparentBackground = true)
        modelHolder.loadModelLocal(uri.toString()) { success ->
            loadingDialog.hide()
            if (success) {
                modelRenderer.setModel(modelHolder.getModel())
            }
        }
    }

    private fun showCaptureAnimation() {
        flashOverlay.apply {
            animate().cancel()

            bringToFront()

            setBackgroundColor(android.graphics.Color.WHITE)
            alpha = 0f
            visibility = View.VISIBLE

            animate()
                .alpha(0.7f)
                .setDuration(50)
                .withEndAction {
                    animate()
                        .alpha(0f)
                        .setDuration(50)
                        .withEndAction {
                            visibility = View.INVISIBLE
                        }
                }
                .start()
        }
    }

    private fun loadMoreMasks() {
        lifecycleScope.launch {
            try {
                maskListAdapter.loadMore()
                val (newMasks, lastId) = maskLoadManager.loadMasks(
                    limit = 6,
                    lastId = maskListAdapter.getLastId()?.let { Integer.parseInt(it) },
                    orderBy = "ratingsCount",
                    orderDirection = "desc"
                )
                maskListAdapter.addMasks(newMasks, lastId)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading masks", e)
                Toast.makeText(this@MainActivity, "Error loading masks", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshGalleryThumbnail() {
        lifecycleScope.launch(Dispatchers.IO) {
            MediaScannerConnection.scanFile(
                applicationContext,
                arrayOf(Environment.getExternalStorageDirectory().toString()),
                null
            ) { _, _ ->
                lifecycleScope.launch(Dispatchers.Main) {
                    galleryManager.updateGalleryButtonThumbnail(galleryButton)
                }
            }
        }
    }

    private suspend fun refreshMaskInList(maskId: Int) {
        CoroutineScope(Dispatchers.Main).async {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://getmask-${Constants.BASE_URL}/?maskId=$maskId")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val updatedMask = JSONObject(response.body!!.string())
                        updateMaskInAdapter(updatedMask)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error refreshing mask data", e)
            }
        }.await()
    }

    private fun updateMaskInAdapter(updatedMask: JSONObject) {
        val adapter = findViewById<RecyclerView>(R.id.maskList).adapter as MaskListAdapter
        adapter.updateMask(updatedMask)
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener {
            refreshTimer?.cancel()
            refreshTimer = object : CountDownTimer(1000, 1000) {
                override fun onTick(millisUntilFinished: Long) {}

                override fun onFinish() {
                    maskListAdapter.clearAll()
                    loadMoreMasks()
                    swipeRefreshLayout.isRefreshing = false
                }
            }.start()
        }

        swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
            refreshTimer?.cancel()
            false
        }
    }

    override fun onResume() {
        super.onResume()

        if (!::galleryButton.isInitialized) {
            lifecycleScope.launch {
                while (!::galleryButton.isInitialized) {
                    delay(100)
                }
                refreshGalleryThumbnail()
            }
        } else {
            refreshGalleryThumbnail()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        modelHolder.cleanup()
        modelRenderer.cleanup()
        mediaCaptureManager.cleanup()
        galleryManager.cleanup()
    }
}