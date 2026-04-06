package com.example.mytvxml

import android.os.Bundle
import android.view.View
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter

class ListFragment : RowsSupportFragment() {

    private var itemSelectedListener: ((DataModel.Result.Detail) -> Unit)? = null
    private var rootAdapter: ArrayObjectAdapter =
        ArrayObjectAdapter(ListRowPresenter(FocusHighlight.ZOOM_FACTOR_MEDIUM))

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = rootAdapter
        onItemViewSelectedListener = ItemViewSelectedListener()
    }

    fun bindData(dataList: DataModel) {
        dataList.result.forEachIndexed { _, result ->
            val arrayObjectAdapter = ArrayObjectAdapter(ItemPresenter())
            result.details.forEach { arrayObjectAdapter.add(it) }
            rootAdapter.add(ListRow(HeaderItem(result.title), arrayObjectAdapter))
        }
    }

    fun setOnContentSelectedListener(listener: (DataModel.Result.Detail) -> Unit) {
        this.itemSelectedListener = listener
    }

    inner class ItemViewSelectedListener : OnItemViewSelectedListener {
        override fun onItemSelected(
            itemViewHolder: Presenter.ViewHolder?,
            item: Any?,
            rowViewHolder: RowPresenter.ViewHolder?,
            row: Row?
        ) {
            if (item is DataModel.Result.Detail) {
                itemSelectedListener?.invoke(item)
            }
        }
    }
}
