package lt.lastweeknextday.cammask.ui.objects

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import com.bumptech.glide.Glide
import com.google.android.gms.common.SignInButton
import lt.lastweeknextday.cammask.R
import lt.lastweeknextday.cammask.activities.BaseActivity
import lt.lastweeknextday.cammask.managers.auth.GoogleAuthManager
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class AccountPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private lateinit var accountContainer: LinearLayout
    private lateinit var menuHandle: View
    private lateinit var menuHandleVisible: View
    private lateinit var userImage: ImageView
    private lateinit var userName: TextView
    private lateinit var userEmail: TextView
    private lateinit var signInButton: SignInButton
    private lateinit var signOutButton: Button
    private lateinit var signInProgress: ProgressBar

    private var initialX = 0f
    private var initialTranslationX = 0f
    private var isPanelOpen = false
    private var isDragging = false
    private var isLoggingInOut = false

    init {
        LayoutInflater.from(context).inflate(R.layout.account_panel, this, true)
        setupViews()
        setupObservers()
    }

    private fun setupViews() {
        accountContainer = findViewById(R.id.accountPanelContainer)
        menuHandle = findViewById(R.id.accountHandle)
        menuHandleVisible = findViewById(R.id.accountHandleVisible)
        userImage = findViewById(R.id.userImage)
        userName = findViewById(R.id.userName)
        userEmail = findViewById(R.id.userEmail)
        signInButton = findViewById(R.id.signInButton)
        signOutButton = findViewById(R.id.signOutButton)
        signInProgress = findViewById(R.id.signInProgress)

        signInButton.setOnClickListener {
            if (!isLoggingInOut) {
                setLoginLoadingState(true)
                (context as? BaseActivity)?.googleSignInManager?.initiateSignIn()
            }
        }

        signOutButton.setOnClickListener {
            if (!isLoggingInOut) {
                setLoginLoadingState(true)
                (context as? BaseActivity)?.googleSignInManager?.signOut()
            }
        }
    }

    private fun setLoginLoadingState(loading: Boolean) {
        isLoggingInOut = loading
        if (loading) {
            signInButton.visibility = View.INVISIBLE
            signOutButton.visibility = View.INVISIBLE
            signInProgress.visibility = View.VISIBLE
        } else {
            signInProgress.visibility = View.GONE
        }
    }

    private fun setupObservers() {
        GoogleAuthManager.isLoggedIn.observe(context as LifecycleOwner) { isLoggedIn ->
            setLoginLoadingState(false)
            signInButton.visibility = if (isLoggedIn) View.GONE else View.VISIBLE
            signOutButton.visibility = if (isLoggedIn) View.VISIBLE else View.GONE

            if (isLoggedIn) {
                Log.d("AccountPanel", "User is logged in")
                GoogleAuthManager.getGoogleAccount()?.let { account ->
                    Log.d("AccountPanel", "User ID: ${account.id}")
                    Log.d("AccountPanel", "User name: ${account.displayName}")
                    Log.d("AccountPanel", "User email: ${account.email}")
                    Log.d("AccountPanel", "User photo URL: ${account.photoUrl}")

                    userName.text = account.displayName
                    userEmail.text = account.email
                    if (account.photoUrl != null) {
                        Glide.with(context)
                            .load(account.photoUrl)
                            .circleCrop()
                            .into(userImage)
                    } else {
                        userImage.setImageResource(R.drawable.default_pic)
                    }
                }
            } else {
                userName.text = ""
                userEmail.text = ""
                userImage.setImageDrawable(null)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val handleBounds = menuHandle.let {
                    val location = IntArray(2)
                    it.getLocationOnScreen(location)
                    val left = location[0]
                    val right = left + it.width
                    val top = location[1]
                    val bottom = top + it.height
                    event.x >= left && event.x <= right && event.y >= top && event.y <= bottom
                }

                if (handleBounds) {
                    initialX = event.x
                    initialTranslationX = accountContainer.translationX
                    isDragging = true
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val dx = event.x - initialX
                    updatePanelPosition(initialTranslationX + dx)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    val dx = event.x - initialX
                    if (abs(dx) > dpToPx(50)) {
                        if (dx < 0) {
                            animatePanel(accountContainer.translationX, -dpToPx(240))
                            isPanelOpen = true
                        } else {
                            animatePanel(accountContainer.translationX, 0f)
                            isPanelOpen = false
                        }
                    } else {
                        animatePanel(accountContainer.translationX, if (isPanelOpen) -dpToPx(240) else 0f)
                    }
                    isDragging = false
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updatePanelPosition(position: Float) {
        val constrainedPosition = max(-dpToPx(240), min(0f, position))
        accountContainer.translationX = constrainedPosition
        menuHandle.translationX = constrainedPosition
        menuHandleVisible.translationX = constrainedPosition
    }

    private fun animatePanel(from: Float, to: Float) {
        ValueAnimator.ofFloat(from, to).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                updatePanelPosition(animation.animatedValue as Float)
            }
            start()
        }
    }

    private fun dpToPx(dp: Int): Float {
        return dp * resources.displayMetrics.density
    }
}