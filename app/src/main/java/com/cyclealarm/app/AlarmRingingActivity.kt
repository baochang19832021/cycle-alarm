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

    // ── Cached UI references for refresh on onNewIntent ──
    private var tvIcon: TextView? = null
    private var tvLabel: TextView? = null
    private var tvNote: TextView? = null
    private var tvTime: TextView? = null
    private var btnSnooze: Button? = null
    private var btnDismiss: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLockScreenWindowFlags()
        setContentView(R.layout.activity_alarm_ringing)

        tvIcon = findViewById(R.id.tvAlarmIcon)
        tvLabel = findViewById(R.id.tvAlarmLabel)
        tvNote = findViewById(R.id.tvAlarmNote)
        tvTime = findViewById(R.id.tvAlarmTime)
        btnSnooze = findViewById(R.id.btnSnooze)
        btnDismiss = findViewById(R.id.btnDismiss)

        refreshUI()
    }

    /** Refresh all UI from current intent extras. Called from both onCreate and onNewIntent. */
    private fun refreshUI() {
        val label = intent.getStringExtra("label") ?: "周期闹钟"
        val note = intent.getStringExtra("note") ?: ""
        val medicineName = intent.getStringExtra(AlarmService.EXTRA_MEDICINE_NAME) ?: ""
        val alarmId = intent.getStringExtra("alarm_id")
        val isTest = intent.getBooleanExtra(AlarmService.EXTRA_IS_TEST, false)

        // Title always shows the alarm name
        tvLabel?.text = label

        // Supplementary info: medicine or rule
        if (medicineName.isNotEmpty()) {
            tvIcon?.text = "💊"
            tvNote?.text = medicineName
            tvNote?.visibility = android.view.View.VISIBLE
            tvNote?.setTextColor(0xFFE65100.toInt())
            tvNote?.textSize = 26f
        } else if (note.isNotEmpty()) {
            tvIcon?.text = "⏰"
            tvNote?.text = note
            tvNote?.visibility = android.view.View.VISIBLE
            tvNote?.setTextColor(0xFF888888.toInt())
            tvNote?.textSize = 16f
        } else {
            tvIcon?.text = "⏰"
            tvNote?.visibility = android.view.View.GONE
        }

        val now = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA).format(java.util.Date())
        tvTime?.text = now

        // Dismiss button
        btnDismiss?.setOnClickListener {
            performDismiss(alarmId, isTest)
        }

        // Snooze button
        val prefs = getSharedPreferences("pixso_ui_alarm_state", Context.MODE_PRIVATE)
        val snoozeEnabled = prefs.getBoolean("snoozeEnabled", true)
        val snoozeMinutes = prefs.getInt("snoozeMinutes", 5).coerceIn(1, 30)
        val snoozeMs = snoozeMinutes * 60 * 1000L

        btnSnooze?.visibility = if (isTest || !snoozeEnabled) android.view.View.GONE else android.view.View.VISIBLE
        btnSnooze?.text = "${snoozeMinutes}分钟后再提醒"
        btnSnooze?.setOnClickListener {
            alarmId?.let { id ->
                AlarmScheduler.scheduleSnooze(this, id, System.currentTimeMillis() + snoozeMs)
            }
            AlarmService.stop(this, shouldReschedule = false)
            finish()
        }
    }

    private fun performDismiss(alarmId: String?, isTest: Boolean) {
        AlarmService.stop(this, shouldReschedule = !isTest && !alarmId.isNullOrEmpty())
        finish()
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
        refreshUI()
    }

    private fun applyLockScreenWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            try {
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (keyguardManager.isDeviceLocked) {
                    keyguardManager.requestDismissKeyguard(this, null)
                }
            } catch (e: Exception) {
                // KeyGuard 请求失败不应影响基本功能
                android.util.Log.w("AlarmRingingActivity", "KeyGuard request failed: ${e.message}")
            }
        } else {
            // Deprecated flags — only on API < 27 (modern flags used above on API 27+)
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON)
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        }

        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }
}
