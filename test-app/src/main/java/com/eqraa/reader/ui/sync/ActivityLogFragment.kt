package com.eqraa.reader.ui.sync

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.eqraa.reader.R
import com.eqraa.reader.data.model.SyncLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// @AndroidEntryPoint // If using Hilt, but ViewModel here uses AndroidViewModel (Factory needed if not Hilt)
// We are manually injecting via Factory or standard ViewModel if Application available.
class ActivityLogFragment : Fragment() {

    private val viewModel: ActivityLogViewModel by viewModels() // Requires Application context, default factory works for AndroidViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_activity_log, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.log_recycler_view)
        val emptyView = view.findViewById<TextView>(R.id.log_empty_view)

        val adapter = LogAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.logs.collectLatest { logs ->
                    if (logs.isEmpty()) {
                        emptyView.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    } else {
                        emptyView.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        adapter.submitList(logs)
                    }
                }
            }
        }
    }

    inner class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {
        private var items: List<SyncLogEntry> = emptyList()

        fun submitList(list: List<SyncLogEntry>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_sync_log, parent, false)
            return LogViewHolder(view)
        }

        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val sourceText: TextView = itemView.findViewById(R.id.log_source)
            private val timeText: TextView = itemView.findViewById(R.id.log_time)
            private val messageText: TextView = itemView.findViewById(R.id.log_message)
            private val detailsText: TextView = itemView.findViewById(R.id.log_details)
            private val dateFormatter = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

            fun bind(entry: SyncLogEntry) {
                sourceText.text = entry.source
                timeText.text = dateFormatter.format(Date(entry.timestamp))
                messageText.text = "[${entry.eventType}] ${entry.message}"
                
                if (!entry.details.isNullOrEmpty()) {
                    detailsText.text = entry.details
                    detailsText.visibility = View.VISIBLE
                } else {
                    detailsText.visibility = View.GONE
                }
                
                // Color coding
                if (entry.eventType == "ERROR") {
                    messageText.setTextColor(android.graphics.Color.RED)
                } else {
                    messageText.setTextColor(android.graphics.Color.BLACK) // Or default
                }
            }
        }
    }
}
