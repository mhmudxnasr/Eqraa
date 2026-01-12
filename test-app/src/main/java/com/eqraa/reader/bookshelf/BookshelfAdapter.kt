/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package com.eqraa.reader.bookshelf

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import java.io.File
import com.eqraa.reader.R
import com.eqraa.reader.data.model.Book
import com.eqraa.reader.databinding.ItemRecycleBookBinding
import com.eqraa.reader.utils.singleClick

class BookshelfAdapter(
    private val onBookClick: (Book) -> Unit,
    private val onBookLongClick: (Book) -> Unit,
) : ListAdapter<Book, BookshelfAdapter.ViewHolder>(BookListDiff()) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        return ViewHolder(
            ItemRecycleBookBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val book = getItem(position)

        viewHolder.bind(book)
    }

    inner class ViewHolder(private val binding: ItemRecycleBookBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(book: Book) {
            binding.bookshelfTitleText.text = book.title
            binding.bookshelfAuthorText.text = book.author
            Picasso.get()
                .load(File(book.cover))
                .placeholder(R.drawable.cover)
                .into(binding.bookshelfCoverImage)
            
            // Parse progression and set progress bar width
            val progress = try {
                val json = org.json.JSONObject(book.progression ?: "{}")
                val locations = json.optJSONObject("locations")
                locations?.optDouble("totalProgression", 0.0) ?: 0.0
            } catch (e: Exception) {
                0.0
            }
            
            binding.progressBar.post {
                val parentWidth = (binding.progressBar.parent as View).width
                val params = binding.progressBar.layoutParams
                params.width = (parentWidth * progress).toInt()
                binding.progressBar.layoutParams = params
            }
            
            binding.root.singleClick {
                onBookClick(book)
            }
            binding.root.setOnLongClickListener {
                onBookLongClick(book)
                true
            }
        }
    }

    private class BookListDiff : DiffUtil.ItemCallback<Book>() {

        override fun areItemsTheSame(
            oldItem: Book,
            newItem: Book,
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: Book,
            newItem: Book,
        ): Boolean {
            return oldItem.title == newItem.title &&
                oldItem.href == newItem.href &&
                oldItem.author == newItem.author &&
                oldItem.identifier == newItem.identifier &&
                oldItem.progression == newItem.progression
        }
    }
}
