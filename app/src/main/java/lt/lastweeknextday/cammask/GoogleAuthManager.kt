package lt.lastweeknextday.cammask

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.auth.api.signin.GoogleSignInAccount

object GoogleAuthManager {
    private val _isLoggedIn = MutableLiveData<Boolean>()
    val isLoggedIn: LiveData<Boolean> = _isLoggedIn

    private var googleAccount: GoogleSignInAccount? = null

    init {
        _isLoggedIn.value = false
    }

    fun setLoggedIn(acc: GoogleSignInAccount) {
        Log.d("GoogleAuthManager", "setLoggedIn: Account ID: ${acc.id}")
        googleAccount = acc
        _isLoggedIn.value = true
    }

    fun logout() {
        Log.d("GoogleAuthManager", "logout: Logging out")
        googleAccount = null
        _isLoggedIn.value = false
    }


    fun getGoogleAccount(): GoogleSignInAccount? = googleAccount
}