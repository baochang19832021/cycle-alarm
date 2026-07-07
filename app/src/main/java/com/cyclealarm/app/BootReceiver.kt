package com.cyclealarm.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.miui.action.QUICKBOOT_POWERON",
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                AlarmScheduler.rescheduleAll(context)
                ReliabilityLogger.log(context, ReliabilityLogger.Event.BOOT,
                    intent.action ?: "unknown")
            }
            AlarmScheduler.ACTION_HEALTH_CHECK -> {
                AlarmScheduler.rescheduleAll(context)
            }
        }
    }
}
