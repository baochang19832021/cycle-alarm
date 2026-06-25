package com.cyclealarm.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

class AlarmService : Service() {

    companion object {
        const val ACTION_START = "com.cyclealarm.app.ACTION_START"
        const val ACTION_STOP = "com.cyclealarm.app.ACTION_STOP"
        const val EXTRA_LABEL = "label"
        const val EXTRA_RINGTONE = "ringtone"
        const val EXTRA_NOTE = "note"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_VIBRATE = "vibrate"
        const val EXTRA_RESCHEDULE = "reschedule"
        const val CHANNEL_ID = "cycle_alarm_foreground"
        const val NOTIFICATION_ID = 2001

        fun start(
            context: Context,
            label: String,
            ringtone: String?,
            note: String = "",
            alarmId: String? = null,
            vibrate: Boolean = true
        ) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_RINGTONE, ringtone)
                putExtra(EXTRA_NOTE, note)
                putExtra(EXTRA_VIBRATE, vibrate)
                if (alarmId != null) putExtra(EXTRA_ALARM_ID, alarmId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context, shouldReschedule: Boolean = true) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_RESCHEDULE, shouldReschedule)
            }
            context.startService(intent)
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentAlarmId: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAlarm(intent.getBooleanExtra(EXTRA_RESCHEDULE, true))
            return START_NOT_STICKY
        }

        val label = intent?.getStringExtra(EXTRA_LABEL) ?: "给妈挂号"
        val ringtone = intent?.getStringExtra(EXTRA_RINGTONE)
        val note = intent?.getStringExtra(EXTRA_NOTE) ?: ""
        val alarmId = intent?.getStringExtra(EXTRA_ALARM_ID)
        cleanup()
        currentAlarmId = alarmId

        // Acquire wake lock to keep CPU alive
        val powerMgr = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerMgr.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CycleAlarm::AlarmWakeLock"
        ).apply {
            acquire(5 * 60 * 1000L) // max 5 minutes
        }

        // Start as foreground service first (must be called within 5s)
        startForeground(NOTIFICATION_ID, buildNotification(label))

        // Launch full-screen ringing activity
        val ringingIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            putExtra(EXTRA_LABEL, label)
            putExtra(EXTRA_NOTE, note)
            if (alarmId != null) putExtra(EXTRA_ALARM_ID, alarmId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(ringingIntent)

        // Start ringing
        startRingtone(ringtone)
        if (intent?.getBooleanExtra(EXTRA_VIBRATE, true) != false) startVibration()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }

    private fun startRingtone(ringtonePath: String?) {
        try {
            val uri = if (!ringtonePath.isNullOrEmpty()) {
                Uri.parse(ringtonePath)
            } else {
                android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                setWakeMode(this@AlarmService, PowerManager.PARTIAL_WAKE_LOCK)
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: try system default
            try {
                val defaultUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(this@AlarmService, defaultUri)
                    isLooping = true
                    setWakeMode(this@AlarmService, PowerManager.PARTIAL_WAKE_LOCK)
                    prepare()
                    start()
                }
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 1000, 500, 1000), 0))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(longArrayOf(0, 1000, 500, 1000), 0)
            }
        }
    }

    private fun buildNotification(label: String): Notification {
        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPi = PendingIntent.getActivity(
            this, 1, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ 该挂号了！")
            .setContentText(label)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(contentPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭闹钟", stopPi)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "闹钟响铃",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "挂号闹钟响铃通知"
                setSound(null, null)
                enableVibration(true)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun stopAlarm(shouldReschedule: Boolean) {
        cleanup()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()

        // Reschedule next cycle for this specific alarm
        if (shouldReschedule && currentAlarmId != null) {
            AlarmScheduler.rescheduleNext(this, currentAlarmId!!)
        } else if (shouldReschedule) {
            AlarmScheduler.rescheduleNext(this)
        }
    }

    private fun cleanup() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null
        } catch (_: Exception) {}

        try {
            vibrator?.cancel()
            vibrator = null
        } catch (_: Exception) {}

        try {
            wakeLock?.apply {
                if (isHeld) release()
            }
            wakeLock = null
        } catch (_: Exception) {}
    }
}
