package com.cyclealarm.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // goAsync() extends the BroadcastReceiver lifecycle beyond onReceive() return.
        // Without it, lowmemorykiller can kill the process between onReceive() ending
        // and Service.onStartCommand() beginning — alarm silently lost (AOSP Bug 25846551).
        val pendingResult = goAsync()

        val label = intent.getStringExtra("label") ?: "给妈挂号"
        val ringtone = intent.getStringExtra("ringtone")
        val note = intent.getStringExtra("note") ?: ""
        val medicineName = intent.getStringExtra("medicine_name") ?: ""
        val vibrate = intent.getBooleanExtra("vibrate", true)
        val isTest = intent.getBooleanExtra(AlarmService.EXTRA_IS_TEST, false)
        val alarmId = intent.getStringExtra("alarm_id")
        val isBackup = intent.getBooleanExtra("is_backup", false)

        // ── Backup alarm dedup ──
        // If this is a backup alarm but the main alarm already fired, silently cancel.
        // If the main alarm was cleared by the ROM, the backup acts as the main alarm.
        if (isBackup && alarmId != null) {
            val prefs = context.getSharedPreferences(AlarmScheduler.PREFS_NAME, Context.MODE_PRIVATE)
            val mainFired = prefs.getBoolean("alarm_fired_$alarmId", false)
            if (mainFired) {
                prefs.edit().remove("alarm_fired_$alarmId").apply()
                pendingResult.finish()
                return
            }
        }

        // Mark main alarm as fired so the backup (if any) will skip itself
        if (!isBackup && alarmId != null) {
            context.getSharedPreferences(AlarmScheduler.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean("alarm_fired_$alarmId", true).apply()
        }

        // Post the full-screen alarm notification first. On strict ROMs this is
        // often more reliable than launching an Activity directly from background.
        showFiredNotification(context, label, note, alarmId, isTest)

        // Launch ForegroundService to handle ringing
        AlarmService.start(context, label, ringtone, note, medicineName, alarmId, vibrate, isTest)

        // Signal that we're done — process can now safely return from onReceive()
        pendingResult.finish()

        ReliabilityLogger.log(context,
            if (isBackup) ReliabilityLogger.Event.MISS else ReliabilityLogger.Event.FIRE,
            "$label${if (isBackup) " (备份触发)" else ""}")
    }

    private fun showFiredNotification(
        context: Context,
        label: String,
        note: String,
        alarmId: String?,
        isTest: Boolean
    ) {
        val displayText = if (note.isNotEmpty()) note else label
        val channelId = AlarmService.CHANNEL_ID
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "闹钟响铃",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "周期闹钟全屏响铃通知"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(true)
                enableLights(true)
                setBypassDnd(true)
            }
            nm.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, AlarmRingingActivity::class.java).apply {
            putExtra(AlarmService.EXTRA_LABEL, label)
            putExtra(AlarmService.EXTRA_NOTE, note)
            putExtra(AlarmService.EXTRA_IS_TEST, isTest)
            if (alarmId != null) putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val contentPi = PendingIntent.getActivity(
            context, if (isTest) 7303 else 2, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (isTest) "测试响铃" else displayText)
            .setContentText(if (isTest) "验证锁屏提示、铃声和振动" else "闹钟正在响铃")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(contentPi)
            .setFullScreenIntent(contentPi, true)
            .setAutoCancel(false)
            .build()

        nm.notify(AlarmService.FIRED_NOTIFICATION_ID, notification)
    }
}
