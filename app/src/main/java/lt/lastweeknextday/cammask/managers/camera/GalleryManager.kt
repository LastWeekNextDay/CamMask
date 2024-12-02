package lt.lastweeknextday.cammask.managers.camera

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.widget.ImageButton
import android.widget.Toast

class GalleryManager(private val context: Context) {
    private var latestMediaUri: Uri? = null
    private var currentThumbnail: Bitmap? = null

    private fun getLatestMediaThumbnail(): Bitmap? {
        try {
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.MIME_TYPE
            )

            val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?) AND " +
                    "${MediaStore.Files.FileColumns.MIME_TYPE} IS NOT NULL"

            val selectionArgs = arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
            )

            val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
            val queryUri = MediaStore.Files.getContentUri("external")

            context.contentResolver.query(
                queryUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val mediaTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                    val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)

                    val id = cursor.getLong(idColumn)
                    val mediaType = cursor.getInt(mediaTypeColumn)
                    val mimeType = cursor.getString(mimeTypeColumn)

                    if (mimeType != null) {
                        latestMediaUri = when (mediaType) {
                            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> {
                                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                            }
                            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> {
                                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                            }
                            else -> null
                        }

                        if (latestMediaUri != null) {
                            try {
                                return context.contentResolver.loadThumbnail(
                                    latestMediaUri!!,
                                    Size(200, 200),
                                    null
                                )
                            } catch (e: Exception) {
                                Log.e("GalleryManager", "Error loading thumbnail", e)
                                return null
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GalleryManager", "Error querying media", e)
        }

        latestMediaUri = null
        return null
    }

    fun openGallery(activity: Activity) {
        try {
            val intent = Intent().apply {
                if (latestMediaUri != null) {
                    action = Intent.ACTION_VIEW
                    data = latestMediaUri
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                } else {
                    action = Intent.ACTION_MAIN
                    addCategory(Intent.CATEGORY_APP_GALLERY)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }

            try {
                activity.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                val fallbackIntent = Intent().apply {
                    action = Intent.ACTION_VIEW
                    type = "image/*"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                activity.startActivity(fallbackIntent)
            }
        } catch (e: Exception) {
            try {
                val finalFallbackIntent = Intent().apply {
                    action = Intent.ACTION_VIEW
                    data = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                activity.startActivity(finalFallbackIntent)
            } catch (e: Exception) {
                Log.e("GalleryManager", "Could not open gallery", e)
                Toast.makeText(context, "Could not open gallery", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateGalleryButtonThumbnail(button: ImageButton) {
        currentThumbnail?.recycle()
        currentThumbnail = null

        getLatestMediaThumbnail()?.let { thumbnail ->
            currentThumbnail = getRoundedBitmap(thumbnail)
            button.setImageBitmap(currentThumbnail)
        } ?: run {
            button.setImageBitmap(null)
            latestMediaUri = null
        }
    }

    private fun getRoundedBitmap(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(
            bitmap.width,
            bitmap.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(output)
        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
        }
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        canvas.drawCircle(
            bitmap.width / 2f,
            bitmap.height / 2f,
            bitmap.width / 2f,
            paint
        )
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }

    fun cleanup() {
        currentThumbnail?.recycle()
        currentThumbnail = null
        latestMediaUri = null
    }
}