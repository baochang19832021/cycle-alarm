package com.cyclealarm.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class UiAlarmSchedulePlannerTest {
    @Test
    fun regularDailyUsesYesterdayAnchorSoTodayCanStillRing() {
        val now = timeMs(2026, Calendar.JULY, 3, 7, 30)

        val plans = UiAlarmSchedulePlanner.planRegular(
            enabled = true,
            hour = 8,
            minute = 0,
            label = "工作日提醒",
            ringtoneUri = null,
            vibrate = true,
            selectedRules = setOf("每天"),
            nowMs = now
        )

        assertEquals(1, plans.size)
        assertEquals("alarm_ui_regular_daily", plans.first().id)
        assertEquals(1, plans.first().intervalDays)
        assertCalendar(plans.first().startDateMs, 2026, Calendar.JULY, 2, 0, 0)
    }

    @Test
    fun regularWeeklyCreatesOnePlanForEachSelectedWeekday() {
        val now = timeMs(2026, Calendar.JULY, 3, 9, 0) // Friday

        val plans = UiAlarmSchedulePlanner.planRegular(
            enabled = true,
            hour = 8,
            minute = 0,
            label = "工作日提醒",
            ringtoneUri = null,
            vibrate = true,
            selectedRules = setOf("每周一", "每周三", "每周五"),
            nowMs = now
        )

        assertEquals(listOf("alarm_ui_regular_mon", "alarm_ui_regular_wed", "alarm_ui_regular_fri"), plans.map { it.id })
        assertTrue(plans.all { it.intervalDays == 7 })
        assertCalendar(plans[0].startDateMs, 2026, Calendar.JUNE, 29, 0, 0)
        assertCalendar(plans[1].startDateMs, 2026, Calendar.JULY, 1, 0, 0)
        assertCalendar(plans[2].startDateMs, 2026, Calendar.JULY, 3, 0, 0)
    }

    @Test
    fun regularMonthlyCreatesMonthIntervalPlan() {
        val selectedDate = timeMs(2026, Calendar.JULY, 10, 12, 0)

        val plans = UiAlarmSchedulePlanner.planRegular(
            enabled = true,
            hour = 8,
            minute = 0,
            label = "还款提醒",
            ringtoneUri = null,
            vibrate = true,
            selectedRules = setOf("每月"),
            selectedDateMs = selectedDate,
            nowMs = timeMs(2026, Calendar.JULY, 3, 9, 0)
        )

        assertEquals(1, plans.size)
        assertEquals("alarm_ui_regular_month", plans.first().id)
        assertEquals(1, plans.first().repeatMonths)
        assertCalendar(plans.first().startDateMs, 2026, Calendar.JULY, 10, 8, 0)
    }

    @Test
    fun specialDayCycleUsesSelectedStartDateAndIntervalDays() {
        val start = timeMs(2026, Calendar.JULY, 1, 12, 0)

        val plans = UiAlarmSchedulePlanner.planSpecial(
            enabled = true,
            hour = 8,
            minute = 0,
            label = "复诊提醒",
            ringtoneUri = null,
            vibrate = true,
            unit = "天",
            value = 40,
            hours = 0,
            minutes = 0,
            selectedWeekdays = emptySet(),
            startDateMs = start,
            nowMs = timeMs(2026, Calendar.JULY, 3, 9, 0)
        )

        assertEquals(1, plans.size)
        assertEquals("alarm_ui_special_day", plans.first().id)
        assertEquals(40, plans.first().intervalDays)
        assertCalendar(plans.first().startDateMs, 2026, Calendar.JULY, 1, 0, 0)
    }

    @Test
    fun specialDayCycleClampsTo365Days() {
        val start = timeMs(2026, Calendar.JULY, 1, 12, 0)

        val plans = UiAlarmSchedulePlanner.planSpecial(
            enabled = true,
            hour = 8,
            minute = 0,
            label = "复诊提醒",
            ringtoneUri = null,
            vibrate = true,
            unit = "天",
            value = 999,
            hours = 0,
            minutes = 0,
            selectedWeekdays = emptySet(),
            startDateMs = start,
            nowMs = timeMs(2026, Calendar.JULY, 3, 9, 0)
        )

        assertEquals(1, plans.size)
        assertEquals(365, plans.first().intervalDays)
        assertEquals("每 365 天", plans.first().note)
    }

    @Test
    fun specialMonthCycleCreatesMonthIntervalPlan() {
        val start = timeMs(2026, Calendar.JULY, 10, 12, 0)

        val plans = UiAlarmSchedulePlanner.planSpecial(
            enabled = true,
            hour = 8,
            minute = 0,
            label = "复诊提醒",
            ringtoneUri = null,
            vibrate = true,
            unit = "月",
            value = 3,
            hours = 0,
            minutes = 0,
            selectedWeekdays = emptySet(),
            startDateMs = start,
            nowMs = timeMs(2026, Calendar.JULY, 3, 9, 0)
        )

        assertEquals(1, plans.size)
        assertEquals("alarm_ui_special_month", plans.first().id)
        assertEquals(3, plans.first().repeatMonths)
        assertCalendar(plans.first().startDateMs, 2026, Calendar.JULY, 10, 8, 0)
    }

    @Test
    fun specialHourMinuteCycleCreatesMinuteIntervalPlan() {
        val plans = UiAlarmSchedulePlanner.planSpecial(
            enabled = true,
            hour = 8,
            minute = 0,
            label = "复诊提醒",
            ringtoneUri = null,
            vibrate = true,
            unit = "时分",
            value = 40,
            hours = 2,
            minutes = 30,
            selectedWeekdays = emptySet(),
            startDateMs = 0L,
            nowMs = timeMs(2026, Calendar.JULY, 3, 9, 0)
        )

        assertEquals(1, plans.size)
        assertEquals("alarm_ui_special_time", plans.first().id)
        assertEquals(150, plans.first().repeatMinutes)
        assertEquals(0, plans.first().intervalDays)
        assertCalendar(plans.first().startDateMs, 2026, Calendar.JULY, 3, 8, 0)
    }

    @Test
    fun shiftCycleCreatesPlansForWorkDaysAndReminderTimes() {
        val start = timeMs(2026, Calendar.JULY, 1, 0, 0)
        val now = timeMs(2026, Calendar.JULY, 3, 9, 0)

        val plans = UiAlarmSchedulePlanner.planShift(
            enabled = true,
            cycleDays = 4,
            startDateMs = start,
            daySummaries = listOf("07:30", "07:30 +1", "19:30", "休"),
            label = "轮班闹钟",
            ringtoneUri = null,
            vibrate = true,
            nowMs = now
        )

        assertEquals(
            listOf("alarm_ui_shift_day1_time1", "alarm_ui_shift_day2_time1", "alarm_ui_shift_day3_time1"),
            plans.map { it.id }
        )
        assertTrue(plans.all { it.intervalDays == 4 })
        assertCalendar(plans[0].startDateMs, 2026, Calendar.JULY, 1, 0, 0)
        assertCalendar(plans[1].startDateMs, 2026, Calendar.JULY, 3, 0, 0)
        assertCalendar(plans[2].startDateMs, 2026, Calendar.JUNE, 29, 0, 0)
    }

    @Test
    fun shiftPreviewTimeTextUsesFirstWorkdayTime() {
        val text = UiAlarmSchedulePlanner.shiftPreviewTimeText(listOf("休", "05:30 +1", "19:30", "休"))

        assertEquals("05:30", text)
    }

    @Test
    fun shiftCalendarSummaryRepeatsAccordingToSavedCycle() {
        val start = timeMs(2026, Calendar.JULY, 1, 0, 0)
        val target = timeMs(2026, Calendar.JULY, 4, 12, 0)

        val summary = UiAlarmSchedulePlanner.shiftCalendarSummary(
            startDateMs = start,
            cycleDays = 4,
            daySummaries = listOf("07:30", "07:30 +1", "19:30", "休"),
            targetDateMs = target
        )

        assertEquals("休", summary)
    }

    @Test
    fun shiftCalendarReminderLinesReturnsAllTimesForWorkday() {
        val start = timeMs(2026, Calendar.JULY, 1, 0, 0)
        val target = timeMs(2026, Calendar.JULY, 2, 12, 0)

        val lines = UiAlarmSchedulePlanner.shiftCalendarReminderLines(
            startDateMs = start,
            cycleDays = 4,
            daySummaries = listOf("07:30", "07:30 +1", "19:30", "休"),
            targetDateMs = target
        )

        assertEquals(listOf("07:30 +1"), lines)
    }

    @Test
    fun firstShiftDayTimeChangeAppliesToAllWorkDaysAndKeepsRestDays() {
        val nextSummaries = UiAlarmSchedulePlanner.applyShiftDaySummaryChange(
            currentSummaries = listOf("07:30", "07:30", "07:30", "07:30", "07:30", "07:30", "休"),
            changedIndex = 0,
            newSummary = "05:30"
        )

        assertEquals(listOf("05:30", "05:30", "05:30", "05:30", "05:30", "05:30", "休"), nextSummaries)
    }

    @Test
    fun firstShiftDayCanBeReenabledAfterRest() {
        val nextSummaries = UiAlarmSchedulePlanner.applyShiftDaySummaryChange(
            currentSummaries = listOf("休", "05:30", "休", "休"),
            changedIndex = 0,
            newSummary = "07:30"
        )

        assertEquals(listOf("07:30", "07:30", "休", "休"), nextSummaries)
    }

    @Test
    fun laterShiftDayTimeChangeOnlyAppliesToThatDay() {
        val nextSummaries = UiAlarmSchedulePlanner.applyShiftDaySummaryChange(
            currentSummaries = listOf("05:30", "05:30", "05:30", "休"),
            changedIndex = 1,
            newSummary = "19:30"
        )

        assertEquals(listOf("05:30", "19:30", "05:30", "休"), nextSummaries)
    }

    private fun timeMs(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun assertCalendar(actualMs: Long, year: Int, month: Int, day: Int, hour: Int, minute: Int) {
        val cal = Calendar.getInstance().apply { timeInMillis = actualMs }
        assertEquals(year, cal.get(Calendar.YEAR))
        assertEquals(month, cal.get(Calendar.MONTH))
        assertEquals(day, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(hour, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(minute, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }
}
