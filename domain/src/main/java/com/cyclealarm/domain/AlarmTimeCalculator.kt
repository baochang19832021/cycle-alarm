package com.cyclealarm.domain

import java.util.Calendar

object AlarmTimeCalculator {

    data class NextTimeResult(
        val nextTimeMs: Long,
        val intervalDays: Int,
        val normalizedStartDateMs: Long
    )

    /**
     * Calculate the next trigger time based on hour, minute, interval, and start date.
     *
     * @param hour target hour (0-23)
     * @param minute target minute (0-59)
     * @param intervalDays repeat interval in days. 0 means one-shot (no repeat).
     * @param startDateMs anchor date in ms. Used as the reference point for interval calculation.
     * @param nowMs current time in ms. Defaults to System.currentTimeMillis().
     */
    fun calculateNextTime(
        hour: Int,
        minute: Int,
        intervalDays: Int,
        startDateMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): NextTimeResult {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        val safeInterval = intervalDays.coerceIn(0, 365)

        val now = Calendar.getInstance().apply { timeInMillis = nowMs }

        // Normalize start date to midnight. Use nowMs as fallback anchor.
        val normalizedStart = Calendar.getInstance().apply {
            timeInMillis = if (startDateMs > 0L) startOfDay(startDateMs) else startOfDay(nowMs)
        }

        val candidate = Calendar.getInstance().apply {
            timeInMillis = normalizedStart.timeInMillis
            // First occurrence is startDate + intervalDays (not startDate itself)
            if (safeInterval > 0) {
                add(Calendar.DAY_OF_YEAR, safeInterval)
            }
            set(Calendar.HOUR_OF_DAY, safeHour)
            set(Calendar.MINUTE, safeMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (safeInterval > 0) {
            while (!candidate.after(now)) {
                candidate.add(Calendar.DAY_OF_YEAR, safeInterval)
            }
        } else {
            // One-shot: if the target time today already passed, use tomorrow
            if (!candidate.after(now)) {
                candidate.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        return NextTimeResult(
            nextTimeMs = candidate.timeInMillis,
            intervalDays = safeInterval,
            normalizedStartDateMs = normalizedStart.timeInMillis
        )
    }

    private fun startOfDay(ms: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = ms
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /**
     * Calculate the next trigger for a minute-interval alarm.
     * Advances from anchorTimeMs by multiples of intervalMinutes until past nowMs.
     */
    fun calculateNextMinuteInterval(
        anchorTimeMs: Long,
        intervalMinutes: Int,
        nowMs: Long = System.currentTimeMillis()
    ): Long {
        val safeInterval = intervalMinutes.coerceAtLeast(1)
        val anchor = Calendar.getInstance().apply { timeInMillis = anchorTimeMs }
        val now = Calendar.getInstance().apply { timeInMillis = nowMs }

        val candidate = Calendar.getInstance().apply {
            timeInMillis = anchor.timeInMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        while (!candidate.after(now)) {
            candidate.add(Calendar.MINUTE, safeInterval)
        }

        return candidate.timeInMillis
    }

    /**
     * Calculate the next trigger for a month-interval alarm.
     * Handles end-of-month clamping (e.g., Jan 31 → Feb 28/29).
     */
    fun calculateNextMonthInterval(
        anchorTimeMs: Long,
        intervalMonths: Int,
        nowMs: Long = System.currentTimeMillis()
    ): Long {
        val safeInterval = intervalMonths.coerceAtLeast(1)
        val anchor = Calendar.getInstance().apply { timeInMillis = anchorTimeMs }
        val now = Calendar.getInstance().apply { timeInMillis = nowMs }

        val targetDay = anchor.get(Calendar.DAY_OF_MONTH)

        val candidate = Calendar.getInstance().apply {
            timeInMillis = anchor.timeInMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        while (!candidate.after(now)) {
            candidate.add(Calendar.MONTH, safeInterval)
            // Clamp to last day of month if the target day exceeds it
            val maxDay = candidate.getActualMaximum(Calendar.DAY_OF_MONTH)
            if (targetDay > maxDay) {
                candidate.set(Calendar.DAY_OF_MONTH, maxDay)
            } else {
                candidate.set(Calendar.DAY_OF_MONTH, targetDay)
            }
        }

        return candidate.timeInMillis
    }
}
