package com.example.mytvxml

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide

class ItemPresenter : Presenter() {

    companion object {
        private const val ITEM_SIZE_PERCENT = 12
    }
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_view, parent, false)

        val params = view.layoutParams
        params.width = getWidthPercent(parent.context)
        params.height = getHeightPercent(parent.context)

        return ViewHolder(view)
    }

    private fun getWidthPercent(context: Context): Int {
        val width = context.resources.displayMetrics.widthPixels
        return (width * ITEM_SIZE_PERCENT) / 100
    }

    private fun getHeightPercent(context: Context): Int {
        val height = context.resources.displayMetrics.heightPixels
        return (height * ITEM_SIZE_PERCENT) / 100
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val content = item as? DataModel.Result.Detail ?: return
        val imageView = viewHolder.view.findViewById<ImageView>(R.id.poster_image) ?: return

        val posterPath = content.poster_path
        if (posterPath.isBlank()) {
            imageView.setImageDrawable(null)
            return
        }

        val url = "https://image.tmdb.org/t/p/w500$posterPath"
        Glide.with(viewHolder.view.context)
            .load(url)
            .into(imageView)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val imageView = viewHolder.view.findViewById<ImageView>(R.id.poster_image)
        if (imageView != null) {
            Glide.with(viewHolder.view.context).clear(imageView)
            imageView.setImageDrawable(null)
        }
    }
}
