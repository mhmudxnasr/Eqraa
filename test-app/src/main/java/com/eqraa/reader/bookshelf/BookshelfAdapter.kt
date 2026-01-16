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

    private var lastPosition = -1

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val book = getItem(position)
        viewHolder.bind(book)
        setAnimation(viewHolder.itemView, position)
    }

    private fun setAnimation(viewToAnimate: View, position: Int) {
        if (position > lastPosition) {
            val animation = android.view.animation.AnimationUtils.loadAnimation(viewToAnimate.context, R.anim.item_animation_fall_down)
            // Use longer delay for a more dramatic staggered entrance
            animation.startOffset = (position * 100).toLong().coerceAtMost(800) 
            viewToAnimate.startAnimation(animation)
            lastPosition = position
        }
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
                val parentWidth = (binding.progressBar.parent as android.view.View).width
                val params = binding.progressBar.layoutParams
                params.width = (parentWidth * progress).toInt()
                binding.progressBar.layoutParams = params
            }
            
            // Scale and Click Handling
            setupScaleClick(book)
            
            binding.root.setOnLongClickListener {
                onBookLongClick(book)
                true
            }
        }

        private fun setupScaleClick(book: Book) {
            binding.root.setOnTouchListener { view, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                        if (event.action == android.view.MotionEvent.ACTION_UP) {
                            view.performClick()
                            onBookClick(book)
                        }
                    }
                }
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
