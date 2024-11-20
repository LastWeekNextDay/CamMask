package lt.lastweeknextday.cammask

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SlideMenuLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var initialX = 0f
    private var initialTranslationX = 0f
    private lateinit var slideMenu: ViewGroup
    private lateinit var menuHandle: View
    private lateinit var menuHandleVisible: View
    private var isMenuOpen = false
    private var isDragging = false

    override fun onFinishInflate() {
        super.onFinishInflate()
        slideMenu = findViewById(R.id.slideMenuContainer)
        menuHandle = findViewById(R.id.menuHandle)
        menuHandleVisible = findViewById(R.id.menuHandleVisible)
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
                    initialTranslationX = slideMenu.translationX
                    isDragging = true
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val dx = event.x - initialX
                    updateMenuPosition(initialTranslationX + dx)
                    menuHandle.translationX = slideMenu.translationX
                    menuHandleVisible.alpha = 1 - slideMenu.translationX / dpToPx(240)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    val dx = event.x - initialX
                    if (abs(dx) > dpToPx(50)) {
                        if (dx > 0) {
                            animateMenu(slideMenu.translationX, dpToPx(240))
                            isMenuOpen = true
                        } else {
                            animateMenu(slideMenu.translationX, 0f)
                            isMenuOpen = false
                        }
                    } else {
                        animateMenu(slideMenu.translationX, if (isMenuOpen) dpToPx(240) else 0f)
                    }
                    isDragging = false
                    if (isMenuOpen){
                        menuHandleVisible.visibility = View.GONE
                    } else {
                        menuHandleVisible.visibility = View.VISIBLE
                        menuHandleVisible.alpha = 1f
                    }
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateMenuPosition(position: Float) {
        val constrainedPosition = min(dpToPx(240), max(0f, position))
        slideMenu.translationX = constrainedPosition
        menuHandle.translationX = constrainedPosition
        menuHandleVisible.translationX = constrainedPosition
    }

    private fun animateMenu(from: Float, to: Float) {
        ValueAnimator.ofFloat(from, to).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                updateMenuPosition(animation.animatedValue as Float)
            }
            start()
        }
    }

    private fun dpToPx(dp: Int): Float {
        return dp * resources.displayMetrics.density
    }
}