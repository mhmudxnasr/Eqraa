package com.eqraa.reader.opds

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.eqraa.reader.R
import com.eqraa.reader.databinding.ItemOpdsEntryBinding
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.opds.images
import org.readium.r2.shared.publication.Link

class OpdsAdapter(
    private val onItemClick: (OpdsItem) -> Unit
) : ListAdapter<OpdsItem, OpdsAdapter.ViewHolder>(OpdsDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemOpdsEntryBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemOpdsEntryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: OpdsItem) {
            when (item) {
                is OpdsItem.Group -> {
                    binding.opdsItemTitle.text = item.title
                    binding.opdsItemAuthor.text = "${item.links.size} items"
                    binding.opdsItemCover.setImageResource(R.drawable.baseline_local_library_24)
                    
                    // Or try to load first image from link if available
                    // item.images.firstOrNull() ...
                }
                is OpdsItem.Book -> {
                    binding.opdsItemTitle.text = item.publication.metadata.title
                    binding.opdsItemAuthor.text = item.publication.metadata.authors.joinToString(", ") { it.name }
                    
                    val coverUrl = item.publication.images.firstOrNull()?.href?.toString()
                    if (coverUrl != null) {
                        Picasso.get().load(coverUrl).into(binding.opdsItemCover)
                    } else {
                        binding.opdsItemCover.setImageResource(R.drawable.cover)
                    }
                }
            }
            
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }
}

sealed class OpdsItem {
    data class Group(val title: String, val links: List<Link>) : OpdsItem()
    data class Book(val publication: Publication) : OpdsItem()
}

class OpdsDiffCallback : DiffUtil.ItemCallback<OpdsItem>() {
    override fun areItemsTheSame(oldItem: OpdsItem, newItem: OpdsItem): Boolean {
        return oldItem == newItem // Data classes
    }

    override fun areContentsTheSame(oldItem: OpdsItem, newItem: OpdsItem): Boolean {
        return oldItem == newItem
    }
}
