package lt.lastweeknextday.cammask.adapters

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import lt.lastweeknextday.cammask.R
import org.json.JSONObject

class MaskListAdapter(private val onMaskSelected: (JSONObject) -> Unit,
                      private val onMaskUnselected: (JSONObject) -> Unit,
                      private val onMaskClicked: (JSONObject) -> Unit)
    : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val masksList = mutableListOf<JSONObject>()
    private val loadedIds = mutableSetOf<Int>()
    private var isLoading = false
    private var lastId: String? = null
    private var lastSnapshot: String? = null
    private var hasMoreItems = true
    private var selectedMaskId: Int? = null

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_LOADING = 1
    }

    inner class MaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val maskImage: ImageView = itemView.findViewById(R.id.maskImage)
        private val maskName: TextView = itemView.findViewById(R.id.maskName)
        private val ratingText: TextView = itemView.findViewById(R.id.ratingText)
        private val tagText: TextView = itemView.findViewById(R.id.tagText)
        private val cardView: CardView = itemView as CardView
        private val starViews = listOf<ImageView>(
            itemView.findViewById(R.id.star1),
            itemView.findViewById(R.id.star2),
            itemView.findViewById(R.id.star3),
            itemView.findViewById(R.id.star4),
            itemView.findViewById(R.id.star5)
        )
        private var longPressHandler: Runnable? = null
        private var isLongPress = false

        @SuppressLint("ClickableViewAccessibility")
        fun bind(mask: JSONObject) {
            try {
                maskName.text = mask.getString("maskName")
                val rating = mask.getDouble("averageRating")
                val ratingCount = mask.getInt("ratingsCount")
                ratingText.text = "${String.format("%.1f", rating)} ($ratingCount)"

                val tagsRaw = mask.getString("tags")
                val tags = tagsRaw.trim('[', ']').split(",").map { it.trim() }
                tagText.text = tags.joinToString(", ")

                val fullStars = rating.toInt()
                starViews.forEachIndexed { index, star ->
                    star.setImageResource(if (index < fullStars) R.drawable.star_filled else R.drawable.star_empty)
                }

                val isSelected = mask.getInt("id") == selectedMaskId
                if (isSelected) {
                    cardView.setBackgroundResource(R.drawable.selected_card_background)
                } else {
                    cardView.setBackgroundResource(R.drawable.default_card_background)
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

                itemView.setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            isLongPress = false
                            longPressHandler = Runnable {
                                isLongPress = true
                                if (isSelected) {
                                    selectedMaskId = null
                                    onMaskUnselected(mask)
                                } else {
                                    selectedMaskId = mask.getInt("id")
                                    notifyDataSetChanged()
                                    onMaskSelected(mask)
                                }
                            }
                            view.postDelayed(longPressHandler, 1000)
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            longPressHandler?.let { view.removeCallbacks(it) }
                            if (!isLongPress) {
                                onMaskClicked(mask)
                            }
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            longPressHandler?.let { view.removeCallbacks(it) }
                            true
                        }
                        else -> false
                    }
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

                if (id == selectedMaskId) {
                    onMaskSelected(mask)
                }
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

    fun updateMask(updatedMask: JSONObject) {
        val maskId = updatedMask.getInt("id")
        val position = masksList.indexOfFirst {
            it.getInt("id") == maskId
        }

        if (position != -1) {
            masksList[position] = updatedMask
            notifyItemChanged(position)
        }
    }

    fun clearSelection() {
        selectedMaskId = null
        notifyDataSetChanged()
    }

    fun clearAllButSelection() {
        masksList.clear()
        loadedIds.clear()
        lastId = null
        lastSnapshot = null
        hasMoreItems = true
        notifyDataSetChanged()
    }

    fun clearAll(){
        masksList.clear()
        loadedIds.clear()
        lastId = null
        lastSnapshot = null
        hasMoreItems = true
        selectedMaskId = null
        notifyDataSetChanged()
    }

    fun getLastSnapshot() = lastSnapshot
    fun isLoading() = isLoading
    fun hasMore() = hasMoreItems
    fun getLastId() = lastId
}