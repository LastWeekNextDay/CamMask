package lt.lastweeknextday.cammask.adapters

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import androidx.recyclerview.widget.RecyclerView
import lt.lastweeknextday.cammask.R

class ImageSelectionAdapter(
    private var images: List<Uri>,
    private var selectedPosition: Int = 0,
    private val onPrimarySelected: (Int) -> Unit
) : RecyclerView.Adapter<ImageSelectionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imagePreview)
        val radioButton: RadioButton = view.findViewById(R.id.primarySelector)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.image_selection_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.imageView.setImageURI(images[position])
        holder.radioButton.isChecked = position == selectedPosition

        holder.radioButton.setOnClickListener {
            selectImage(position)
        }

        holder.imageView.setOnClickListener {
            selectImage(position)
        }
    }

    override fun getItemCount() = images.size

    fun updateImages(newImages: List<Uri>) {
        images = newImages
        selectedPosition = 0
        notifyDataSetChanged()
    }

    private fun selectImage(position: Int){
        val oldPosition = selectedPosition
        selectedPosition = position
        notifyItemChanged(oldPosition)
        notifyItemChanged(selectedPosition)
        onPrimarySelected(position)
    }
}