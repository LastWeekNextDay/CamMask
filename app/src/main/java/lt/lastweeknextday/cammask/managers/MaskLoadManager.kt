package lt.lastweeknextday.cammask.managers

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import lt.lastweeknextday.cammask.misc.Constants
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class MaskLoadManager {
    private val client = OkHttpClient()

    suspend fun loadMasks(limit: Int = 6, lastId: Int? = null,
                          orderBy: String = "ratingsCount", orderDirection: String = "desc",
                          filterTags: List<String> = emptyList()): Pair<List<JSONObject>, String?> {
        try {
            var url = "https://getmasks-${Constants.BASE_URL}/?limit=$limit"
            if (lastId != null) {
                url += "&lastId=$lastId"
            }
            if (orderBy.isNotEmpty()) {
                url += "&orderBy=$orderBy"
            }
            if (orderDirection.isNotEmpty()) {
                url += "&orderDirection=$orderDirection"
            }

            Log.d("MaskLoader", "Loading masks with URL: $url")

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            var pair = Pair<List<JSONObject>, String?>(emptyList(), null)

            CoroutineScope(Dispatchers.IO).async {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("Failed to load masks: ${response.code}")
                    }

                    val jsonResponse = JSONObject(response.body!!.string())
                    val masksArray = jsonResponse.getJSONArray("masks")
                    val masks = mutableListOf<JSONObject>()

                    Log.d("MaskLoader", "Received ${masksArray.length()} masks")

                    for (i in 0 until masksArray.length()) {
                        if (filterTags.isNotEmpty()) {
                            val maskTags = masksArray.getJSONObject(i).getString("tags")
                                .trim('[', ']').split(",").map { it.trim() }
                            if (!maskTags.containsAll(filterTags)) {
                                continue
                            } else {
                                masks.add(masksArray.getJSONObject(i))
                            }
                        } else {
                            masks.add(masksArray.getJSONObject(i))
                        }
                    }

                    val nextId = if (!jsonResponse.isNull("lastId")) {
                        jsonResponse.getString("lastId")
                    } else null

                    Log.d("MaskLoader", "Next ID: $nextId")
                    pair = Pair(masks, nextId)
                }
            }.await()

            return pair
        } catch (e: Exception) {
            Log.e("MaskLoader", "Error loading masks", e)
            throw e
        }
    }
}