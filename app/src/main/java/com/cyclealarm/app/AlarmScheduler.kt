package com.cyclealarm.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.cyclealarm.domain.AlarmTimeCalculator
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.UUID

object AlarmScheduler {
    const val PREFS_NAME = "CycleAlarmPrefs"
    const val ALARMS_DIR = "cycle_alarms"
    const val INTERVAL_DEFAULT_DAYS = 40
    const val ACTION_ALARM_FIRED = "com.cyclealarm.app.ALARM_FIRED"
    const val ACTION_HEALTH_CHECK = "com.cyclealarm.app.HEALTH_CHECK"
    private const val RELIABILITY_TEST_REQUEST_CODE = 7302
    private const val HEALTH_CHECK_REQUEST_CODE = 7303
    private const val PERSISTENT_NOTIFICATION_ID = 3001
    private const val PERSISTENT_CHANNEL_ID = "cycle_alarm_persistent"

    // Legacy SharedPreferences keys (for migration)
    const val KEY_INTERVAL_DAYS = "interval_days"
    const val KEY_START_DATE_MS = "start_date_ms"
    const val KEY_ALARM_TIME_MS = "alarm_time_ms"
    const val KEY_RINGTONE_URI = "ringtone_uri"
    const val KEY_LABEL = "alarm_label"
    const val KEY_NOTE = "alarm_note"

    // ── Data class ──
    data class AlarmData(
        var id: String = "",
        var hour: Int = 8,
        var minute: Int = 0,
        var intervalDays: Int = 40,
        var repeatMinutes: Int = 0,
        var repeatMonths: Int = 0,
        var startDateMs: Long = 0L,
        var nextTimeMs: Long = 0L,
        var ringtoneUri: String? = null,
        var label: String = "",
        var note: String = "",
        var medicineName: String = "",
        var vibrate: Boolean = true,
        var isActive: Boolean = false
    )

    // ── Generate unique ID ──
    fun newId(): String = "alarm_${UUID.randomUUID()}"

    // ── Get alarms directory ──
    private fun alarmsDir(context: Context): File {
        return File(context.filesDir, ALARMS_DIR).apply { mkdirs() }
    }

    // ── File path for alarm ──
    private fun alarmFile(context: Context, id: String): File {
        return File(alarmsDir(context), "$id.json")
    }

    // ════════════════════════════════════
    //  SAVE / LOAD / DELETE (JSON files)
    // ════════════════════════════════════

    fun saveAlarm(context: Context, data: AlarmData) {
        val json = JSONObject().apply {
            put("id", data.id)
            put("hour", data.hour)
            put("minute", data.minute)
            put("intervalDays", data.intervalDays)
            put("repeatMinutes", data.repeatMinutes)
            put("repeatMonths", data.repeatMonths)
            put("startDateMs", data.startDateMs)
            put("nextTimeMs", data.nextTimeMs)
            put("ringtoneUri", data.ringtoneUri ?: "")
            put("label", data.label)
            put("note", data.note)
            put("medicineName", data.medicineName)
            put("vibrate", data.vibrate)
            put("isActive", data.isActive)
        }
        alarmFile(context, data.id).writeText(json.toString(2))
    }

    fun loadAlarm(context: Context, id: String): AlarmData? {
        val file = alarmFile(context, id)
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            AlarmData(
                id = json.optString("id", id),
                hour = json.optInt("hour", 8),
                minute = json.optInt("minute", 0),
                intervalDays = json.optInt("intervalDays", INTERVAL_DEFAULT_DAYS),
                repeatMinutes = json.optInt("repeatMinutes", 0),
                repeatMonths = json.optInt("repeatMonths", 0),
                startDateMs = json.optLong("startDateMs", 0L),
                nextTimeMs = json.optLong("nextTimeMs", 0L),
                ringtoneUri = json.optString("ringtoneUri", "").ifEmpty { null },
                label = json.optString("label", ""),
                note = json.optString("note", ""),
                medicineName = json.optString("medicineName", ""),
                vibrate = json.optBoolean("vibrate", true),
                isActive = json.optBoolean("isActive", false)
            )
        } catch (_: Exception) { null }
    }

    fun deleteAlarm(context: Context, id: String) {
        // Cancel the scheduled alarm first
        cancelById(context, id)
        alarmFile(context, id).delete()
    }

    fun getAllAlarms(context: Context): List<AlarmData> {
        // Migration: convert old single SharedPreferences alarm to new file-based format
        migrateFromLegacyPrefs(context)

        val dir = alarmsDir(context)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { file -> file.extension == "json" && file.name.startsWith("alarm_") }
            ?.mapNotNull { loadAlarm(context, it.nameWithoutExtension) }
            ?.sortedBy { it.nextTimeMs }
            ?: emptyList()
    }

    private fun migrateFromLegacyPrefs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val nextTime = prefs.getLong(KEY_ALARM_TIME_MS, 0L)
        if (nextTime <= 0L) return

        val id = newId()
        val cal = Calendar.getInstance().apply { timeInMillis = nextTime }
        val data = AlarmData(
            id = id,
            hour = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE),
            intervalDays = prefs.getInt(KEY_INTERVAL_DAYS, INTERVAL_DEFAULT_DAYS),
            repeatMinutes = 0,
            repeatMonths = 0,
            startDateMs = prefs.getLong(KEY_START_DATE_MS, 0L),
            nextTimeMs = nextTime,
            ringtoneUri = prefs.getString(KEY_RINGTONE_URI, null),
            label = prefs.getString(KEY_LABEL, "") ?: "",
            note = prefs.getString(KEY_NOTE, "") ?: "",
            vibrate = true,
            isActive = nextTime > System.currentTimeMillis()
        )
        saveAlarm(context, data)
        // Clear legacy alarm keys (keep miui_hint)
        prefs.edit().apply {
            remove(KEY_INTERVAL_DAYS)
            remove(KEY_START_DATE_MS)
            remove(KEY_ALARM_TIME_MS)
            remove(KEY_RINGTONE_URI)
            remove(KEY_LABEL)
            remove(KEY_NOTE)
            apply()
        }
    }

    // ════════════════════════════════════
    //  SCHEDULE / CANCEL (AlarmManager)
    // ════════════════════════════════════

    fun schedule(context: Context, data: AlarmData) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val operation = pendingIntent(context, data)
        val nextTriggerMs = when {
            data.repeatMinutes > 0 -> {
                AlarmTimeCalculator.calculateNextMinuteInterval(
                    anchorTimeMs = data.startDateMs,
                    intervalMinutes = data.repeatMinutes
                ).also { nextMs ->
                    Calendar.getInstance().apply {
                        timeInMillis = nextMs
                        data.hour = get(Calendar.HOUR_OF_DAY)
                        data.minute = get(Calendar.MINUTE)
                    }
                    data.intervalDays = 0
                    data.repeatMonths = 0
                }
            }
            data.repeatMonths > 0 -> {
                AlarmTimeCalculator.calculateNextMonthInterval(
                    anchorTimeMs = data.startDateMs,
                    intervalMonths = data.repeatMonths
                ).also { nextMs ->
                    Calendar.getInstance().apply {
                        timeInMillis = nextMs
                        data.hour = get(Calendar.HOUR_OF_DAY)
                        data.minute = get(Calendar.MINUTE)
                    }
                    data.intervalDays = 0
                    data.repeatMinutes = 0
                }
            }
            else -> {
                val nextTime = AlarmTimeCalculator.calculateNextTime(
                    hour = data.hour,
                    minute = data.minute,
                    intervalDays = data.intervalDays,
                    startDateMs = data.startDateMs
                )
                data.intervalDays = nextTime.intervalDays
                data.startDateMs = nextTime.normalizedStartDateMs
                nextTime.nextTimeMs
            }
        }
        data.nextTimeMs = nextTriggerMs
        data.isActive = true

        // Clear the fired flag so the next fire is correctly tracked
        clearFiredFlag(context, data.id)

        // Persist BEFORE scheduling — if save fails, we never register a dangling alarm
        saveAlarm(context, data)

        // Build a proper Activity showIntent for AlarmClockInfo (not the broadcast PendingIntent)
        val showIntentRequestCode = data.id.hashCode().and(0x7fffffff) + 1
        val showIntent = alarmShowPendingIntent(
            context = context,
            requestCode = showIntentRequestCode,
            label = data.label,
            note = data.note,
            medicineName = data.medicineName,
            alarmId = data.id,
            isTest = false
        )

        // Proactive check: on Android 12+ the user may have revoked SCHEDULE_EXACT_ALARM.
        // scheduleAlarmClock() already has try-catch fallback, but an early check lets us
        // skip the doomed setAlarmClock() attempt entirely.
        val canUseAlarmClock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmMgr.canScheduleExactAlarms()
        } else {
            true // setAlarmClock() needs no extra permission before API 31
        }

        if (canUseAlarmClock) {
            scheduleAlarmClock(alarmMgr, nextTriggerMs, showIntent, operation)
        } else {
            // Permission denied — go straight to the fallback path
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerMs, operation)
            } else {
                alarmMgr.setExact(AlarmManager.RTC_WAKEUP, nextTriggerMs, operation)
            }
        }

        // ── Backup alarm (Redundant Scheduling) ──
        // Some ROMs (notably MIUI "优化") silently clear alarms. Register a second
        // alarm 2 minutes later as a safety net. AlarmReceiver checks a fired flag
        // to avoid double-ringing when the main alarm already triggered.
        val backupTriggerMs = nextTriggerMs + 2 * 60 * 1000L
        val backupRequestCode = data.id.hashCode().and(0x7fffffff) + 2
        val backupIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_FIRED
            putExtra("alarm_id", data.id)
            putExtra("label", data.label)
            putExtra("ringtone", data.ringtoneUri ?: "")
            putExtra("note", data.note)
            putExtra("medicine_name", data.medicineName)
            putExtra("vibrate", data.vibrate)
            putExtra("is_backup", true)
        }
        val backupOperation = PendingIntent.getBroadcast(
            context, backupRequestCode, backupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleAlarmClock(alarmMgr, backupTriggerMs, showIntent, backupOperation)

        // ── Post-schedule verification ──
        // Some ROMs accept setAlarmClock() but silently discard it. Verify that the
        // alarm actually landed in the system queue.
        try {
            val nextClock = alarmMgr.nextAlarmClock
            if (nextClock == null || nextClock.triggerTime != nextTriggerMs) {
                android.util.Log.w(
                    "AlarmScheduler",
                    "Alarm ${data.id}: verification mismatch — " +
                    "expected=$nextTriggerMs, actual=${nextClock?.triggerTime ?: "none"}"
                )
            }
        } catch (_: Exception) {
            // getNextAlarmClock() is informational only; failure here must not crash
        }

        // Keep a silent persistent notification while alarms are active.
        // This raises the process priority so the system is less likely to kill us.
        updatePersistentNotification(context)

        ReliabilityLogger.log(context, ReliabilityLogger.Event.SCHEDULE,
            "${data.label} @ ${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(data.nextTimeMs))}")

        // Ensure the 30-minute health-check alarm is running
        scheduleHealthCheck(context)
    }

    fun cancelById(context: Context, id: String) {
        val requestCode = id.hashCode().and(0x7fffffff)
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply { action = ACTION_ALARM_FIRED }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmMgr.cancel(pendingIntent)
        pendingIntent.cancel()

        // Also cancel the backup alarm
        val backupRequestCode = id.hashCode().and(0x7fffffff) + 2
        val backupIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_FIRED
            putExtra("is_backup", true)
        }
        val backupPendingIntent = PendingIntent.getBroadcast(
            context, backupRequestCode, backupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmMgr.cancel(backupPendingIntent)
        backupPendingIntent.cancel()

        // Clear fired flag
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove("alarm_fired_$id").apply()

        // Update persisted state
        loadAlarm(context, id)?.let { data ->
            data.isActive = false
            saveAlarm(context, data)
        }

        // Update or remove the persistent notification
        updatePersistentNotification(context)

        ReliabilityLogger.log(context, ReliabilityLogger.Event.CANCEL, "id=$id")
    }

    fun rescheduleNext(context: Context, id: String) {
        val data = loadAlarm(context, id) ?: return
        if (data.nextTimeMs <= 0L) return
        if (data.intervalDays == 0) {
            if (data.repeatMinutes > 0) {
                data.startDateMs = data.nextTimeMs + data.repeatMinutes * 60_000L
                schedule(context, data)
                return
            }
            if (data.repeatMonths > 0) {
                data.startDateMs = data.nextTimeMs
                schedule(context, data)
                return
            }
            data.isActive = false
            saveAlarm(context, data)
            return
        }
        // Use the already-scheduled nextTime as the new anchor, advance by interval
        val cal = Calendar.getInstance().apply { timeInMillis = data.nextTimeMs }
        data.hour = cal.get(Calendar.HOUR_OF_DAY)
        data.minute = cal.get(Calendar.MINUTE)
        schedule(context, data)
    }

    fun scheduleSnooze(context: Context, id: String, triggerAtMs: Long) {
        val data = loadAlarm(context, id) ?: return
        if (!data.isActive) return

        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context, data)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
        } else {
            alarmMgr.setExact(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
        }
    }

    fun scheduleReliabilityTest(context: Context, triggerAtMs: Long) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_FIRED
            putExtra("label", "测试响铃")
            putExtra("note", "周期闹钟测试响铃")
            putExtra("vibrate", true)
            putExtra(AlarmService.EXTRA_IS_TEST, true)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            RELIABILITY_TEST_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showIntent = alarmShowPendingIntent(
            context = context,
            requestCode = RELIABILITY_TEST_REQUEST_CODE + 1,
            label = "测试响铃",
            note = "周期闹钟测试响铃",
            medicineName = "",
            alarmId = null,
            isTest = true
        )

        scheduleAlarmClock(alarmMgr, triggerAtMs, showIntent, pendingIntent)
    }

    fun rescheduleAll(context: Context) {
        getAllAlarms(context)
            .filter { it.isActive }
            .forEach { schedule(context, it) }
    }

    /** Register an inexact repeating alarm every ~30 minutes that triggers
     *  rescheduleAll(). If a ROM silently clears our alarms, this will recover
     *  them within 30 minutes. */
    fun scheduleHealthCheck(context: Context) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, BootReceiver::class.java).apply {
            action = ACTION_HEALTH_CHECK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, HEALTH_CHECK_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmMgr.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 30 * 60 * 1000L,
                30 * 60 * 1000L,
                pendingIntent
            )
        } catch (_: Exception) {
            // Best-effort; failure must not crash
        }
    }

    private fun pendingIntent(context: Context, data: AlarmData): PendingIntent {
        val requestCode = data.id.hashCode().and(0x7fffffff)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_FIRED
            putExtra("alarm_id", data.id)
            putExtra("label", data.label)
            putExtra("ringtone", data.ringtoneUri ?: "")
            putExtra("note", data.note)
            putExtra("medicine_name", data.medicineName)
            putExtra("vibrate", data.vibrate)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleAlarmClock(
        alarmMgr: AlarmManager,
        triggerAtMs: Long,
        showIntent: PendingIntent,
        operation: PendingIntent
    ) {
        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMs, showIntent)
            alarmMgr.setAlarmClock(alarmClockInfo, operation)
        } catch (_: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, operation)
            } else {
                alarmMgr.setExact(AlarmManager.RTC_WAKEUP, triggerAtMs, operation)
            }
        }
    }

    private fun alarmShowPendingIntent(
        context: Context,
        requestCode: Int,
        label: String,
        note: String,
        medicineName: String,
        alarmId: String?,
        isTest: Boolean
    ): PendingIntent {
        val intent = Intent(context, AlarmRingingActivity::class.java).apply {
            putExtra(AlarmService.EXTRA_LABEL, label)
            putExtra(AlarmService.EXTRA_NOTE, note)
            putExtra(AlarmService.EXTRA_MEDICINE_NAME, medicineName)
            putExtra(AlarmService.EXTRA_IS_TEST, isTest)
            if (alarmId != null) putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ════════════════════════════════════
    //  POST-FIRE VERIFICATION
    // ════════════════════════════════════

    /** Scan active alarms for any that should have fired but didn't.
     *  Returns a list of alarm labels that are overdue. */
    fun findMissedAlarms(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        return getAllAlarms(context)
            .filter { it.isActive && it.nextTimeMs in 1..(now - 60_000) }
            .filter { !prefs.getBoolean("alarm_fired_${it.id}", false) }
            .map { it.label.ifEmpty { "周期闹钟" } }
    }

    /** Clear the fired flag before scheduling, so the next fire is tracked. */
    private fun clearFiredFlag(context: Context, id: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove("alarm_fired_$id").apply()
    }

    // ════════════════════════════════════
    //  PERSISTENT NOTIFICATION
    // ════════════════════════════════════

    /** Show a silent IMPORTANCE_MIN notification when alarms are active.
     *  This raises process priority so the system is less likely to kill us.
     *  The notification is invisible (no icon, no sound, no ticker). */
    private fun updatePersistentNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val hasActive = getAllAlarms(context).any { it.isActive }

        if (!hasActive) {
            cancelPersistentNotification(context)
            return
        }

        // Create channel once (silent, minimal importance)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PERSISTENT_CHANNEL_ID,
                "闹钟服务",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "保持闹钟后台运行"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            nm.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, AlarmListActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val contentPi = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, PERSISTENT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("闹钟运行中")
            .setContentText("周期闹钟正在后台等待触发")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(contentPi)
            .build()

        nm.notify(PERSISTENT_NOTIFICATION_ID, notification)
    }

    private fun cancelPersistentNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(PERSISTENT_NOTIFICATION_ID)
    }

    // ════════════════════════════════════
    //  LEGACY HELPERS (for backward compat)
    // ════════════════════════════════════

    /** Returns the first active alarm's next time, or 0 */
    fun getNextTimeMillis(context: Context): Long {
        return getAllAlarms(context).firstOrNull { it.isActive }?.nextTimeMs ?: 0L
    }

    fun getIntervalDays(context: Context): Int {
        return getAllAlarms(context).firstOrNull()?.intervalDays ?: INTERVAL_DEFAULT_DAYS
    }

    fun getStartDate(context: Context): Calendar? {
        val ms = getAllAlarms(context).firstOrNull()?.startDateMs ?: 0L
        return if (ms <= 0L) null else Calendar.getInstance().apply { timeInMillis = ms }
    }

    fun getNote(context: Context): String {
        return getAllAlarms(context).firstOrNull()?.note ?: ""
    }

    fun getRingtoneUri(context: Context): String? {
        return getAllAlarms(context).firstOrNull()?.ringtoneUri
    }

    fun getLabel(context: Context): String {
        return getAllAlarms(context).firstOrNull()?.label ?: "给妈挂号"
    }

    fun isAlarmActive(context: Context): Boolean {
        return getAllAlarms(context).any { it.isActive }
    }

    fun cancel(context: Context) {
        getAllAlarms(context).forEach { cancelById(context, it.id) }
    }

    fun rescheduleNext(context: Context) {
        getAllAlarms(context).forEach { rescheduleNext(context, it.id) }
    }
}
