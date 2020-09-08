/*
 * Copyright 2020 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixmultiangleondemand.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.phenixrts.suite.phenixmultiangleondemand.databinding.RowStreamItemBinding
import com.phenixrts.suite.phenixmultiangleondemand.models.Stream
import kotlin.properties.Delegates

class StreamAdapter(
    private val onStreamClicked: (stream: Stream) -> Unit
) : RecyclerView.Adapter<StreamAdapter.ViewHolder>() {

    var data: List<Stream> by Delegates.observable(mutableListOf()) { _, old, new ->
        DiffUtil.calculateDiff(RoomMemberDiff(old, new)).dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        RowStreamItemBinding.inflate(LayoutInflater.from(parent.context)).apply {
            lifecycleOwner = parent.context as? LifecycleOwner
        }
    )

    override fun getItemCount() = data.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val stream = data[position]
        holder.binding.stream = stream
        stream.setThumbnailSurfaces(holder.binding.itemStreamSurface, holder.binding.itemBitmapSurface)
        stream.isMainRendered.observeForever {
            holder.binding.stream = stream
        }
    }

    inner class ViewHolder(val binding: RowStreamItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.itemSurfaceHolder.setOnClickListener {
                data.getOrNull(adapterPosition)?.let { roomMember ->
                    onStreamClicked(roomMember)
                }
            }
        }
    }

    class RoomMemberDiff(private val oldItems: List<Stream>,
                         private val newItems: List<Stream>
    ) : DiffUtil.Callback() {

        override fun getOldListSize() = oldItems.size

        override fun getNewListSize() = newItems.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldItems[oldItemPosition].streamId == newItems[newItemPosition].streamId
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldItems[oldItemPosition] == newItems[newItemPosition]
        }
    }
}
