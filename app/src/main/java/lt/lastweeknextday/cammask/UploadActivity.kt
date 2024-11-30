package lt.lastweeknextday.cammask

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.common.SignInButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class UploadActivity : BaseActivity() {
    private val fileAnalyzer = FileAnalyzer()
    private val tags = mutableListOf<String>()

    private val selectedImages = mutableListOf<Uri>()
    private var primaryImagePosition = 0
    private lateinit var imageAdapter: ImageSelectionAdapter

    private var selectedModel: Uri? = null
    private var modelFullName: String = ""

    private lateinit var uploadButton: Button
    private lateinit var loginButton: SignInButton
    private lateinit var loginProgress: ProgressBar
    private var isLoggingInOut = false

    private val imagesPicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris?.let {
            selectedImages.clear()
            selectedImages.addAll(it)
            updateImagesPreview()
        }
    }

    private val modelPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            Log.d("UploadActivity", "Selected model uri: $it")
            val fileInfo = fileAnalyzer.analyze(this, it)
            Log.d("UploadActivity", "Selected file: $fileInfo")
            if (fileInfo.extension == "glb") {
                selectedModel = it
                modelFullName = fileInfo.name
                updateModelPreview()
            } else {
                showError("Please select a GLB file")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload)

        setupImageRecyclerView()
        setupViews()
        observeAuthState()
    }

    private fun setupViews() {
        findViewById<Button>(R.id.selectImagesButton).setOnClickListener {
            imagesPicker.launch("image/*")
        }

        findViewById<Button>(R.id.selectModelButton).setOnClickListener {
            modelPicker.launch("*/*")
        }

        findViewById<ImageButton>(R.id.addTagButton).setOnClickListener {
            showAddTagDialog()
        }

        uploadButton = findViewById(R.id.uploadButton)
        uploadButton.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                if (!GoogleAuthManager.checkIfCanUpload()) {
                    Toast.makeText(this@UploadActivity, "You are banned from uploading", Toast.LENGTH_LONG).show()
                    return@launch
                } else {
                    handleUpload()
                }
            }
        }

        loginButton = findViewById(R.id.loginButton)
        loginProgress = findViewById(R.id.loginProgress)

        loginButton.setOnClickListener {
            if (!isLoggingInOut) {
                setLoginLoadingState(true)
                googleSignInManager.initiateSignIn()
            }
        }
    }

    private fun setLoginLoadingState(loading: Boolean) {
        isLoggingInOut = loading
        if (loading) {
            loginButton.visibility = View.INVISIBLE
            loginProgress.visibility = View.VISIBLE
        } else {
            loginButton.visibility = View.VISIBLE
            loginProgress.visibility = View.GONE
        }
    }

    private fun observeAuthState() {
        GoogleAuthManager.isLoggedIn.observe(this) { isLoggedIn ->
            setLoginLoadingState(false)
            updateButtonVisibility(isLoggedIn)
        }
    }

    private fun updateButtonVisibility(isLoggedIn: Boolean) {
        uploadButton.visibility = if (isLoggedIn) View.VISIBLE else View.GONE
        loginButton.visibility = if (isLoggedIn) View.GONE else View.VISIBLE
        loginProgress.visibility = View.GONE
    }

    private fun setupImageRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.imagesRecyclerView)
        imageAdapter = ImageSelectionAdapter(selectedImages) { position ->
            primaryImagePosition = position
        }

        recyclerView.layoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.HORIZONTAL,
            false
        )
        recyclerView.adapter = imageAdapter
    }

    private fun showAddTagDialog() {
        Log.d("UploadActivity", "showAddTagDialog called")
        val input = EditText(this)
        MaterialAlertDialogBuilder(this)
            .setTitle("Add Tag")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val tag = input.text.toString().trim()
                if (tag.isNotEmpty()) {
                    tags.add(tag)
                    updateTagsView()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateTagsView() {
        Log.d("UploadActivity", "updateTagsView called")
        val chipGroup = findViewById<ChipGroup>(R.id.tagsChipGroup)
        chipGroup.removeAllViews()
        tags.forEach { tag ->
            val chip = Chip(this).apply {
                text = tag
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    tags.remove(tag)
                    chipGroup.removeView(this)
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun updateImagesPreview() {
        val imagesText = findViewById<TextView>(R.id.selectedImagesText)
        imageAdapter.updateImages(selectedImages)
        if (selectedImages.isNotEmpty()) {
            imagesText.text = "Selected images: ${selectedImages.size}"
        } else {
            imagesText.text = "No images selected"
        }
    }

    private fun updateModelPreview() {
        Log.d("UploadActivity", "updateModelPreview called")
        val modelText = findViewById<TextView>(R.id.selectedModelText)
        if (selectedModel != null) {
            modelText.text = modelFullName
        } else {
            modelText.text = "No model selected"
        }
    }

    private fun handleUpload() {
        Log.d("UploadActivity", "handleUpload called")
        val name = findViewById<TextInputLayout>(R.id.maskNameInput).editText?.text.toString()
        val description = findViewById<TextInputLayout>(R.id.descriptionInput).editText?.text.toString()

        if (name.isBlank()) {
            showError("Please enter a mask name")
            return
        }

        val primaryImage = if (selectedImages.isNotEmpty()) {
            selectedImages[primaryImagePosition]
        } else null

        if (primaryImage == null) {
            showError("Please select at least one image")
            return
        }

        if (selectedModel == null) {
            showError("Please select a model file")
            return
        }

        // TODO: Upload func
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun showError(message: String) {
        Log.e("UploadActivity", "showError called with message: $message")
        MaterialAlertDialogBuilder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}