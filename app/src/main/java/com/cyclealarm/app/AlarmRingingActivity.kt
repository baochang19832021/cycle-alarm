package com.cyclealarm.app

import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AlarmRingingActivity : AppCompatActivity() {

    private var dismissCountDown = 0
    private val handler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyLockScreenWindowFlags()

        setContentView(R.layout.activity_alarm_ringing)

        val label = intent.getStringExtra("label") ?: "周期闹钟"
        val note = intent.getStringExtra("note") ?: ""
        val medicineName = intent.getStringExtra(AlarmService.EXTRA_MEDICINE_NAME) ?: ""
        val alarmId = intent.getStringExtra("alarm_id")
        val isTest = intent.getBooleanExtra(AlarmService.EXTRA_IS_TEST, false)

        val tvIcon = findViewById<TextView>(R.id.tvAlarmIcon)
        val tvLabel = findViewById<TextView>(R.id.tvAlarmLabel)
        val tvNote = findViewById<TextView>(R.id.tvAlarmNote)

        // Title always shows the alarm name
        tvLabel.text = label

        // Supplementary info: medicine or rule
        if (medicineName.isNotEmpty()) {
            tvIcon.text = "💊"
            tvNote.text = medicineName
            tvNote.visibility = android.view.View.VISIBLE
            tvNote.setTextColor(0xFFE65100.toInt())
            tvNote.textSize = 26f
        } else if (note.isNotEmpty()) {
            tvIcon.text = "⏰"
            tvNote.text = note
            tvNote.visibility = android.view.View.VISIBLE
            tvNote.setTextColor(0xFF888888.toInt())
            tvNote.textSize = 16f
        } else {
            tvIcon.text = "⏰"
            tvNote.visibility = android.view.View.GONE
        }

        val now = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA).format(java.util.Date())
        findViewById<TextView>(R.id.tvAlarmTime).text = now

        // ── Long-press to dismiss (anti-accidental) ──
        val btnDismiss = findViewById<Button>(R.id.btnDismiss)
        var dismissing = false
        btnDismiss.setOnClickListener {
            if (!dismissing) {
                dismissing = true
                btnDismiss.text = "长按 2 秒关闭"
                Toast.makeText(this, "请长按按钮 2 秒关闭闹钟", Toast.LENGTH_SHORT).show()
                dismissCountDown = 0
                dismissRunnable?.let { handler.removeCallbacks(it) }
                dismissRunnable = object : Runnable {
                    override fun run() {
                        dismissCountDown++
                        btnDismiss.text = "长按 ${3 - dismissCountDown} 秒关闭"
                        if (dismissCountDown >= 3) {
                            performDismiss(alarmId, medicineName, isTest)
                        } else {
                            handler.postDelayed(this, 800)
                        }
                    }
                }
                handler.postDelayed(dismissRunnable!!, 800)
            }
        }

        val prefs = getSharedPreferences("pixso_ui_alarm_state", Context.MODE_PRIVATE)
        val snoozeEnabled = prefs.getBoolean("snoozeEnabled", true)
        val snoozeMinutes = prefs.getInt("snoozeMinutes", 5)

        val btnSnooze = findViewById<Button>(R.id.btnSnooze)
        btnSnooze.visibility = if (isTest || !snoozeEnabled) android.view.View.GONE else android.view.View.VISIBLE
        val snoozeMs = snoozeMinutes * 60 * 1000L
        btnSnooze.text = "${snoozeMinutes}分钟后再提醒"

        btnSnooze.setOnClickListener {
            alarmId?.let { id ->
                AlarmScheduler.scheduleSnooze(this, id, System.currentTimeMillis() + snoozeMs)
            }
            AlarmService.stop(this, shouldReschedule = false)
            finish()
        }
    }

    private fun performDismiss(alarmId: String?, medicineName: String, isTest: Boolean) {
        AlarmService.stop(this, shouldReschedule = !isTest && !alarmId.isNullOrEmpty())

        // Medicine confirmation dialog
        if (medicineName.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("服药确认")
                .setMessage("已服用 $medicineName 了吗？")
                .setPositiveButton("已服药") { _, _ ->
                    Toast.makeText(this, "已记录服药", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .setNegativeButton("稍后") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        } else {
            finish()
        }
    }

    override fun onBackPressed() {
        // Don't dismiss by back button; use the on-screen action.
    }

    override fun onResume() {
        super.onResume()
        applyLockScreenWindowFlags()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyLockScreenWindowFlags()
    }

    private fun applyLockScreenWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }

        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }
}
