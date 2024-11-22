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
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.common.SignInButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout

class UploadActivity : AppCompatActivity() {
    private lateinit var googleSignInManager: GoogleSignInManager

    private val tags = mutableListOf<String>()

    private val selectedImages = mutableListOf<Uri>()
    private var primaryImagePosition = 0
    private lateinit var imageAdapter: ImageSelectionAdapter
    private var selectedModel: Uri? = null

    private lateinit var uploadButton: Button
    private lateinit var loginButton: SignInButton

    private val imagesPicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris?.let {
            selectedImages.clear()
            selectedImages.addAll(it)
            updateImagesPreview()
        }
    }

    private val modelPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            if (it.toString().endsWith(".fbx", ignoreCase = true)) {
                selectedModel = it
                updateModelPreview()
            } else {
                showError("Please select an FBX file")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload)

        googleSignInManager = GoogleSignInManager(this)

        setupImageRecyclerView()

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
            handleUpload()
        }

        loginButton = findViewById(R.id.loginButton)
        loginButton.setOnClickListener {
            googleSignInManager.initiateSignIn()
        }

        updateButtonVisibility(GoogleAuthManager.isLoggedIn.value == true)
        observeAuthState()
    }

    private fun observeAuthState() {
        GoogleAuthManager.isLoggedIn.observe(this) { isLoggedIn ->
            updateButtonVisibility(isLoggedIn)
        }
    }

    private fun updateButtonVisibility(isLoggedIn: Boolean) {
        uploadButton.visibility = if (isLoggedIn) View.VISIBLE else View.GONE
        loginButton.visibility = if (isLoggedIn) View.GONE else View.VISIBLE
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
        imageAdapter.updateImages(selectedImages)
    }

    private fun updateModelPreview() {
        Log.d("UploadActivity", "updateModelPreview called")
        val modelText = findViewById<TextView>(R.id.selectedModelText)
        modelText.text = selectedModel?.lastPathSegment ?: "No model selected"
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