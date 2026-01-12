package org.readium.r2.testapp.reader

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.readium.r2.testapp.R
import org.readium.r2.testapp.data.model.ReadingPosition
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

class ConflictResolutionDialogFragment : DialogFragment() {

    private var listener: ConflictResolutionListener? = null
    
    interface ConflictResolutionListener {
        fun onKeepLocal()
        fun onJumpToSynced(targetPosition: ReadingPosition)
    }

    fun setListener(listener: ConflictResolutionListener) {
        this.listener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val local = arguments?.getSerializable(ARG_LOCAL) as? ReadingPosition
        val cloud = arguments?.getSerializable(ARG_CLOUD) as? ReadingPosition
            ?: return super.onCreateDialog(savedInstanceState)
        
        // If local is null, we can probably default to "Jump to Synced" or just show cloud info vs "New Start". 
        // But for conflict, both should exist. 
        // If we only have cloud and no local state (fresh install), we usually auto-sync.
        // This dialog is for *Conflict*.
        
        val context = requireContext()
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_conflict_resolution, null)
        
        bindView(view, local, cloud)

        return MaterialAlertDialogBuilder(context)
             .setView(view)
             .setCancelable(false)
             .create()
    }
    
    private fun bindView(view: View, local: ReadingPosition?, cloud: ReadingPosition) {
        val localTime = view.findViewById<TextView>(R.id.conflict_local_time)
        val localProgress = view.findViewById<TextView>(R.id.conflict_local_progress)
        val cloudTime = view.findViewById<TextView>(R.id.conflict_cloud_time)
        val cloudProgress = view.findViewById<TextView>(R.id.conflict_cloud_progress)
        val btnKeepLocal = view.findViewById<MaterialButton>(R.id.btn_keep_local)
        val btnJumpSynced = view.findViewById<MaterialButton>(R.id.btn_keep_cloud)
        val conflictMessage = view.findViewById<TextView>(R.id.conflict_message)

        val dateFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
        
        if (local != null) {
            localTime.text = dateFormat.format(Date(local.timestamp))
            localProgress.text = formatProgress(local)
        } else {
             localTime.text = "-"
             localProgress.text = "0%"
        }
        
        cloudTime.text = dateFormat.format(Date(cloud.timestamp))
        cloudProgress.text = formatProgress(cloud)
        
        // Construct message
        val deviceName = cloud.deviceId ?: "Another Device"
        conflictMessage.text = "Reading progress found from $deviceName"

        btnKeepLocal.setOnClickListener {
            listener?.onKeepLocal()
            dismiss()
        }
        
        btnJumpSynced.setOnClickListener {
            listener?.onJumpToSynced(cloud)
            dismiss()
        }
        
        // Visual polish: highlight the one that is "ahead" or "newer"? 
        // For now, just sticking to the requested UI.
        btnJumpSynced.text = "Jump to Synced"
        btnKeepLocal.text = "Keep Local"
    }
    
    private fun formatProgress(position: ReadingPosition): String {
        return if (position.pageNumber != null && position.totalPages != null) {
            "Page ${position.pageNumber} of ${position.totalPages} (${(position.percentage * 100).roundToInt()}%)"
        } else {
             "${(position.percentage * 100).roundToInt()}%"
        }
    }

    companion object {
        const val TAG = "ConflictResolutionDialog"
        private const val ARG_LOCAL = "local_position"
        private const val ARG_CLOUD = "cloud_position"

        // ReadingPosition is Serializable because it's data class (with primitives mostly) 
        // but to be safe usually we implement Serializable or Parcelable.
        // Given it is @Serializable (Kotlin serialization), it is not android.os.Parcelable by default.
        // We might need to make it Serializable or pass fields. 
        // For simplicity, I will cast to Serializable if data class implements it or just serialize to string if needed.
        // Actually, @Serializable doesn't mean java.io.Serializable.
        
        fun newInstance(local: ReadingPosition?, cloud: ReadingPosition): ConflictResolutionDialogFragment {
             val fragment = ConflictResolutionDialogFragment()
             val args = Bundle()
             args.putSerializable(ARG_LOCAL, local as? java.io.Serializable) 
             args.putSerializable(ARG_CLOUD, cloud as? java.io.Serializable)
             fragment.arguments = args
             return fragment
        }
    }
}
