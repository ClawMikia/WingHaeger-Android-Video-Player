package com.winghaeger.app.ui

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager
import com.winghaeger.app.R
import com.winghaeger.app.databinding.ItemVideoCardBinding
import com.winghaeger.app.databinding.ItemLibraryHeaderBinding
import com.winghaeger.app.util.FormatUtils

class LibraryAdapter(
    private val onHeaderClick: ((String) -> Unit)? = null,
    private val onRemove: ((Long) -> Unit)? = null,
    private val onOpen: (videoId: Long, playlistIds: LongArray, index: Int) -> Unit
) : ListAdapter<LibraryListItem, RecyclerView.ViewHolder>(DIFF) {

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_VIDEO  = 1
        val DIFF = object : DiffUtil.ItemCallback<LibraryListItem>() {
            override fun areItemsTheSame(a: LibraryListItem, b: LibraryListItem) = when {
                a is LibraryListItem.Header && b is LibraryListItem.Header -> a.folderName == b.folderName
                a is LibraryListItem.VideoRow && b is LibraryListItem.VideoRow -> a.entity.id == b.entity.id
                else -> false
            }
            override fun areContentsTheSame(a: LibraryListItem, b: LibraryListItem) = a == b
        }
    }

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is LibraryListItem.Header -> TYPE_HEADER
        is LibraryListItem.VideoRow -> TYPE_VIDEO
    }

    fun spanSizeLookup() = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(pos: Int) = if (getItemViewType(pos) == TYPE_HEADER) 2 else 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER)
            HeaderVH(ItemLibraryHeaderBinding.inflate(inflater, parent, false))
        else
            VideoVH(ItemVideoCardBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is LibraryListItem.Header -> (holder as HeaderVH).bind(item)
            is LibraryListItem.VideoRow -> (holder as VideoVH).bind(item)
        }
    }

    inner class HeaderVH(val b: ItemLibraryHeaderBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: LibraryListItem.Header) {
            b.tvHeaderTitle.text = item.folderName
            b.tvHeaderCount.text = "${item.count}"
            b.tvExpandIndicator.text = if (item.expanded) "▼" else "▶"
            b.root.setOnClickListener { onHeaderClick?.invoke(item.folderName) }
        }
    }

    inner class VideoVH(val b: ItemVideoCardBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: LibraryListItem.VideoRow) {
            val e = item.entity
            b.tvTitle.text = e.title
            b.tvFolder.text = e.folderGroup
            b.tvDuration.text = if (e.durationMs > 0) FormatUtils.formatDuration(e.durationMs) else "—"
            if (e.durationMs > 0 && e.positionMs > 0) {
                b.progressBar.progress = ((e.positionMs * 100L) / e.durationMs).toInt().coerceIn(0, 100)
            } else {
                b.progressBar.progress = 0
            }
            if (e.thumbnail != null) {
                val bmp = BitmapFactory.decodeByteArray(e.thumbnail, 0, e.thumbnail.size)
                b.thumbnail.setImageBitmap(bmp)
            } else {
                b.thumbnail.setImageResource(android.R.drawable.ic_media_play)
            }
            b.root.setOnClickListener {
                val all = currentList.filterIsInstance<LibraryListItem.VideoRow>()
                val idx = all.indexOfFirst { it.entity.id == e.id }
                val ids = all.map { it.entity.id }.toLongArray()
                onOpen(e.id, ids, idx.coerceAtLeast(0))
            }
            b.root.setOnLongClickListener {
                onRemove?.invoke(e.id)
                true
            }
        }
    }
}
