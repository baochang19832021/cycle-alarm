package com.cyclealarm.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra("label") ?: "给妈挂号"
        val ringtone = intent.getStringExtra("ringtone")
        val note = intent.getStringExtra("note") ?: ""
        val vibrate = intent.getBooleanExtra("vibrate", true)

        val alarmId = intent.getStringExtra("alarm_id")

        // Launch ForegroundService to handle ringing
        AlarmService.start(context, label, ringtone, note, alarmId, vibrate)

        // Also post a high-priority notification (for wearables / lock screen)
        showFiredNotification(context, label, note)
    }

    private fun showFiredNotification(context: Context, label: String, note: String) {
        val displayText = if (note.isNotEmpty()) note else label
        val channelId = "cycle_alarm_channel"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "挂号提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "医院挂号闹钟"
                setSound(null, null)
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentPi = PendingIntent.getActivity(
            context, 2, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(displayText)
            .setContentText("")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPi)
            .setAutoCancel(true)
            .build()

        nm.notify(1001, notification)
    }
}
