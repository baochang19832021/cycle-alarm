package com.cyclealarm.domain

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class LegalWorkdayHelperTest {

    private fun ms(year: Int, month: Int, day: Int, hour: Int = 8, minute: Int = 0): Long {
        return Calendar.getInstance().apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `normal Monday is workday`() {
        // 2026-06-22 is Monday, no holiday nearby
        assertTrue(LegalWorkdayHelper.isWorkday(ms(2026, 6, 22)))
    }

    @Test
    fun `normal Saturday is NOT workday`() {
        // 2026-06-20 is Saturday
        assertFalse(LegalWorkdayHelper.isWorkday(ms(2026, 6, 20)))
    }

    @Test
    fun `normal Sunday is NOT workday`() {
        // 2026-06-21 is Sunday
        assertFalse(LegalWorkdayHelper.isWorkday(ms(2026, 6, 21)))
    }

    @Test
    fun `makeup Sunday becomes workday`() {
        // 2026-01-04 is Sunday, makeup for New Year
        assertTrue(LegalWorkdayHelper.isWorkday(ms(2026, 1, 4)))
    }

    @Test
    fun `makeup Saturday becomes workday`() {
        // 2026-02-14 is Saturday, makeup for Spring Festival
        assertTrue(LegalWorkdayHelper.isWorkday(ms(2026, 2, 14)))
    }

    @Test
    fun `holiday Friday is NOT workday`() {
        // 2026-01-02 is Friday, New Year holiday
        assertFalse(LegalWorkdayHelper.isWorkday(ms(2026, 1, 2)))
    }

    @Test
    fun `Spring Festival weekday is NOT workday`() {
        // 2026-02-16 is Monday, Spring Festival holiday
        assertFalse(LegalWorkdayHelper.isWorkday(ms(2026, 2, 16)))
    }

    @Test
    fun `National Day weekday is NOT workday`() {
        // 2026-10-01 is Thursday, National Day holiday
        assertFalse(LegalWorkdayHelper.isWorkday(ms(2026, 10, 1)))
    }

    @Test
    fun `nextWorkday on Monday at 8am returns same day`() {
        val mon8am = ms(2026, 6, 22, 8, 0) // Monday
        val result = LegalWorkdayHelper.nextWorkdayAtOrAfter(mon8am, 8, 0)
        assertEquals(mon8am, result)
    }

    @Test
    fun `nextWorkday on Saturday returns Monday`() {
        val sat = ms(2026, 6, 20, 8, 0) // Saturday
        val mon = ms(2026, 6, 22, 8, 0) // Monday
        val result = LegalWorkdayHelper.nextWorkdayAtOrAfter(sat, 8, 0)
        assertEquals(mon, result)
    }

    @Test
    fun `nextWorkday on Friday evening returns Monday morning`() {
        val fri8pm = ms(2026, 6, 19, 20, 0) // Friday 8pm
        val mon8am = ms(2026, 6, 22, 7, 0)  // Monday 7am
        val result = LegalWorkdayHelper.nextWorkdayAtOrAfter(fri8pm, 7, 0)
        assertEquals(mon8am, result)
    }

    @Test
    fun `nextWorkday across holiday returns post-holiday workday`() {
        // 2026-09-25 is Friday (Mid-Autumn holiday)
        // Next workday should be Mon Sep 28 (or makeup Sep 20 if that's a workday... wait Sep 20 IS a makeup)
        // Actually let me use a simpler case: National Day
        // Oct 1-7 is holiday. Oct 8 (Thursday) is a workday.
        val oct1 = ms(2026, 10, 1, 8, 0)
        val oct8 = ms(2026, 10, 8, 8, 0)
        val result = LegalWorkdayHelper.nextWorkdayAtOrAfter(oct1, 8, 0)
        // Should skip Oct 1-7 and land on Oct 8
        assertTrue(result >= oct8)
        val resultCal = Calendar.getInstance().apply { timeInMillis = result }
        assertEquals(2026, resultCal.get(Calendar.YEAR))
        assertEquals(10, resultCal.get(Calendar.MONTH) + 1)
        assertTrue(resultCal.get(Calendar.DAY_OF_MONTH) in 8..9) // Oct 8 or 9
    }

    @Test
    fun `nextWorkday on makeup day returns same day`() {
        // 2026-02-14 is Saturday but makeup workday
        val feb14 = ms(2026, 2, 14, 7, 0)
        val result = LegalWorkdayHelper.nextWorkdayAtOrAfter(feb14, 7, 0)
        assertEquals(feb14, result)
    }

    @Test
    fun `nextWorkday before work hours returns same day`() {
        val mon6am = ms(2026, 6, 22, 6, 0) // Monday 6am
        val mon7am = ms(2026, 6, 22, 7, 0) // Monday 7am
        val result = LegalWorkdayHelper.nextWorkdayAtOrAfter(mon6am, 7, 0)
        assertEquals(mon7am, result)
    }
}
