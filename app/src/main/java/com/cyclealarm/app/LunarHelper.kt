package com.cyclealarm.app

import com.nlf.calendar.Lunar
import com.nlf.calendar.Solar
import java.util.Calendar

object LunarHelper {

    private val monthNames = listOf(
        "正月", "二月", "三月", "四月", "五月", "六月",
        "七月", "八月", "九月", "十月", "冬月", "腊月"
    )
    private val dayNames = listOf(
        "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    )

    /**
     * Parse lunar date text like "五月十八" → Pair(monthIndex, dayIndex) 1-based
     */
    fun parseLunarDate(text: String): Pair<Int, Int>? {
        val monthIdx = monthNames.indexOfFirst { text.startsWith(it) }
        val dayIdx = dayNames.indexOfFirst { text.endsWith(it) }
        return if (monthIdx >= 0 && dayIdx >= 0) Pair(monthIdx + 1, dayIdx + 1) else null
    }

    /**
     * Compute the next solar occurrence (epoch ms) of the given lunar date.
     *
     * @param lunarMonth 1-12 lunar month index
     * @param lunarDay 1-30 lunar day index
     * @param isLeapMonth reserved, currently false
     * @param hour target hour 0-23
     * @param minute target minute 0-59
     * @param repeatMonths 12=yearly, 1=monthly, 0=one-shot
     * @param nowMs current time reference
     */
    fun nextLunarOccurrence(
        lunarMonth: Int,
        lunarDay: Int,
        isLeapMonth: Boolean,
        hour: Int,
        minute: Int,
        repeatMonths: Int,
        nowMs: Long
    ): Long {
        val todaySolar = solarFromMs(nowMs)
        val todayLunar = todaySolar.lunar

        if (repeatMonths >= 12) {
            // Yearly: find upcoming occurrence in this or next lunar year
            return nextYearly(todayLunar, lunarMonth, lunarDay, hour, minute, nowMs)
        }
        if (repeatMonths >= 1) {
            // Monthly: find upcoming occurrence in successive lunar months
            return nextMonthly(todayLunar, lunarMonth, lunarDay, hour, minute, repeatMonths, nowMs)
        }
        // One-shot: find the first future occurrence
        return nextYearly(todayLunar, lunarMonth, lunarDay, hour, minute, nowMs)
    }

    /**
     * Convert solar epoch ms to lunar date text like "五月十八"
     */
    fun toLunarText(dateMs: Long): String {
        val lunar = solarFromMs(dateMs).lunar
        val m = monthNames.getOrElse(lunar.month - 1) { "${lunar.month}月" }
        val d = dayNames.getOrElse(lunar.day - 1) { "${lunar.day}" }
        return "$m$d"
    }

    // ── internal helpers ──

    private fun solarFromMs(ms: Long): Solar {
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        return Solar.fromYmd(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun nextYearly(
        todayLunar: Lunar,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        nowMs: Long
    ): Long {
        var year = todayLunar.year
        var candidate = lunarToSolarMs(year, month, day, hour, minute)
        if (candidate <= nowMs) {
            candidate = lunarToSolarMs(year + 1, month, day, hour, minute)
        }
        return candidate
    }

    private fun nextMonthly(
        todayLunar: Lunar,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        repeatMonths: Int,
        nowMs: Long
    ): Long {
        var lYear = todayLunar.year
        var lMonth = todayLunar.month
        while (true) {
            val candidate = lunarToSolarMs(lYear, lMonth, day, hour, minute)
            if (candidate > nowMs) return candidate
            lMonth += repeatMonths
            if (lMonth > 12) {
                lYear += lMonth / 12
                lMonth = (lMonth - 1) % 12 + 1
            }
        }
    }

    private fun lunarToSolarMs(
        lunarYear: Int,
        lunarMonth: Int,
        lunarDay: Int,
        hour: Int,
        minute: Int
    ): Long {
        return try {
            val safeDay = lunarDay.coerceIn(1, 30)
            val lunar = Lunar.fromYmd(lunarYear, lunarMonth, safeDay)
            val solar = lunar.solar
            Calendar.getInstance().apply {
                set(solar.year, solar.month - 1, solar.day, hour, minute, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } catch (_: Exception) {
            // Day may not exist in this lunar month (e.g. day 30 in a 29-day month);
            // try day-1 as a safe fallback, then add 24h
            try {
                val lunar = Lunar.fromYmd(lunarYear, lunarMonth, (lunarDay - 1).coerceAtLeast(1))
                val solar = lunar.solar
                Calendar.getInstance().apply {
                    set(solar.year, solar.month - 1, solar.day, hour, minute, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_YEAR, 1)
                }.timeInMillis
            } catch (_: Exception) {
                System.currentTimeMillis() + 30L * 24 * 3600 * 1000
            }
        }
    }
}
