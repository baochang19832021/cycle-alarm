package com.cyclealarm.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.UUID

object AlarmScheduler {
    const val PREFS_NAME = "CycleAlarmPrefs"
    const val ALARMS_DIR = "cycle_alarms"
    const val INTERVAL_DEFAULT_DAYS = 40
    const val ACTION_ALARM_FIRED = "com.cyclealarm.app.ALARM_FIRED"

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
        var startDateMs: Long = 0L,
        var nextTimeMs: Long = 0L,
        var ringtoneUri: String? = null,
        var label: String = "",
        var note: String = "",
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
            put("startDateMs", data.startDateMs)
            put("nextTimeMs", data.nextTimeMs)
            put("ringtoneUri", data.ringtoneUri ?: "")
            put("label", data.label)
            put("note", data.note)
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
                startDateMs = json.optLong("startDateMs", 0L),
                nextTimeMs = json.optLong("nextTimeMs", 0L),
                ringtoneUri = json.optString("ringtoneUri", "").ifEmpty { null },
                label = json.optString("label", ""),
                note = json.optString("note", ""),
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
        data.intervalDays = data.intervalDays.coerceIn(0, 365)

        val pendingIntent = pendingIntent(context, data)

        // Calculate target time
        val anchor = if (data.startDateMs > 0L) {
            Calendar.getInstance().apply { timeInMillis = data.startDateMs }
        } else {
            Calendar.getInstance()
        }

        val target = anchor.clone() as Calendar
        target.apply {
            set(Calendar.HOUR_OF_DAY, data.hour)
            set(Calendar.MINUTE, data.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, data.intervalDays)
        }

        val now = Calendar.getInstance()
        while (!target.after(now)) {
            target.add(Calendar.DAY_OF_YEAR, if (data.intervalDays == 0) 1 else data.intervalDays)
        }

        data.startDateMs = anchor.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        data.nextTimeMs = target.timeInMillis
        data.isActive = true

        // Schedule
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(target.timeInMillis, pendingIntent)
            alarmMgr.setAlarmClock(alarmClockInfo, pendingIntent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
        } else {
            alarmMgr.setExact(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
        }

        // Persist
        saveAlarm(context, data)
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

        // Update persisted state
        loadAlarm(context, id)?.let { data ->
            data.isActive = false
            saveAlarm(context, data)
        }
    }

    fun rescheduleNext(context: Context, id: String) {
        val data = loadAlarm(context, id) ?: return
        if (data.nextTimeMs <= 0L) return
        if (data.intervalDays == 0) {
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

    fun rescheduleAll(context: Context) {
        getAllAlarms(context)
            .filter { it.isActive }
            .forEach { schedule(context, it) }
    }

    private fun pendingIntent(context: Context, data: AlarmData): PendingIntent {
        val requestCode = data.id.hashCode().and(0x7fffffff)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_FIRED
            putExtra("alarm_id", data.id)
            putExtra("label", data.label)
            putExtra("ringtone", data.ringtoneUri ?: "")
            putExtra("note", data.note)
            putExtra("vibrate", data.vibrate)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
