package lt.lastweeknextday.cammask.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import lt.lastweeknextday.cammask.R

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