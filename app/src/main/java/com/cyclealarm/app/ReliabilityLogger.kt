package com.cyclealarm.app

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Simple append-only reliability log file.
 * Logs schedule, fire, failure, and permission-change events so users
 * can export the file when reporting missed-alarm issues.
 */
object ReliabilityLogger {

    private const val LOG_FILE = "reliability_log.txt"
    private const val MAX_LINES = 2000

    enum class Event(val label: String) {
        SCHEDULE("调度"),
        FIRE("触发"),
        MISS("遗漏"),
        CANCEL("取消"),
        PERMISSION("权限"),
        BOOT("开机重排"),
        ERROR("错误")
    }

    fun log(context: Context, event: Event, detail: String) {
        try {
            val file = File(context.filesDir, LOG_FILE)
            val ts = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date())
            val line = "[$ts] ${event.label} $detail\n"

            // Keep last MAX_LINES to prevent unbounded growth
            val lines = if (file.exists()) file.readLines().takeLast(MAX_LINES - 1) else emptyList()
            file.writeText((lines + line).joinToString(""))
        } catch (_: Exception) {
            // Logging failure must never crash
        }
    }

    fun readRecent(context: Context, count: Int = 200): String {
        return try {
            val file = File(context.filesDir, LOG_FILE)
            if (!file.exists()) "(日志为空)"
            else file.readLines().takeLast(count).joinToString("\n")
        } catch (_: Exception) {
            "(无法读取日志)"
        }
    }
}
