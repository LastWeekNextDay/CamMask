package lt.lastweeknextday.cammask.activities

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.gms.common.SignInButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lt.lastweeknextday.cammask.adapters.CommentsAdapter
import lt.lastweeknextday.cammask.misc.Constants
import lt.lastweeknextday.cammask.adapters.ImageCarouselAdapter
import lt.lastweeknextday.cammask.R
import lt.lastweeknextday.cammask.managers.auth.GoogleAuthManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class MaskActivity : BaseActivity() {
    private lateinit var maskData: JSONObject
    private lateinit var userData: JSONObject
    private lateinit var commentsAdapter: CommentsAdapter
    private var currentRating: Int = 0
    private var isLoggingInOut = false
    private lateinit var reportDialog: Dialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mask)

        val maskJson = intent.getStringExtra("maskData") ?: run {
            finish()
            return
        }

        try {
            maskData = JSONObject(maskJson)
            CoroutineScope(Dispatchers.Main).launch {
                userData = fetchUserData(maskData.getString("uploaderGoogleId"))
                if (userData.getInt("id") == -1) {
                    Toast.makeText(this@MaskActivity, "Error loading uploader data", Toast.LENGTH_SHORT).show()
                    finish()
                }
                setupUploaderView()
            }.start()
            setupViews()
            setupObservers()
            CoroutineScope(Dispatchers.Main).async {
                loadComments()
            }.start()
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading mask data", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private suspend fun fetchUserData(googleId: String): JSONObject {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://getuser-${Constants.BASE_URL}/?googleId=$googleId")
            .get()
            .build()

        var userData = JSONObject()
        CoroutineScope(Dispatchers.IO).async {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    userData = JSONObject(response.body!!.string())
                } else {
                    Log.e("MaskActivity", "Failed to load user data: ${response.code}")
                }
            }
        }.await()
        return userData
    }

    private fun setupViews() {
        findViewById<TextView>(R.id.maskName).text = maskData.getString("maskName")
        findViewById<TextView>(R.id.maskDescription).text =
            maskData.optString("description", "No description available")
        val tagsRaw = maskData.getString("tags")
        val tags = tagsRaw.trim('[', ']').split(",").map { it.trim() }
        findViewById<TextView>(R.id.tagTextMain).text = tags.joinToString(", ")
        findViewById<ImageButton>(R.id.reportMaskButton).setOnClickListener {
            showReportDialog("mask", maskData.getInt("id").toString())
        }

        val imagesList = maskData.getString("images")
            .trim('[', ']')
            .split(",")
            .map { it.trim() }

        val viewPager = findViewById<ViewPager2>(R.id.imageCarousel)
        viewPager.adapter = ImageCarouselAdapter(imagesList)

        updateAverageRatingDisplay(
            maskData.getDouble("averageRating"),
            maskData.getInt("ratingsCount")
        )

        val commentsList = findViewById<RecyclerView>(R.id.commentsList)
        commentsAdapter = CommentsAdapter(
            onReportClick = { commentId ->
                showReportDialog("comment", commentId)
            }
        )
        commentsList.layoutManager = LinearLayoutManager(this)
        commentsList.adapter = commentsAdapter

        setupCommentSubmission()
    }

    private fun setupUploaderView(){
        findViewById<TextView>(R.id.userNameUploader).text = userData.getString("name")
        val uploaderImage = findViewById<ImageView>(R.id.userImageUploader)
        if (userData.getString("photoUrl") != ""){
            Glide.with(this).load(userData.getString("photoUrl")).into(uploaderImage)
        } else {
            uploaderImage.setImageResource(R.drawable.default_pic)
        }
    }

    private fun setLoginLoadingState(loading: Boolean) {
        isLoggingInOut = loading
        val loginButton = findViewById<SignInButton>(R.id.loginButton)
        val loginProgress = findViewById<View>(R.id.loginProgress)
        if (loading) {
            loginButton.visibility = View.INVISIBLE
            loginProgress.visibility = View.VISIBLE
        } else {
            loginButton.visibility = View.VISIBLE
            loginProgress.visibility = View.GONE
        }
    }

    private fun setupCommentSubmission() {
        val commentSection = findViewById<LinearLayout>(R.id.commentSubmissionSection)
        val loginButton = findViewById<SignInButton>(R.id.loginButton)
        val commentInput = findViewById<EditText>(R.id.commentInput)
        val submitButton = findViewById<Button>(R.id.submitComment)
        val ratingStars = findViewById<LinearLayout>(R.id.ratingStars)
        val userRatingStars = findViewById<LinearLayout>(R.id.userRatingStarsContainer)

        for (i in 0 until 5) {
            (userRatingStars.getChildAt(i) as ImageView).setOnClickListener {
                CoroutineScope(Dispatchers.Main).launch {
                    submitRating(i + 1)
                }
            }
        }

        submitButton.setOnClickListener {
            val comment = commentInput.text.toString()
            if (comment.isNotBlank()) {
                CoroutineScope(Dispatchers.Main).launch {
                    submitComment(comment)
                    commentInput.text.clear()
                }
            }
        }

        loginButton.setOnClickListener {
            if (!isLoggingInOut) {
                setLoginLoadingState(true)
                googleSignInManager.initiateSignIn()
            }
        }

        updateSubmissionUIState()
    }

    private fun updateSubmissionUIState() {
        val commentSection = findViewById<LinearLayout>(R.id.commentSubmissionSection)
        val loginButton = findViewById<SignInButton>(R.id.loginButton)

        if (GoogleAuthManager.checkIfLoggedIn()) {
            commentSection.visibility = View.VISIBLE
            loginButton.visibility = View.GONE
        } else {
            commentSection.visibility = View.GONE
            loginButton.visibility = View.VISIBLE
        }
    }

    private suspend fun submitRating(rating: Int) {
        if (!GoogleAuthManager.checkIfLoggedIn()) {
            Toast.makeText(this, "Please login to rate", Toast.LENGTH_SHORT).show()
            return
        }

        var submitted = false
        var allowed = true
        val ratingProgress = findViewById<View>(R.id.ratingProgress)
        val container = findViewById<View>(R.id.userRatingStarsContainer)

        container.visibility = View.GONE
        ratingProgress.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).async {
            try {
                if (!GoogleAuthManager.checkIfCanComment()) {
                    allowed = false
                    return@async
                }

                val maskId = maskData.getInt("id").toString()

                val client = OkHttpClient()
                val requestBody = JSONObject().apply {
                    put("maskId", maskId)
                    put("googleId", GoogleAuthManager.getGoogleAccount()?.id)
                    put("rating", rating)
                }.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("https://postrating-${Constants.BASE_URL}")
                    .post(requestBody)
                    .build()

                CoroutineScope(Dispatchers.IO).async {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            currentRating = rating
                            val resultIntent = Intent().apply {
                                putExtra("updatedMaskId", maskData.getInt("id"))
                            }
                            setResult(Activity.RESULT_OK, resultIntent)
                            loadMaskData()
                            submitted = true
                        }
                    }
                }.await()
            } catch (e: Exception) {
                Log.e("MaskActivity", "Error submitting rating", e)
                submitted = false
            }
        }.await()
        if (!allowed) {
            Toast.makeText(this, "You cannot do this task", Toast.LENGTH_SHORT).show()
            return
        } else if (!submitted) {
            Toast.makeText(this, "Error submitting rating", Toast.LENGTH_SHORT).show()
            return
        }

        container.visibility = View.VISIBLE
        ratingProgress.visibility = View.GONE
        if (submitted) {
            updateUserRatingStars()
        }
    }

    private suspend fun submitComment(comment: String) {
        var submitted = false
        val commentButton = findViewById<Button>(R.id.submitComment)
        val progressBar = findViewById<View>(R.id.submitCommentProgress)
        commentButton.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).async {
            try {
                val maskId = maskData.getInt("id").toString()

                val client = OkHttpClient()
                val requestBody = JSONObject().apply {
                    put("maskId", maskId)
                    put("googleId", GoogleAuthManager.getGoogleAccount()?.id)
                    put("comment", comment)
                }.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("https://postcomment-${Constants.BASE_URL}")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        findViewById<EditText>(R.id.commentInput).text.clear()
                        loadComments()
                        submitted = true
                    }
                }
            } catch (e: Exception) {
                Log.e("MaskActivity", "Error submitting comment", e)
                submitted = true
            }
        }.await()
        commentButton.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        if (!submitted) {
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(this@MaskActivity, "Error submitting comment", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun loadComments() {
        var loaded = false
        CoroutineScope(Dispatchers.IO).async {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://getcomments-${Constants.BASE_URL}/?maskId=${maskData.getInt("id")}")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val comments = JSONArray(response.body!!.string())
                        CoroutineScope(Dispatchers.Main).launch {
                            commentsAdapter.updateComments(comments)
                        }
                        loaded = true
                    }
                }
            } catch (e: Exception) {
                Log.e("MaskActivity", "Error loading comments", e)
            }
        }.await()
        if (!loaded) {
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(this@MaskActivity, "Error loading comments", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun loadMaskData() {
        var loaded = false
        CoroutineScope(Dispatchers.IO).async {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://getmask-${Constants.BASE_URL}/?maskId=${maskData.getInt("id")}")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        maskData = JSONObject(response.body!!.string())
                        CoroutineScope(Dispatchers.Main).launch {
                            updateAverageRatingDisplay(
                                maskData.getDouble("averageRating"),
                                maskData.getInt("ratingsCount")
                            )
                        }
                        loaded = true
                    }
                }
            } catch (e: Exception) {
                Log.e("MaskActivity", "Error loading mask data", e)
            }
        }.await()
        if (!loaded) {
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(this@MaskActivity, "Error loading mask data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun loadUserRating() {
        if (!GoogleAuthManager.checkIfLoggedIn()) {
            return
        }

        val ratingProgress = findViewById<View>(R.id.ratingProgress)
        val container = findViewById<View>(R.id.userRatingStarsContainer)

        container.visibility = View.GONE
        ratingProgress.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).async {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://getrating-${Constants.BASE_URL}/?maskId=${maskData.getInt("id")}&googleId=${GoogleAuthManager.getGoogleAccount()?.id}")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.code == 404) {
                        currentRating = 0
                    } else if (response.isSuccessful) {
                        val ratingData = JSONObject(response.body!!.string())
                        currentRating = ratingData.getInt("rating")
                        updateUserRatingStars()
                    } else {
                        Log.e("MaskActivity", "Failed to load user rating: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e("MaskActivity", "Error loading user rating", e)
            }
        }.await()

        container.visibility = View.VISIBLE
        ratingProgress.visibility = View.GONE
    }

    private fun updateAverageRatingDisplay(rating: Double, count: Int) {
        findViewById<TextView>(R.id.ratingText).text =
            String.format("%.1f (%d ratings)", rating, count)

        val starsContainer = findViewById<LinearLayout>(R.id.ratingStars)
        val fullStars = rating.toInt()

        for (i in 0 until 5) {
            (starsContainer.getChildAt(i) as ImageView)
                .setImageResource(if (i < fullStars) R.drawable.star_filled else R.drawable.star_empty)
        }
    }

    private fun updateUserRatingStars() {
        val userRatingStars = findViewById<LinearLayout>(R.id.userRatingStarsContainer)
        for (i in 0 until 5) {
            (userRatingStars.getChildAt(i) as ImageView)
                .setImageResource(if (i < currentRating) R.drawable.star_filled else R.drawable.star_empty)
        }
    }

    private fun setupObservers() {
        GoogleAuthManager.isLoggedIn.observe(this) { isLoggedIn ->
            updateSubmissionUIState()
            if (isLoggedIn) {
                setLoginLoadingState(false)
                val signInButton = findViewById<SignInButton>(R.id.loginButton)
                signInButton.visibility = View.GONE
                CoroutineScope(Dispatchers.Main).launch {
                    loadUserRating()
                }
            } else {
                currentRating = 0
                updateUserRatingStars()
            }
        }
    }

    private fun showReportDialog(itemType: String, itemId: String) {
        if (!GoogleAuthManager.checkIfLoggedIn()) {
            Toast.makeText(this, "Please login to report", Toast.LENGTH_SHORT).show()
            return
        }

        reportDialog = Dialog(this).apply {
            setContentView(R.layout.dialog_report)
            setCancelable(false)
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        with(reportDialog) {
            findViewById<RadioButton>(R.id.reasonNotWorking).visibility =
                if (itemType == "mask") View.VISIBLE else View.GONE

            val buttonContainer = findViewById<View>(R.id.buttonContainer)
            val sendProgress = findViewById<ProgressBar>(R.id.sendProgress)

            findViewById<Button>(R.id.cancelButton).setOnClickListener {
                dismiss()
            }

            findViewById<Button>(R.id.sendButton).setOnClickListener {
                val reasonGroup = findViewById<RadioGroup>(R.id.reasonGroup)
                val selectedReasonId = reasonGroup.checkedRadioButtonId

                if (selectedReasonId == -1) {
                    Toast.makeText(this@MaskActivity, "Please select a reason", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val reason = when (selectedReasonId) {
                    R.id.reasonNotWorking -> "not_working"
                    R.id.reasonHarmful -> "harmful"
                    R.id.reasonSpam -> "spam"
                    else -> ""
                }

                val description = findViewById<TextInputEditText>(R.id.descriptionInput)
                    .text.toString()

                buttonContainer.visibility = View.INVISIBLE
                sendProgress.visibility = View.VISIBLE

                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        submitReport(
                            itemType,
                            itemId,
                            reason,
                            description
                        )
                        Toast.makeText(this@MaskActivity, "Report sent successfully", Toast.LENGTH_SHORT).show()
                        dismiss()
                    } catch (e: Exception) {
                        Log.e("MaskActivity", "Error submitting report", e)
                        Toast.makeText(this@MaskActivity, "Error sending report", Toast.LENGTH_SHORT).show()
                        buttonContainer.visibility = View.VISIBLE
                        sendProgress.visibility = View.GONE
                    }
                }
            }
        }

        reportDialog.show()
    }

    private suspend fun submitReport(
        itemType: String,
        itemId: String,
        reason: String,
        description: String
    ) = withContext(Dispatchers.IO) {
        val client = OkHttpClient()

        val reportData = JSONObject().apply {
            put("reportedItemType", itemType)
            put("reportedItemId", itemId)
            put("reporterGoogleId", GoogleAuthManager.getGoogleAccount()?.id)
            put("reason", reason)
            put("description", description)
        }

        val requestBody = reportData.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://postreport-${Constants.BASE_URL}")
            .post(requestBody)
            .build()

        var success = false
        CoroutineScope(Dispatchers.IO).async {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    success = true
                }
            }
        }.await()
        if (!success) {
            Toast.makeText(this@MaskActivity, "Error sending report", Toast.LENGTH_SHORT).show()
        }
    }
}