package com.eqraa.reader.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.eqraa.reader.MainActivity
import com.eqraa.reader.R
import timber.log.Timber

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.d("Boot completed, rescheduling daily reminder")
            NotificationScheduler.scheduleDailyReminder(context)
            return
        }

        Timber.d("Received daily reminder broadcast")
        showNotification(context)
    }

    private fun showNotification(context: Context) {
        val channelId = "reading_reminder_channel"
        val notificationId = 1001

        createNotificationChannel(context, channelId)

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val messages = listOf(
            "Time for your daily reading ritual! 📚",
            "A few pages a day keeps the ignorance away. ✨",
            "Escape into another world for a while. 🌍",
            "Your book is waiting for you... 📖",
            "Knowledge is power. Spend 15 minutes reading? ⚡"
        )
        val selectedMessage = messages.random()

        val builder = NotificationCompat.Builder(context, channelId)
        builder.setSmallIcon(com.eqraa.reader.R.mipmap.ic_launcher_foreground)
        builder.setContentTitle("Daily Reading Time")
        builder.setContentText(selectedMessage)
        builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
        builder.setContentIntent(pendingIntent)
        builder.setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(notificationId, builder.build())
            } catch (e: SecurityException) {
                Timber.e(e, "Permission missing for notification")
            }
        }
    }

    private fun createNotificationChannel(context: Context, channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Reading Reminders"
            val descriptionText = "Daily reminders to keep up with your reading goals"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
