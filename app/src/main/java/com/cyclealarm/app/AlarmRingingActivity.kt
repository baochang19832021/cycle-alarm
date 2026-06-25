package com.cyclealarm.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AlarmRingingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ═══ Full-screen over lock screen (MIUI-proof) ═══
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        // Key API for MIUI: keep screen on + show over lock screen
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        setContentView(R.layout.activity_alarm_ringing)

        val label = intent.getStringExtra("label") ?: "给妈挂号"
        val note = intent.getStringExtra("note") ?: ""
        val alarmId = intent.getStringExtra("alarm_id")
        // 大标题优先显示备注内容，无备注时显示 label
        findViewById<TextView>(R.id.tvAlarmLabel).text = if (note.isNotEmpty()) note else label

        // 备注已作为大标题显示，下方备注行隐藏
        val tvNote = findViewById<TextView>(R.id.tvAlarmNote)
        tvNote.visibility = android.view.View.GONE

        val now = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA).format(java.util.Date())
        findViewById<TextView>(R.id.tvAlarmTime).text = now

        findViewById<Button>(R.id.btnDismiss).setOnClickListener {
            AlarmService.stop(this)
            finish()
        }

        findViewById<Button>(R.id.btnSnooze).setOnClickListener {
            // Snooze 5 minutes for the same alarm
            alarmId?.let { id ->
                AlarmScheduler.scheduleSnooze(this, id, System.currentTimeMillis() + 5 * 60 * 1000)
            }
            AlarmService.stop(this, shouldReschedule = false)
            finish()
        }

    }

    override fun onBackPressed() {
        // Don't dismiss by back button — must press the button
    }
}
