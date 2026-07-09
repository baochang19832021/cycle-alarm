package com.cyclealarm.domain

import java.util.Calendar
import java.util.Locale

object UiAlarmSchedulePlanner {
    data class SchedulePlan(
        val id: String,
        val hour: Int,
        val minute: Int,
        val intervalDays: Int,
        val startDateMs: Long,
        val label: String,
        val note: String,
        val ringtoneUri: String?,
        val vibrate: Boolean,
        val repeatMinutes: Int = 0,
        val repeatMonths: Int = 0,
        val medicineName: String = ""
    )

    fun planRegular(
        enabled: Boolean,
        hour: Int,
        minute: Int,
        label: String,
        ringtoneUri: String?,
        vibrate: Boolean,
        selectedRules: Set<String>,
        selectedDateMs: Long = 0L,
        nowMs: Long = System.currentTimeMillis()
    ): List<SchedulePlan> {
        if (!enabled || selectedRules.isEmpty()) return emptyList()

        if ("每天" in selectedRules) {
            val firstTrigger = nextDateAtOrAfter(nowMs, 0, hour, minute)
            return listOf(
                plan(
                    id = "alarm_ui_regular_daily",
                    firstTriggerMs = firstTrigger,
                    intervalDays = 1,
                    hour = hour,
                    minute = minute,
                    label = label,
                    note = "每天",
                    ringtoneUri = ringtoneUri,
                    vibrate = vibrate
                )
            )
        }

        if ("法定工作日" in selectedRules) {
            val firstTrigger = LegalWorkdayHelper.nextWorkdayAtOrAfter(nowMs, hour, minute)
            return listOf(
                plan(
                    id = "alarm_ui_regular_workday",
                    firstTriggerMs = firstTrigger,
                    intervalDays = 1,
                    hour = hour,
                    minute = minute,
                    label = label,
                    note = "法定工作日",
                    ringtoneUri = ringtoneUri,
                    vibrate = vibrate
                )
            )
        }

        val plans = mutableListOf<SchedulePlan>()
        plans += WEEKDAY_RULES.mapNotNull { (rule, weekday) ->
            if (rule !in selectedRules) return@mapNotNull null
            val firstTrigger = nextWeekdayAtOrAfter(nowMs, weekday, hour, minute)
            plan(
                id = "alarm_ui_regular_${weekdayId(weekday)}",
                firstTriggerMs = firstTrigger,
                intervalDays = 7,
                hour = hour,
                minute = minute,
                label = label,
                note = rule,
                ringtoneUri = ringtoneUri,
                vibrate = vibrate
            )
        }
        if ("每月" in selectedRules) {
            plans += monthPlan(
                id = "alarm_ui_regular_month",
                startDateMs = selectedDateMs,
                nowMs = nowMs,
                hour = hour,
                minute = minute,
                repeatMonths = 1,
                label = label,
                note = "每月",
                ringtoneUri = ringtoneUri,
                vibrate = vibrate
            )
        }
        if ("每年" in selectedRules) {
            plans += monthPlan(
                id = "alarm_ui_regular_year",
                startDateMs = selectedDateMs,
                nowMs = nowMs,
                hour = hour,
                minute = minute,
                repeatMonths = 12,
                label = label,
                note = "每年",
                ringtoneUri = ringtoneUri,
                vibrate = vibrate
            )
        }
        return plans
    }

    fun planSpecial(
        enabled: Boolean,
        hour: Int,
        minute: Int,
        label: String,
        ringtoneUri: String?,
        vibrate: Boolean,
        unit: String,
        value: Int,
        hours: Int,
        minutes: Int,
        selectedWeekdays: Set<String>,
        startDateMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        medicineName: String = ""
    ): List<SchedulePlan> {
        if (!enabled) return emptyList()
        return when (unit) {
            "天" -> {
                val intervalDays = value.coerceIn(1, 365)
                listOf(
                    SchedulePlan(
                        id = "alarm_ui_special_day",
                        hour = hour.coerceIn(0, 23),
                        minute = minute.coerceIn(0, 59),
                        intervalDays = intervalDays,
                        startDateMs = startOfDay(if (startDateMs > 0L) startDateMs else nowMs),
                        label = label,
                        note = "每 $intervalDays 天",
                        ringtoneUri = ringtoneUri,
                        vibrate = vibrate,
                        medicineName = medicineName
                    )
                )
            }
            "周" -> {
                val intervalDays = value.coerceIn(1, 52) * 7
                selectedWeekdays.sortedBy { WEEKDAY_TEXT[it] ?: 99 }.mapNotNull { day ->
                    val weekday = WEEKDAY_TEXT[day] ?: return@mapNotNull null
                    val firstTrigger = nextWeekdayAtOrAfter(nowMs, weekday, hour, minute)
                    plan(
                        id = "alarm_ui_special_week_${weekdayId(weekday)}",
                        medicineName = medicineName,
                        firstTriggerMs = firstTrigger,
                        intervalDays = intervalDays,
                        hour = hour,
                        minute = minute,
                        label = label,
                        note = "每 ${value.coerceIn(1, 52)} 周 周$day",
                        ringtoneUri = ringtoneUri,
                        vibrate = vibrate
                    )
                }
            }
            "月" -> listOf(
                monthPlan(
                    id = "alarm_ui_special_month",
                    medicineName = medicineName,
                    startDateMs = startDateMs,
                    nowMs = nowMs,
                    hour = hour,
                    minute = minute,
                    repeatMonths = value.coerceIn(1, 36),
                    label = label,
                    note = "每 ${value.coerceIn(1, 36)} 月",
                    ringtoneUri = ringtoneUri,
                    vibrate = vibrate
                )
            )
            "年" -> listOf(
                monthPlan(
                    id = "alarm_ui_special_year",
                    medicineName = medicineName,
                    startDateMs = startDateMs,
                    nowMs = nowMs,
                    hour = hour,
                    minute = minute,
                    repeatMonths = value.coerceIn(1, 10) * 12,
                    label = label,
                    note = "每 ${value.coerceIn(1, 10)} 年",
                    ringtoneUri = ringtoneUri,
                    vibrate = vibrate
                )
            )
            "时分" -> {
                val totalMinutes = hours.coerceAtLeast(0) * 60 + minutes.coerceAtLeast(0)
                if (totalMinutes <= 0) {
                    emptyList()
                } else {
                    val anchor = Calendar.getInstance().apply {
                        timeInMillis = if (startDateMs > 0L) startDateMs else nowMs
                        set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
                        set(Calendar.MINUTE, minute.coerceIn(0, 59))
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    listOf(
                        SchedulePlan(
                            id = "alarm_ui_special_time",
                            hour = hour.coerceIn(0, 23),
                            minute = minute.coerceIn(0, 59),
                            intervalDays = 0,
                            startDateMs = anchor.timeInMillis,
                            label = label,
                            note = repeatMinuteNote(hours, minutes),
                            ringtoneUri = ringtoneUri,
                            vibrate = vibrate,
                            repeatMinutes = totalMinutes.coerceIn(1, 24 * 60 * 366),
                            medicineName = medicineName
                        )
                    )
                }
            }
            else -> emptyList()
        }
    }

    fun planShift(
        enabled: Boolean,
        cycleDays: Int,
        startDateMs: Long,
        daySummaries: List<String>,
        label: String,
        ringtoneUri: String?,
        vibrate: Boolean,
        nowMs: Long = System.currentTimeMillis(),
        medicineName: String = ""
    ): List<SchedulePlan> {
        if (!enabled) return emptyList()
        val safeCycleDays = cycleDays.coerceIn(1, 31)
        val start = startOfDay(if (startDateMs > 0L) startDateMs else nowMs)
        val result = mutableListOf<SchedulePlan>()

        daySummaries.take(safeCycleDays).forEachIndexed { dayIndex, summary ->
            parseShiftReminderTimes(summary).forEachIndexed { timeIndex, parsed ->
                val firstTrigger = firstFutureShiftTrigger(
                    startMs = start,
                    cycleDays = safeCycleDays,
                    dayOffset = dayIndex + parsed.extraDayOffset,
                    hour = parsed.hour,
                    minute = parsed.minute,
                    nowMs = nowMs
                )
                result += plan(
                    id = "alarm_ui_shift_day${dayIndex + 1}_time${timeIndex + 1}",
                    firstTriggerMs = firstTrigger,
                    intervalDays = safeCycleDays,
                    hour = parsed.hour,
                    minute = parsed.minute,
                    label = label,
                    note = "第${dayIndex + 1}天",
                    ringtoneUri = ringtoneUri,
                    vibrate = vibrate,
                    medicineName = medicineName
                )
            }
        }
        return result
    }

    fun shiftPreviewTimeText(daySummaries: List<String>): String {
        return daySummaries.asSequence()
            .filter { it.isNotBlank() && !it.contains("休") }
            .mapNotNull { summary ->
                Regex("""\d{1,2}:\d{2}""").find(summary)?.value
            }
            .firstOrNull()
            ?: "休"
    }

    fun shiftCalendarSummary(startDateMs: Long, cycleDays: Int, daySummaries: List<String>, targetDateMs: Long): String {
        val safeCycleDays = cycleDays.coerceIn(1, 31)
        if (daySummaries.isEmpty()) return "休"
        val offset = dayOffset(startDateMs, targetDateMs)
        val index = ((offset % safeCycleDays) + safeCycleDays) % safeCycleDays
        return daySummaries.getOrNull(index) ?: "休"
    }

    fun shiftCalendarReminderLines(startDateMs: Long, cycleDays: Int, daySummaries: List<String>, targetDateMs: Long): List<String> {
        val summary = shiftCalendarSummary(startDateMs, cycleDays, daySummaries, targetDateMs)
        if (summary.contains("休")) return emptyList()
        return summary.split("、")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun applyShiftDaySummaryChange(
        currentSummaries: List<String>,
        changedIndex: Int,
        newSummary: String
    ): List<String> {
        if (changedIndex !in currentSummaries.indices) return currentSummaries
        val normalized = newSummary.ifBlank { "休" }
        return currentSummaries.mapIndexed { index, summary ->
            if (index == changedIndex) normalized else summary
        }
    }

    private fun plan(
        id: String,
        firstTriggerMs: Long,
        intervalDays: Int,
        hour: Int,
        minute: Int,
        label: String,
        note: String,
        ringtoneUri: String?,
        vibrate: Boolean,
        medicineName: String = ""
    ): SchedulePlan {
        val anchor = Calendar.getInstance().apply {
            timeInMillis = firstTriggerMs
            add(Calendar.DAY_OF_YEAR, -intervalDays.coerceAtLeast(1))
        }
        return SchedulePlan(
            id = id,
            hour = hour.coerceIn(0, 23),
            minute = minute.coerceIn(0, 59),
            intervalDays = intervalDays.coerceIn(1, 365),
            startDateMs = startOfDay(anchor.timeInMillis),
            label = label,
            note = note,
            ringtoneUri = ringtoneUri,
            vibrate = vibrate,
            medicineName = medicineName
        )
    }

    private fun monthPlan(
        id: String,
        startDateMs: Long,
        nowMs: Long,
        hour: Int,
        minute: Int,
        repeatMonths: Int,
        label: String,
        note: String,
        ringtoneUri: String?,
        vibrate: Boolean,
        medicineName: String = ""
    ): SchedulePlan {
        val anchor = Calendar.getInstance().apply {
            timeInMillis = if (startDateMs > 0L) startDateMs else nowMs
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return SchedulePlan(
            id = id,
            hour = hour.coerceIn(0, 23),
            minute = minute.coerceIn(0, 59),
            intervalDays = 0,
            startDateMs = anchor.timeInMillis,
            label = label,
            note = note,
            ringtoneUri = ringtoneUri,
            vibrate = vibrate,
            repeatMonths = repeatMonths.coerceIn(1, 12 * 50),
            medicineName = medicineName
        )
    }

    private fun nextDateAtOrAfter(nowMs: Long, dayOffset: Int, hour: Int, minute: Int): Long {
        val target = Calendar.getInstance().apply {
            timeInMillis = nowMs
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(Calendar.getInstance().apply { timeInMillis = nowMs })) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }

    private fun nextWeekdayAtOrAfter(nowMs: Long, weekday: Int, hour: Int, minute: Int): Long {
        val now = Calendar.getInstance().apply { timeInMillis = nowMs }
        val target = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var guard = 0
        while ((target.get(Calendar.DAY_OF_WEEK) != weekday || !target.after(now)) && guard < 8) {
            target.add(Calendar.DAY_OF_YEAR, 1)
            guard++
        }
        return target.timeInMillis
    }

    private fun firstFutureShiftTrigger(
        startMs: Long,
        cycleDays: Int,
        dayOffset: Int,
        hour: Int,
        minute: Int,
        nowMs: Long
    ): Long {
        val now = Calendar.getInstance().apply { timeInMillis = nowMs }
        val target = Calendar.getInstance().apply {
            timeInMillis = startMs
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        while (!target.after(now)) {
            target.add(Calendar.DAY_OF_YEAR, cycleDays)
        }
        return target.timeInMillis
    }

    private data class ShiftReminderTime(val hour: Int, val minute: Int, val extraDayOffset: Int)

    private fun parseShiftReminderTimes(summary: String): List<ShiftReminderTime> {
        if (summary.contains("休")) return emptyList()
        return summary.split("、")
            .mapNotNull { raw ->
                val item = raw.trim()
                val match = Regex("""(\d{1,2}):(\d{2})(\s*\+1)?""").find(item) ?: return@mapNotNull null
                val hour = match.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: return@mapNotNull null
                val minute = match.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: return@mapNotNull null
                val extra = if (match.groupValues[3].isNotBlank()) 1 else 0
                ShiftReminderTime(hour, minute, extra)
            }
    }

    private fun repeatMinuteNote(hours: Int, minutes: Int): String =
        when {
            hours > 0 && minutes > 0 -> "每 $hours 小时 $minutes 分钟"
            hours > 0 -> "每 $hours 小时"
            else -> "每 $minutes 分钟"
        }

    private fun startOfDay(ms: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = ms
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun dayOffset(startDateMs: Long, targetDateMs: Long): Int {
        val start = startOfDay(startDateMs)
        val target = startOfDay(targetDateMs)
        val diff = (target - start) / (24L * 60L * 60L * 1000L)
        return diff.toInt()
    }

    private fun weekdayId(weekday: Int): String =
        when (weekday) {
            Calendar.MONDAY -> "mon"
            Calendar.TUESDAY -> "tue"
            Calendar.WEDNESDAY -> "wed"
            Calendar.THURSDAY -> "thu"
            Calendar.FRIDAY -> "fri"
            Calendar.SATURDAY -> "sat"
            Calendar.SUNDAY -> "sun"
            else -> weekday.toString().lowercase(Locale.US)
        }

    private val WEEKDAY_RULES = listOf(
        "每周一" to Calendar.MONDAY,
        "每周二" to Calendar.TUESDAY,
        "每周三" to Calendar.WEDNESDAY,
        "每周四" to Calendar.THURSDAY,
        "每周五" to Calendar.FRIDAY,
        "每周六" to Calendar.SATURDAY,
        "每周日" to Calendar.SUNDAY
    )

    private val WEEKDAY_TEXT = mapOf(
        "一" to Calendar.MONDAY,
        "二" to Calendar.TUESDAY,
        "三" to Calendar.WEDNESDAY,
        "四" to Calendar.THURSDAY,
        "五" to Calendar.FRIDAY,
        "六" to Calendar.SATURDAY,
        "日" to Calendar.SUNDAY
    )
}
