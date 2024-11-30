package lt.lastweeknextday.cammask

import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {
    lateinit var googleSignInManager: GoogleSignInManager
    private lateinit var accountPanel: AccountPanel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        googleSignInManager = GoogleSignInManager(this)
    }

    override fun setContentView(layoutResID: Int) {
        val rootLayout = FrameLayout(this)
        layoutInflater.inflate(layoutResID, rootLayout, true)

        accountPanel = AccountPanel(this)
        rootLayout.addView(accountPanel)

        super.setContentView(rootLayout)
    }
}