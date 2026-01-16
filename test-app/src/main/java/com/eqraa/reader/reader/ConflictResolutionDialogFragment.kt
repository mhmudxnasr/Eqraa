import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import com.eqraa.reader.data.model.ReadingPosition
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
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val local = arguments?.getSerializable(ARG_LOCAL) as? ReadingPosition
                val cloud = arguments?.getSerializable(ARG_CLOUD) as? ReadingPosition

                if (cloud != null) {
                    ConflictResolutionScreen(
                        local = local,
                        cloud = cloud,
                        onKeepLocal = { 
                            listener?.onKeepLocal()
                            dismiss() 
                        },
                        onJumpSynced = { 
                            listener?.onJumpToSynced(cloud)
                            dismiss() 
                        }
                    )
                } else {
                    dismiss()
                }
            }
        }
    }

    companion object {
        const val TAG = "ConflictResolutionDialog"
        private const val ARG_LOCAL = "local_position"
        private const val ARG_CLOUD = "cloud_position"

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

@Composable
fun ConflictResolutionScreen(
    local: ReadingPosition?,
    cloud: ReadingPosition,
    onKeepLocal: () -> Unit,
    onJumpSynced: () -> Unit
) {
    // Glassy Black Theme
    val darkGlass = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF282828).copy(alpha = 0.95f),
            Color(0xFF121212).copy(alpha = 0.98f)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(darkGlass)
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                RoundedCornerShape(24.dp)
            )
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            val deviceName = cloud.deviceId ?: "Another Device"
            Text(
                text = "Reading progress found from\n$deviceName",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Comparison Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Local Box
                ConflictInfoBox(
                    title = "ON THIS DEVICE",
                    position = local,
                    modifier = Modifier.weight(1f)
                )

                // Cloud Box
                ConflictInfoBox(
                    title = "IN CLOUD",
                    position = cloud,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Buttons
            // 1. Keep Local (Secondary)
            Button(
                onClick = onKeepLocal,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(
                    "KEEP LOCAL",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Jump to Synced (Primary - High Contrast)
            Button(
                onClick = onJumpSynced,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, Color.White), // Explicit white border for contrast
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                 Text(
                    "Jump to Synced",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ConflictInfoBox(
    title: String,
    position: ReadingPosition?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (position != null) {
                val timeFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                val time = timeFormat.format(java.util.Date(position.timestamp))
                
                Text(
                    text = time,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                val percentage = (position.percentage * 100).toInt()
                Text(
                    text = "$percentage%",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Light
                )
            } else {
                Text(
                    text = "-",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp
                )
            }
        }
    }
}
