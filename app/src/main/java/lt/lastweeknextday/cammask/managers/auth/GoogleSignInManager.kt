package lt.lastweeknextday.cammask.managers.auth

import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GoogleSignInManager(private val activity: AppCompatActivity) {
    private val activityResultLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (result.resultCode != AppCompatActivity.RESULT_OK) {
                    Log.d("GoogleSignInManager", "Sign in failed with result code: ${result.resultCode}")
                    GoogleAuthManager.logout()
                    return@launch
                }

                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                handleSignInTask(task.result)
            } catch (e: Exception) {
                Log.e("GoogleSignInManager", "Sign in failed", e)
                GoogleAuthManager.logout()
            }
        }
    }

    private var googleSignInClient: GoogleSignInClient

    init {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestId()
            .build()

        googleSignInClient = GoogleSignIn.getClient(activity, options)
    }

    fun initiateSignIn() {
        Log.d("GoogleSignInManager", "initiateSignIn: Initiating sign in")
        activityResultLauncher.launch(googleSignInClient.signInIntent)
    }

    private suspend fun handleSignInTask(account: GoogleSignInAccount) = withContext(Dispatchers.Main) {
        Log.d("GoogleSignInManager", "handleSignInTask: Account ID: ${account.id}")
        if (account.id == null) {
            Log.d("GoogleSignInManager", "handleSignInTask: Account ID is null")
            GoogleAuthManager.logout()
            return@withContext
        }

        GoogleAuthManager.doLogin(account)
    }

    fun signOut() {
        googleSignInClient.signOut().addOnCompleteListener {
            GoogleAuthManager.logout()
        }
    }
}