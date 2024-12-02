package lt.lastweeknextday.cammask.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.common.SignInButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
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
    private lateinit var commentsAdapter: CommentsAdapter
    private var currentRating: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mask)

        val maskJson = intent.getStringExtra("maskData") ?: run {
            finish()
            return
        }

        try {
            maskData = JSONObject(maskJson)
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

    private fun setupViews() {
        findViewById<TextView>(R.id.maskName).text = maskData.getString("maskName")
        findViewById<TextView>(R.id.maskDescription).text =
            maskData.optString("description", "No description available")

        val imagesList = maskData.getString("images")
            .trim('[', ']')
            .split(",")
            .map { it.trim() }

        val viewPager = findViewById<ViewPager2>(R.id.imageCarousel)
        viewPager.adapter = ImageCarouselAdapter(imagesList)

        updateRatingDisplay(
            maskData.getDouble("averageRating"),
            maskData.getInt("ratingsCount")
        )

        val commentsList = findViewById<RecyclerView>(R.id.commentsList)
        commentsAdapter = CommentsAdapter()
        commentsList.layoutManager = LinearLayoutManager(this)
        commentsList.adapter = commentsAdapter

        setupCommentSubmission()
    }

    private fun setupCommentSubmission() {
        val commentSection = findViewById<LinearLayout>(R.id.commentSubmissionSection)
        val loginButton = findViewById<SignInButton>(R.id.loginButton)
        val commentInput = findViewById<EditText>(R.id.commentInput)
        val submitButton = findViewById<Button>(R.id.submitComment)
        val ratingStars = findViewById<LinearLayout>(R.id.ratingStars)
        val userRatingStars = findViewById<LinearLayout>(R.id.userRatingStars)

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
            googleSignInManager.initiateSignIn()
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
                            updateRatingStars()
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
                submitted = true
            }
        }.await()
        if (!allowed) {
            Toast.makeText(this, "You cannot do this task", Toast.LENGTH_SHORT).show()
        } else if (!submitted) {
            Toast.makeText(this, "Error submitting rating", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun submitComment(comment: String) {
        var submitted = false
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
                            updateRatingDisplay(
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
                        updateRatingStars()
                    } else {
                        Log.e("MaskActivity", "Failed to load user rating: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e("MaskActivity", "Error loading user rating", e)
            }
        }.await()
    }

    private fun updateRatingDisplay(rating: Double, count: Int) {
        findViewById<TextView>(R.id.ratingText).text =
            String.format("%.1f (%d ratings)", rating, count)

        val starsContainer = findViewById<LinearLayout>(R.id.ratingStars)
        val fullStars = rating.toInt()

        for (i in 0 until 5) {
            (starsContainer.getChildAt(i) as ImageView)
                .setImageResource(if (i < fullStars) R.drawable.star_filled else R.drawable.star_empty)
        }
    }

    private fun updateRatingStars() {
        val userRatingStars = findViewById<LinearLayout>(R.id.userRatingStars)
        for (i in 0 until 5) {
            (userRatingStars.getChildAt(i) as ImageView)
                .setImageResource(if (i < currentRating) R.drawable.star_filled else R.drawable.star_empty)
        }
    }

    private fun setupObservers() {
        GoogleAuthManager.isLoggedIn.observe(this) { isLoggedIn ->
            updateSubmissionUIState()
            if (isLoggedIn) {
                CoroutineScope(Dispatchers.Main).launch {
                    loadUserRating()
                }
            } else {
                currentRating = 0
                updateRatingStars()
            }
        }
    }
}