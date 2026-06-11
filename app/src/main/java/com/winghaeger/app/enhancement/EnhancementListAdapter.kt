package com.winghaeger.app.enhancement

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.winghaeger.app.data.EnhancementMode
import com.winghaeger.app.databinding.ItemEnhancementRowBinding

class EnhancementListAdapter(
    private val selected: EnhancementMode,
    private val onSelect: (EnhancementMode) -> Unit
) : RecyclerView.Adapter<EnhancementListAdapter.VH>() {

    private val modes = EnhancementMode.entries

    inner class VH(val b: ItemEnhancementRowBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(mode: EnhancementMode) {
            b.tvName.text = mode.displayName
            b.ivCheck.visibility = if (mode == selected) android.view.View.VISIBLE else android.view.View.GONE
            b.root.setOnClickListener { onSelect(mode) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemEnhancementRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(modes[position])
    override fun getItemCount() = modes.size
}
