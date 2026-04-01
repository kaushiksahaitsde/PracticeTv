package com.example.mytvxml

import android.os.Bundle
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter


class ListFragment : RowsSupportFragment() {

    private var rootAdapter: ArrayObjectAdapter= ArrayObjectAdapter(ListRowPresenter(FocusHighlight.ZOOM_FACTOR_MEDIUM))


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
       adapter= rootAdapter

    }

    fun bindData(dataList: DataModel) {
        rootAdapter.clear()
        dataList.result.forEach { result ->
            val arrayObjectAdapter = ArrayObjectAdapter(ItemPresenter())

            result.details.forEach {
                arrayObjectAdapter.add(it)
            }

            val headerItem = HeaderItem(result.title)
            val listRow = ListRow(headerItem, arrayObjectAdapter)
            rootAdapter.add(listRow)
        }
    }



}