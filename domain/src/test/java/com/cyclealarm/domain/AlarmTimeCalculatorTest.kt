package com.cyclealarm.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AlarmTimeCalculatorTest {
    @Test
    fun zeroIntervalSchedulesOneTimeFutureTodayWhenTimeHasNotPassed() {
        val now = timeMs(2026, Calendar.JULY, 1, 7, 30)

        val result = AlarmTimeCalculator.calculateNextTime(
            hour = 8,
            minute = 0,
            intervalDays = 0,
            startDateMs = 0L,
            nowMs = now
        )

        assertEquals(0, result.intervalDays)
        assertCalendar(result.nextTimeMs, 2026, Calendar.JULY, 1, 8, 0)
        assertStartOfDay(result.normalizedStartDateMs, 2026, Calendar.JULY, 1)
    }

    @Test
    fun zeroIntervalAdvancesToFutureWhenTimeHasPassed() {
        val now = timeMs(2026, Calendar.JULY, 1, 9, 0)

        val result = AlarmTimeCalculator.calculateNextTime(
            hour = 8,
            minute = 0,
            intervalDays = 0,
            startDateMs = 0L,
            nowMs = now
        )

        assertEquals(0, result.intervalDays)
        assertCalendar(result.nextTimeMs, 2026, Calendar.JULY, 2, 8, 0)
    }

    @Test
    fun oneDayIntervalSchedulesTomorrowFromStartDate() {
        val now = timeMs(2026, Calendar.JULY, 1, 7, 0)
        val start = timeMs(2026, Calendar.JULY, 1, 12, 30)

        val result = AlarmTimeCalculator.calculateNextTime(
            hour = 8,
            minute = 15,
            intervalDays = 1,
            startDateMs = start,
            nowMs = now
        )

        assertEquals(1, result.intervalDays)
        assertCalendar(result.nextTimeMs, 2026, Calendar.JULY, 2, 8, 15)
        assertStartOfDay(result.normalizedStartDateMs, 2026, Calendar.JULY, 1)
    }

    @Test
    fun fortyDayIntervalUsesDefaultCycle() {
        val now = timeMs(2026, Calendar.JULY, 1, 7, 0)
        val start = timeMs(2026, Calendar.JULY, 1, 0, 0)

        val result = AlarmTimeCalculator.calculateNextTime(
            hour = 6,
            minute = 45,
            intervalDays = 40,
            startDateMs = start,
            nowMs = now
        )

        assertEquals(40, result.intervalDays)
        assertCalendar(result.nextTimeMs, 2026, Calendar.AUGUST, 10, 6, 45)
    }

    @Test
    fun passedTargetAdvancesByIntervalUntilFuture() {
        val now = timeMs(2026, Calendar.JULY, 20, 9, 0)
        val start = timeMs(2026, Calendar.JULY, 1, 0, 0)

        val result = AlarmTimeCalculator.calculateNextTime(
            hour = 8,
            minute = 0,
            intervalDays = 7,
            startDateMs = start,
            nowMs = now
        )

        assertCalendar(result.nextTimeMs, 2026, Calendar.JULY, 22, 8, 0)
    }

    @Test
    fun missingStartDateUsesCurrentDateAsAnchor() {
        val now = timeMs(2026, Calendar.JULY, 1, 7, 30)

        val result = AlarmTimeCalculator.calculateNextTime(
            hour = 8,
            minute = 0,
            intervalDays = 3,
            startDateMs = 0L,
            nowMs = now
        )

        assertCalendar(result.nextTimeMs, 2026, Calendar.JULY, 4, 8, 0)
        assertStartOfDay(result.normalizedStartDateMs, 2026, Calendar.JULY, 1)
    }

    @Test
    fun invalidIntervalIsClampedToSupportedRange() {
        val now = timeMs(2026, Calendar.JULY, 1, 7, 30)

        val tooLow = AlarmTimeCalculator.calculateNextTime(
            hour = 8,
            minute = 0,
            intervalDays = -5,
            startDateMs = 0L,
            nowMs = now
        )
        val tooHigh = AlarmTimeCalculator.calculateNextTime(
            hour = 8,
            minute = 0,
            intervalDays = 999,
            startDateMs = 0L,
            nowMs = now
        )

        assertEquals(0, tooLow.intervalDays)
        assertEquals(365, tooHigh.intervalDays)
    }

    @Test
    fun calculatedTimeClearsSecondAndMillisecond() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 1, 7, 30, 42)
            set(Calendar.MILLISECOND, 123)
        }.timeInMillis

        val result = AlarmTimeCalculator.calculateNextTime(
            hour = 8,
            minute = 0,
            intervalDays = 1,
            startDateMs = 0L,
            nowMs = now
        )

        val cal = Calendar.getInstance().apply { timeInMillis = result.nextTimeMs }
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
        assertTrue(result.nextTimeMs > now)
    }

    @Test
    fun minuteIntervalUsesAnchorWhenItIsStillFuture() {
        val now = timeMs(2026, Calendar.JULY, 1, 7, 30)
        val anchor = timeMs(2026, Calendar.JULY, 1, 8, 0)

        val next = AlarmTimeCalculator.calculateNextMinuteInterval(
            anchorTimeMs = anchor,
            intervalMinutes = 150,
            nowMs = now
        )

        assertCalendar(next, 2026, Calendar.JULY, 1, 8, 0)
    }

    @Test
    fun minuteIntervalAdvancesUntilFuture() {
        val now = timeMs(2026, Calendar.JULY, 1, 13, 0)
        val anchor = timeMs(2026, Calendar.JULY, 1, 8, 0)

        val next = AlarmTimeCalculator.calculateNextMinuteInterval(
            anchorTimeMs = anchor,
            intervalMinutes = 150,
            nowMs = now
        )

        assertCalendar(next, 2026, Calendar.JULY, 1, 15, 30)
    }

    @Test
    fun monthIntervalKeepsSelectedDayWhenPossible() {
        val now = timeMs(2026, Calendar.JULY, 3, 9, 0)
        val anchor = timeMs(2026, Calendar.JULY, 10, 8, 0)

        val next = AlarmTimeCalculator.calculateNextMonthInterval(
            anchorTimeMs = anchor,
            intervalMonths = 1,
            nowMs = now
        )

        assertCalendar(next, 2026, Calendar.JULY, 10, 8, 0)
    }

    @Test
    fun monthIntervalClampsToLastDayWhenTargetMonthIsShorter() {
        val now = timeMs(2026, Calendar.FEBRUARY, 1, 9, 0)
        val anchor = timeMs(2026, Calendar.JANUARY, 31, 8, 0)

        val next = AlarmTimeCalculator.calculateNextMonthInterval(
            anchorTimeMs = anchor,
            intervalMonths = 1,
            nowMs = now
        )

        assertCalendar(next, 2026, Calendar.FEBRUARY, 28, 8, 0)
    }

    private fun timeMs(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Long {
        return Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun assertCalendar(
        actualMs: Long,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ) {
        val cal = Calendar.getInstance().apply { timeInMillis = actualMs }
        assertEquals(year, cal.get(Calendar.YEAR))
        assertEquals(month, cal.get(Calendar.MONTH))
        assertEquals(day, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(hour, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(minute, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    private fun assertStartOfDay(actualMs: Long, year: Int, month: Int, day: Int) {
        assertCalendar(actualMs, year, month, day, 0, 0)
    }
}
