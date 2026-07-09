package com.cyclealarm.domain

import java.util.Calendar

/**
 * 中国法定工作日/节假日计算。
 * 内置 2026（官方发布）+ 2027（预估，待更新）数据。
 * 每年国务院发布新安排后更新。
 */
object LegalWorkdayHelper {

    /**
     * 判断指定时间戳（毫秒）是否为工作日。
     * 逻辑：补班日 → true；节假日 → false；周一至周五 → true；周六日 → false。
     */
    fun isWorkday(timestampMs: Long): Boolean {
        val dayKey = dayKey(timestampMs)
        if (dayKey in makeupWorkdays) return true
        if (dayKey in holidays) return false
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        return dow != Calendar.SATURDAY && dow != Calendar.SUNDAY
    }

    /**
     * 从 timestampMs（含）开始，找到下一个工作日（含当天）的 [hour]:[minute] 时刻。
     * 返回该时刻的毫秒时间戳。
     */
    fun nextWorkdayAtOrAfter(timestampMs: Long, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
        // 从当天开始检查
        while (true) {
            val key = dayKey(cal.timeInMillis)
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            val isRest = key in holidays || (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY)
            val isMakeup = key in makeupWorkdays
            if (isMakeup || !isRest) {
                // 工作日：返回这天的 hour:minute
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val result = cal.timeInMillis
                // 如果时刻已过，继续找下一天
                if (result >= timestampMs) return result
            }
            // 下一天
            cal.add(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }
    }

    // ── 内部实现 ──

    /** 日期键: yyyyMMdd */
    private fun dayKey(ms: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        return cal.get(Calendar.YEAR) * 10000 +
                (cal.get(Calendar.MONTH) + 1) * 100 +
                cal.get(Calendar.DAY_OF_MONTH)
    }

    private fun date(year: Int, month: Int, day: Int): Int = year * 10000 + month * 100 + day

    // ── 数据 ──

    /** 法定节假日：本应上班但放假的日子 (yyyyMMdd) */
    private val holidays: Set<Int> = setOf(
        // 2026 元旦 Jan 1-3 (Thu-Sat)
        date(2026, 1, 1), date(2026, 1, 2),
        // 2026 春节 Feb 15-23 (Sun-Mon)，只记录工作日变休息日的
        date(2026, 2, 16), date(2026, 2, 17), date(2026, 2, 18),
        date(2026, 2, 19), date(2026, 2, 20), date(2026, 2, 23),
        // 2026 清明节 Apr 4-6 (Sat-Mon)
        date(2026, 4, 6),
        // 2026 劳动节 May 1-5 (Fri-Tue)
        date(2026, 5, 1), date(2026, 5, 4), date(2026, 5, 5),
        // 2026 端午节 Jun 19-21 (Fri-Sun)
        date(2026, 6, 19),
        // 2026 中秋节 Sep 25-27 (Fri-Sun)
        date(2026, 9, 25),
        // 2026 国庆节 Oct 1-7 (Thu-Wed)
        date(2026, 10, 1), date(2026, 10, 2), date(2026, 10, 5),
        date(2026, 10, 6), date(2026, 10, 7),

        // 2027 元旦 (预估 Dec 31, 2026 - Jan 2, 2027)
        date(2027, 1, 1),
        // 2027 春节 (预估 late Jan / early Feb，待官方公布后修正)
        date(2027, 2, 5), date(2027, 2, 8), date(2027, 2, 9),
        date(2027, 2, 10), date(2027, 2, 11), date(2027, 2, 12),
        // 2027 清明节 (预估 early Apr)
        date(2027, 4, 5),
        // 2027 劳动节 (预估 May 1-5)
        date(2027, 5, 3), date(2027, 5, 4), date(2027, 5, 5),
        // 2027 端午节 (预估 late May / early Jun)
        date(2027, 5, 31),
        // 2027 中秋节+国庆节 (预估合并)
        date(2027, 10, 1), date(2027, 10, 4), date(2027, 10, 5),
        date(2027, 10, 6), date(2027, 10, 7), date(2027, 10, 8)
    )

    /** 补班日：本应休息但上班的日子 (yyyyMMdd) */
    private val makeupWorkdays: Set<Int> = setOf(
        // 2026
        date(2026, 1, 4),   // 元旦补班 (Sun)
        date(2026, 2, 14),  // 春节补班 (Sat)
        date(2026, 2, 28),  // 春节补班 (Sat)
        date(2026, 5, 9),   // 劳动节补班 (Sat)
        date(2026, 9, 20),  // 国庆补班 (Sun)
        date(2026, 10, 10), // 国庆补班 (Sat)

        // 2027 预估
        date(2027, 1, 4),   // 元旦补班
        date(2027, 2, 6),   // 春节补班
        date(2027, 5, 8),   // 劳动节补班
        date(2027, 9, 26),  // 国庆补班
        date(2027, 10, 9)   // 国庆补班
    )
}
