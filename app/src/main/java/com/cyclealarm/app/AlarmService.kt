package com.cyclealarm.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
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
        const val EXTRA_IS_TEST = "is_test_alarm"
        const val EXTRA_MEDICINE_NAME = "medicine_name"
        const val CHANNEL_ID = "cycle_alarm_full_screen_v2"
        const val NOTIFICATION_ID = 2001
        const val FIRED_NOTIFICATION_ID = 1001

        fun start(
            context: Context,
            label: String,
            ringtone: String?,
            note: String = "",
            medicineName: String = "",
            alarmId: String? = null,
            vibrate: Boolean = true,
            isTest: Boolean = false
        ) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_RINGTONE, ringtone)
                putExtra(EXTRA_NOTE, note)
                putExtra(EXTRA_MEDICINE_NAME, medicineName)
                putExtra(EXTRA_VIBRATE, vibrate)
                putExtra(EXTRA_IS_TEST, isTest)
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
    private var screenWakeLock: PowerManager.WakeLock? = null
    private var currentAlarmId: String? = null
    private var currentIsTest: Boolean = false
    private var currentMedName: String = ""
    private var tts: TextToSpeech? = null
    private var audioManager: AudioManager? = null
    private var volumeHandler: Handler? = null
    private var volumeRunnable: Runnable? = null
    private var timeoutHandler: Handler? = null
    private var timeoutRunnable: Runnable? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus: Boolean = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAlarm(intent.getBooleanExtra(EXTRA_RESCHEDULE, true))
            return START_NOT_STICKY
        }

        val label = intent?.getStringExtra(EXTRA_LABEL) ?: "周期闹钟"
        val ringtone = intent?.getStringExtra(EXTRA_RINGTONE)
        val note = intent?.getStringExtra(EXTRA_NOTE) ?: ""
        val medicineName = intent?.getStringExtra(EXTRA_MEDICINE_NAME) ?: ""
        val alarmId = intent?.getStringExtra(EXTRA_ALARM_ID)
        currentMedName = medicineName
        val isMedicineAlarm = note.startsWith("吃药")
        val isTest = intent?.getBooleanExtra(EXTRA_IS_TEST, false) == true
        cleanup()
        currentAlarmId = alarmId
        currentIsTest = isTest

        val powerMgr = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerMgr.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CycleAlarm::AlarmWakeLock"
        ).apply {
            acquire(5 * 60 * 1000L)
        }
        @Suppress("DEPRECATION")
        screenWakeLock = powerMgr.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "CycleAlarm::ScreenWakeLock"
        ).apply {
            acquire(15 * 1000L)
        }

        startForeground(NOTIFICATION_ID, buildNotification(label, note, alarmId, isTest))

        val ringingIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            putExtra(EXTRA_LABEL, label)
            putExtra(EXTRA_NOTE, note)
            putExtra(EXTRA_MEDICINE_NAME, medicineName)
            putExtra(EXTRA_IS_TEST, isTest)
            if (alarmId != null) putExtra(EXTRA_ALARM_ID, alarmId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            startActivity(ringingIntent)
        } catch (_: ActivityNotFoundException) {
            // Full-screen notification remains the primary fallback path.
        } catch (_: SecurityException) {
            // Some ROMs block background activity launches while locked.
        }

        // Request audio focus so we can duck music/video playback
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Push system alarm volume to max so user hears it
        try {
            val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 7
            audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
        } catch (_: SecurityException) {}
        requestAudioFocus()

        startRingtone(ringtone)
        if (intent?.getBooleanExtra(EXTRA_VIBRATE, true) != false) startVibration()
        // Voice announcement: try TTS first, fall back to built-in beep pattern
        if (medicineName.isNotEmpty()) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                speakMedicineName(medicineName)
            }, 3000L)
        } else if (isMedicineAlarm) {
            // Medicine alarm without TTS — use built-in distinct beep pattern
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                playMedicineFallbackBeep()
            }, 3000L)
        }

        // ── Rising volume: 20% → 100% over ~30 seconds ──
        startRisingVolume()

        // ── Auto-stop after 10 minutes (keep notification) ──
        startAutoTimeout(alarmId, isTest)

        // ── Phone state listener: mute on incoming call ──
        registerPhoneStateListener()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }

    private fun speakMedicineName(name: String) {
        // Lower ringtone volume so voice is clearly audible
        try { mediaPlayer?.setVolume(0.1f, 0.1f) } catch (_: Exception) {}

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Use alarm stream so voice plays even when media volume is off
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                // Try Chinese first, fall back to bundled voice if not available
                val langResult = tts?.setLanguage(java.util.Locale.CHINESE) ?: TextToSpeech.LANG_MISSING_DATA
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Chinese TTS not available — use bundled voice file instead
                    try { mediaPlayer?.setVolume(1f, 1f) } catch (_: Exception) {}
                    playMedicineFallbackBeep()
                    return@TextToSpeech
                }
                val utteranceId = "med_${System.currentTimeMillis()}"
                val text = "该吃${name}了"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onDone(utteranceId: String?) {
                            try { mediaPlayer?.setVolume(1f, 1f) } catch (_: Exception) {}
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            try { mediaPlayer?.setVolume(1f, 1f) } catch (_: Exception) {}
                        }
                        override fun onStart(utteranceId: String?) {}
                    })
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                } else {
                    @Suppress("DEPRECATION")
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try { mediaPlayer?.setVolume(1f, 1f) } catch (_: Exception) {}
                    }, 3000L)
                }
            } else {
                // TTS init failed — use built-in beep fallback
                try { mediaPlayer?.setVolume(1f, 1f) } catch (_: Exception) {}
                playMedicineFallbackBeep()
            }
        }
    }

    private fun playMedicineFallbackBeep() {
        // Play bundled voice audio "该吃药了" — real human voice, no TTS needed.
        // Falls back to TTS for medicine name announcement if available.
        try {
            try { mediaPlayer?.setVolume(0.05f, 0.05f) } catch (_: Exception) {}
            val voicePlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(this@AlarmService, Uri.parse("android.resource://${packageName}/${R.raw.medicine_voice}"))
                setOnCompletionListener {
                    try { mediaPlayer?.setVolume(1f, 1f) } catch (_: Exception) {}
                    release()
                }
                setOnErrorListener { _, _, _ ->
                    try { mediaPlayer?.setVolume(1f, 1f) } catch (_: Exception) {}
                    release(); true
                }
                prepare()
                start()
            }
        } catch (_: Exception) {
            try { mediaPlayer?.setVolume(1f, 1f) } catch (_: Exception) {}
        }
    }

    private fun startRingtone(ringtonePath: String?) {
        val candidates = mutableListOf<Uri>()
        if (!ringtonePath.isNullOrEmpty()) {
            candidates += Uri.parse(ringtonePath)
        }
        listOf(
            RingtoneManager.TYPE_ALARM,
            RingtoneManager.TYPE_NOTIFICATION,
            RingtoneManager.TYPE_RINGTONE
        ).forEach { type ->
            RingtoneManager.getDefaultUri(type)?.let { uri ->
                if (!candidates.contains(uri)) candidates += uri
            }
        }

        for (uri in candidates) {
            try {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(this@AlarmService, uri)
                    isLooping = true
                    // Start at 40% — audible from the start, rising to full
                    setVolume(0.4f, 0.4f)
                    setWakeMode(this@AlarmService, PowerManager.PARTIAL_WAKE_LOCK)
                    prepare()
                    start()
                }
                return
            } catch (e: Exception) {
                try {
                    mediaPlayer?.release()
                } catch (_: Exception) {}
                mediaPlayer = null
                e.printStackTrace()
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

    // ── Audio focus: duck music/video when alarm rings ──
    private fun requestAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                            mediaPlayer?.setVolume(0.5f, 0.5f)
                        }
                    }
                    .build()
                hasAudioFocus = am.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                hasAudioFocus = am.requestAudioFocus(
                    { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                            mediaPlayer?.setVolume(0.5f, 0.5f)
                        }
                    },
                    AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (_: Exception) {
            // Audio focus is best-effort; failure must not crash
        }
    }

    // ── Rising volume: 0.4 → 1.0 over ~15 seconds ──
    private fun startRisingVolume() {
        volumeHandler = Handler(Looper.getMainLooper())
        volumeRunnable = object : Runnable {
            var step = 0
            override fun run() {
                val player = mediaPlayer ?: return
                val vol = (0.4f + step * 0.06f).coerceAtMost(1f)
                try { player.setVolume(vol, vol) } catch (_: Exception) {}
                step++
                if (step <= 10) {
                    volumeHandler?.postDelayed(this, 1500L)
                }
            }
        }
        volumeHandler?.postDelayed(volumeRunnable!!, 1500L)
    }

    // ── Auto-timeout: stop ringing after 10 minutes, keep notification ──
    private fun startAutoTimeout(alarmId: String?, isTest: Boolean) {
        timeoutHandler = Handler(Looper.getMainLooper())
        timeoutRunnable = Runnable {
            try {
                mediaPlayer?.apply { if (isPlaying) stop() }
                vibrator?.cancel()
            } catch (_: Exception) {}
            // Don't call stopAlarm() — keep the notification visible
        }
        timeoutHandler?.postDelayed(timeoutRunnable!!, 10 * 60 * 1000L)
    }

    // ── Phone state: mute alarm during incoming calls ──
    @Suppress("DEPRECATION")
    private fun registerPhoneStateListener() {
        try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
            phoneStateListener = object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    if (state == TelephonyManager.CALL_STATE_RINGING) {
                        try {
                            mediaPlayer?.setVolume(0f, 0f)
                        } catch (_: Exception) {}
                    } else if (state == TelephonyManager.CALL_STATE_IDLE) {
                        try {
                            // Restore volume when call ends
                            mediaPlayer?.setVolume(1f, 1f)
                        } catch (_: Exception) {}
                    }
                }
            }
            tm.listen(phoneStateListener!!, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (_: Exception) {
            // Phone state requires READ_PHONE_STATE on older APIs; best-effort
        }
    }

    private fun buildNotification(
        label: String,
        note: String,
        alarmId: String?,
        isTest: Boolean
    ): Notification {
        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val launchIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            putExtra(EXTRA_LABEL, label)
            putExtra(EXTRA_NOTE, note)
            putExtra(EXTRA_IS_TEST, isTest)
            if (alarmId != null) putExtra(EXTRA_ALARM_ID, alarmId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val contentPi = PendingIntent.getActivity(
            this, 1, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (isTest) "测试响铃" else "周期闹钟响铃")
            .setContentText(if (note.isNotEmpty()) note else label)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(contentPi)
            .setFullScreenIntent(contentPi, true)
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
                description = "周期闹钟全屏响铃通知"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(true)
                enableLights(true)
                setBypassDnd(true)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun stopAlarm(shouldReschedule: Boolean) {
        cleanup()
        clearAlarmNotifications()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()

        if (shouldReschedule && currentAlarmId != null && !currentIsTest) {
            AlarmScheduler.rescheduleNext(this, currentAlarmId!!)
        } else if (shouldReschedule && !currentIsTest) {
            AlarmScheduler.rescheduleNext(this)
        }
    }

    private fun clearAlarmNotifications() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(FIRED_NOTIFICATION_ID)
            nm.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
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

        try {
            screenWakeLock?.apply {
                if (isHeld) release()
            }
            screenWakeLock = null
        } catch (_: Exception) {}

        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {}

        try {
            volumeRunnable?.let { volumeHandler?.removeCallbacks(it) }
            volumeHandler = null
            volumeRunnable = null
        } catch (_: Exception) {}

        try {
            timeoutRunnable?.let { timeoutHandler?.removeCallbacks(it) }
            timeoutHandler = null
            timeoutRunnable = null
        } catch (_: Exception) {}

        try {
            hasAudioFocus = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}

        try {
            @Suppress("DEPRECATION")
            phoneStateListener?.let {
                val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                tm?.listen(it, PhoneStateListener.LISTEN_NONE)
            }
            phoneStateListener = null
        } catch (_: Exception) {}
    }
}
