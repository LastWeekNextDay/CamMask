package lt.lastweeknextday.cammask

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.json.JSONArray
import org.json.JSONObject

class MaskListAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val masksList = mutableListOf<JSONObject>()
    private val loadedIds = mutableSetOf<Int>()
    private var isLoading = false
    private var lastId: String? = null
    private var lastSnapshot: String? = null
    private var hasMoreItems = true

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_LOADING = 1
    }

    inner class MaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val maskImage: ImageView = itemView.findViewById(R.id.maskImage)
        private val maskName: TextView = itemView.findViewById(R.id.maskName)
        private val ratingText: TextView = itemView.findViewById(R.id.ratingText)
        private val starViews = listOf<ImageView>(
            itemView.findViewById(R.id.star1),
            itemView.findViewById(R.id.star2),
            itemView.findViewById(R.id.star3),
            itemView.findViewById(R.id.star4),
            itemView.findViewById(R.id.star5)
        )

        fun bind(mask: JSONObject) {
            try {
                maskName.text = mask.getString("maskName")
                val rating = mask.getDouble("averageRating")
                val ratingCount = mask.getInt("ratingsCount")
                ratingText.text = "${String.format("%.1f", rating)} ($ratingCount)"

                val fullStars = rating.toInt()
                starViews.forEachIndexed { index, star ->
                    star.setImageResource(if (index < fullStars) R.drawable.star_filled else R.drawable.star_empty)
                }

                val firstImageUrl = try {
                    val imagesStr = mask.getString("images")
                    Log.d("MaskListAdapter", "Images string: $imagesStr")
                    val imagesList = imagesStr.trim('[', ']').split(",").map { it.trim() }
                    if (imagesList.isNotEmpty()) imagesList[0] else null
                } catch (e: Exception) {
                    Log.e("MaskListAdapter", "Error parsing images: ${e.message}")
                    null
                }

                firstImageUrl?.let { url ->
                    Log.d("MaskListAdapter", "Loading image URL: $url")
                    Glide.with(maskImage.context)
                        .load(url)
                        .centerCrop()
                        .into(maskImage)
                }
            } catch (e: Exception) {
                Log.e("MaskListAdapter", "Error binding mask data", e)
            }
        }
    }

    inner class LoadingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_ITEM -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.mask_list_item, parent, false)
                MaskViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.mask_list_loading_item, parent, false)
                LoadingViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is MaskViewHolder && position < masksList.size) {
            holder.bind(masksList[position])
        }
    }

    override fun getItemCount(): Int {
        return if (isLoading && hasMoreItems) {
            masksList.size + 1
        } else {
            masksList.size
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < masksList.size) VIEW_TYPE_ITEM else VIEW_TYPE_LOADING
    }

    fun addMasks(newMasks: List<JSONObject>, nextId: String?) {
        Log.d("MaskListAdapter", "Adding ${newMasks.size} new masks, next ID: $nextId")

        if (isLoading) {
            isLoading = false
            notifyItemRemoved(masksList.size)
        }

        if (newMasks.isEmpty()) {
            Log.d("MaskListAdapter", "No new masks received, setting hasMoreItems to false")
            hasMoreItems = false
            return
        }

        val startPosition = masksList.size
        var addedCount = 0

        newMasks.forEach { mask ->
            val id = mask.optInt("id", -1)
            if (id != -1 && loadedIds.add(id)) {
                masksList.add(mask)
                addedCount++
            }
        }

        lastId = nextId
        hasMoreItems = addedCount > 0 && nextId != null

        Log.d("MaskListAdapter", "Added $addedCount masks, hasMoreItems: $hasMoreItems")

        if (addedCount > 0) {
            notifyItemRangeInserted(startPosition, addedCount)
        }
    }

    fun loadMore() {
        Log.d("MaskListAdapter", "loadMore called - loading: $isLoading, hasMore: $hasMoreItems, size: ${masksList.size}")
        if (!isLoading && hasMoreItems) {
            isLoading = true
            notifyItemInserted(masksList.size)
        }
    }

    fun getLastSnapshot() = lastSnapshot
    fun isLoading() = isLoading
    fun hasMore() = hasMoreItems
    fun getLastId() = lastId
}