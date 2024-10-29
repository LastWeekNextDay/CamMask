package lt.lastweeknextday.cammask

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.TextView

@SuppressLint("InflateParams")
class LoadingDialog(context: Context) {
    private val dialog: Dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)
    private var messageText: TextView
    private var backgroundView: View

    init {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }
        dialog.setCancelable(false)

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_loading, null)
        messageText = view.findViewById(R.id.loadingText)
        backgroundView = view.findViewById(R.id.dialogBackground)

        dialog.setContentView(view)
    }

    fun show(message: String = "Loading...", transparentBackground: Boolean = false) {
        messageText.text = message
        backgroundView.setBackgroundColor(
            if (transparentBackground) Color.TRANSPARENT
            else Color.parseColor("#80000000")
        )
        dialog.show()
    }

    fun hide() {
        dialog.dismiss()
    }

    fun isShowing(): Boolean {
        return dialog.isShowing
    }
}