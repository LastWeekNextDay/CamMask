package lt.lastweeknextday.cammask

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class ImageCarouselAdapter(private val images: List<String>) :
    RecyclerView.Adapter<ImageCarouselAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.carouselImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.carousel_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        Glide.with(holder.imageView)
            .load(images[position])
            .centerCrop()
            .into(holder.imageView)
    }

    override fun getItemCount() = images.size
}

class CommentsAdapter : RecyclerView.Adapter<CommentsAdapter.ViewHolder>() {
    private val comments = mutableListOf<JSONObject>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userImage: ImageView = view.findViewById(R.id.userImage)
        val userName: TextView = view.findViewById(R.id.userName)
        val commentText: TextView = view.findViewById(R.id.commentText)
        val commentDate: TextView = view.findViewById(R.id.commentDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.comment_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val comment = comments[position]

        // Load user data
        val googleId = comment.getString("googleId")
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://getuser-${Constants.BASE_URL}/?googleId=$googleId")
            .get()
            .build()

        holder.itemView.post {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val userData = JSONObject(response.body!!.string())
                        holder.itemView.post {
                            holder.userName.text = userData.getString("name")
                            Glide.with(holder.userImage)
                                .load(userData.getString("photoUrl"))
                                .circleCrop()
                                .into(holder.userImage)
                        }
                    }
                }
            } catch (e: Exception) {
                holder.userName.text = "Unknown User"
            }
        }

        holder.commentText.text = comment.getString("comment")
        holder.commentDate.text = dateFormat.format(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                .parse(comment.getString("postedOn"))
        )
    }

    override fun getItemCount() = comments.size

    fun updateComments(newComments: JSONArray) {
        comments.clear()
        for (i in 0 until newComments.length()) {
            comments.add(newComments.getJSONObject(i))
        }
        notifyDataSetChanged()
    }
}