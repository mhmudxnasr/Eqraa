package org.readium.r2.testapp.ui.sync

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.readium.r2.testapp.R
import org.readium.r2.testapp.data.model.SyncConflict
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConflictResolutionDialog(
    private val context: Context,
    private val conflict: SyncConflict,
    private val onKeepLocal: () -> Unit,
    private val onKeepCloud: () -> Unit
) {

    fun show() {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_conflict_resolution, null)

        val localTimeText = dialogView.findViewById<TextView>(R.id.conflict_local_time)
        val localProgressText = dialogView.findViewById<TextView>(R.id.conflict_local_progress)
        val cloudTimeText = dialogView.findViewById<TextView>(R.id.conflict_cloud_time)
        val cloudProgressText = dialogView.findViewById<TextView>(R.id.conflict_cloud_progress)
        val btnKeepLocal = dialogView.findViewById<MaterialButton>(R.id.btn_keep_local)
        val btnKeepCloud = dialogView.findViewById<MaterialButton>(R.id.btn_keep_cloud)

        // Bind Data
        val timeFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        
        localTimeText.text = timeFormat.format(Date(conflict.localPosition.timestamp))
        localProgressText.text = "${(conflict.localPosition.percentage * 100).toInt()}%"

        cloudTimeText.text = timeFormat.format(Date(conflict.remotePosition.timestamp))
        cloudProgressText.text = "${(conflict.remotePosition.percentage * 100).toInt()}%"

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.conflict_title))
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnKeepLocal.setOnClickListener {
            onKeepLocal()
            dialog.dismiss()
        }

        btnKeepCloud.setOnClickListener {
            onKeepCloud()
            dialog.dismiss()
        }

        dialog.show()
    }
}
