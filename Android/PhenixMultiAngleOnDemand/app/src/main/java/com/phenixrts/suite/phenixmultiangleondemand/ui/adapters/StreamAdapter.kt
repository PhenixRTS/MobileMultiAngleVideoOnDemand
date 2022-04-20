/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixmultiangleondemand.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.phenixrts.suite.phenixcore.PhenixCore
import com.phenixrts.suite.phenixcore.repositories.models.PhenixStream
import com.phenixrts.suite.phenixcore.repositories.models.PhenixTimeShiftState
import com.phenixrts.suite.phenixmultiangleondemand.common.setVisibleOr
import com.phenixrts.suite.phenixmultiangleondemand.databinding.RowStreamItemBinding
import kotlin.properties.Delegates

class StreamAdapter(
    private val phenixCore: PhenixCore,
    private val onStreamClicked: (stream: PhenixStream) -> Unit
) : RecyclerView.Adapter<StreamAdapter.ViewHolder>() {

    var data: List<PhenixStream> by Delegates.observable(mutableListOf()) { _, old, new ->
        DiffUtil.calculateDiff(PhenixChannelDiff(old, new)).dispatchUpdatesTo(this)
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
        holder.binding.itemSurfaceHolder.tag = stream
        holder.binding.itemSurfaceHolder.setOnClickListener {
            onStreamClicked(it.tag as PhenixStream)
        }
        updateChannelRenderer(stream, holder)
    }

    private fun updateChannelRenderer(channel: PhenixStream, holder: ViewHolder) {
        phenixCore.renderOnImage(channel.id, holder.binding.itemBitmapImage)
        holder.binding.itemBitmapImage.setVisibleOr(
            channel.isSelected || channel.timeShiftState == PhenixTimeShiftState.PAUSED
        )
        if (!channel.isSelected) {
            phenixCore.renderOnSurface(channel.id, holder.binding.itemStreamSurface)
        }
    }

    inner class ViewHolder(val binding: RowStreamItemBinding) : RecyclerView.ViewHolder(binding.root)

    class PhenixChannelDiff(private val oldItems: List<PhenixStream>,
                            private val newItems: List<PhenixStream>
    ) : DiffUtil.Callback() {

        override fun getOldListSize() = oldItems.size

        override fun getNewListSize() = newItems.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldItems[oldItemPosition].id == newItems[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldItems[oldItemPosition] == newItems[newItemPosition]
        }
    }
}
