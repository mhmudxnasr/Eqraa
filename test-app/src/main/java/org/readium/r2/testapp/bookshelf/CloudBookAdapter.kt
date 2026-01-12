/*
 * Cloud Book Adapter
 *
 * RecyclerView adapter for displaying cloud library books with download/delete actions.
 */

package org.readium.r2.testapp.bookshelf

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.readium.r2.testapp.R
import org.readium.r2.testapp.data.model.CloudBookDto

class CloudBookAdapter(
    private val onDownloadClick: (CloudBookDto) -> Unit,
    private val onDeleteClick: (CloudBookDto) -> Unit
) : ListAdapter<CloudBookDto, CloudBookAdapter.CloudBookViewHolder>(CloudBookDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CloudBookViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cloud_book, parent, false)
        return CloudBookViewHolder(view)
    }

    override fun onBindViewHolder(holder: CloudBookViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CloudBookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.cloud_book_title)
        private val authorText: TextView = itemView.findViewById(R.id.cloud_book_author)
        private val downloadBtn: ImageButton = itemView.findViewById(R.id.btn_download_cloud_book)
        private val deleteBtn: ImageButton = itemView.findViewById(R.id.btn_delete_cloud_book)

        fun bind(book: CloudBookDto) {
            titleText.text = book.title ?: book.filename
            authorText.text = book.author ?: "Unknown Author"

            downloadBtn.setOnClickListener { onDownloadClick(book) }
            deleteBtn.setOnClickListener { onDeleteClick(book) }
        }
    }

    class CloudBookDiffCallback : DiffUtil.ItemCallback<CloudBookDto>() {
        override fun areItemsTheSame(oldItem: CloudBookDto, newItem: CloudBookDto): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CloudBookDto, newItem: CloudBookDto): Boolean {
            return oldItem == newItem
        }
    }
}
