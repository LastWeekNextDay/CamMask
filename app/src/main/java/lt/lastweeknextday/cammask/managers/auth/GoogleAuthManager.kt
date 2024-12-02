package lt.lastweeknextday.cammask.managers.auth

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lt.lastweeknextday.cammask.data.UserData
import lt.lastweeknextday.cammask.misc.Constants
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

object GoogleAuthManager {
    private val _isLoggedIn = MutableLiveData<Boolean>()
    val isLoggedIn: LiveData<Boolean> = _isLoggedIn

    private val _userData = MutableLiveData<UserData?>()

    private var googleAccount: GoogleSignInAccount? = null
    private val client = OkHttpClient()

    init {
        _isLoggedIn.value = false
        _userData.value = null
    }

    suspend fun doLogin(acc: GoogleSignInAccount) {
        Log.d("GoogleAuthManager", "setLoggedIn: Account ID: ${acc.id}")
        val id = acc.id ?: throw Exception("Google ID is null")
        val name = acc.displayName ?: throw Exception("Display name is null")
        val photoUrl = acc.photoUrl?.toString() ?: ""

        try {
            var user = fetchUserData(id)
            if (user == null) {
                createUser(id, name, photoUrl)
                user = fetchUserData(id)
                if (user == null) {
                    throw Exception("Failed to create and fetch user")
                }
                _userData.postValue(user)
            } else {
                _userData.postValue(user)
            }

            googleAccount = acc
            _isLoggedIn.postValue(true)
        } catch (e: Exception) {
            Log.e("GoogleAuthManager", "Error during login", e)
            logout()
            return
        }

        _isLoggedIn.value = true
    }

    private suspend fun createUser(googleId: String, displayName: String, photoUrl: String) = withContext(Dispatchers.IO) {
        var json = JSONObject().put("googleId", googleId)
        json = json.put("name", displayName)
        json.put("photoUrl", photoUrl)

        val body = json.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://createuser-${Constants.BASE_URL}")
            .post(body)
            .build()

        client.newCall(request).execute()
    }

    private suspend fun fetchUserData(googleId: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://getuser-${Constants.BASE_URL}/?googleId=$googleId")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body!!.string()
            if (responseBody.isBlank()) {
                return@withContext null
            }

            val json = JSONObject(responseBody)
            if (json.length() == 0) {
                return@withContext null
            }

            return@withContext parseUserData(json)
        }
    }

    private fun parseUserData(json: JSONObject): UserData {
        return UserData(
            id = json.getString("id"),
            name = json.getString("name"),
            photoUrl = json.getString("photoUrl"),
            canComment = json.getBoolean("canComment"),
            canUpload = json.getBoolean("canUpload"),
            creationDate = json.getString("creationDate"),
            lastAccess = json.getString("lastAccess")
        )
    }

    fun logout() {
        Log.d("GoogleAuthManager", "logout: Logging out")
        googleAccount = null
        _userData.value = null
        _isLoggedIn.value = false
    }

    fun getGoogleAccount(): GoogleSignInAccount? = googleAccount

    suspend fun checkIfCanUpload() = withContext(Dispatchers.IO){
        if (googleAccount == null) throw Exception("User not logged in")
        val user = fetchUserData(googleAccount!!.id!!)
        _userData.postValue(user)
        return@withContext _userData.value?.canUpload ?: false
    }

    suspend fun checkIfCanComment() = withContext(Dispatchers.IO) {
        if (googleAccount == null) throw Exception("User not logged in")
        val user = _userData.value ?: throw Exception("User not logged in")
        _userData.postValue(user)
        return@withContext _userData.value?.canComment ?: false
    }

    fun checkIfLoggedIn(): Boolean {
        return _isLoggedIn.value ?: false
    }
}