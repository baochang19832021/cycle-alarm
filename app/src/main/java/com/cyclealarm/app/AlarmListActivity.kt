package com.cyclealarm.app

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.DatePicker
import android.widget.EditText
import android.widget.ImageButton
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar
import com.cyclealarm.domain.UiAlarmSchedulePlanner
import com.cyclealarm.domain.AlarmTimeCalculator
import com.cyclealarm.domain.LegalWorkdayHelper
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.materialswitch.MaterialSwitch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AlarmListActivity : AppCompatActivity() {

    companion object {
        const val ACTION_VIEW = "com.cyclealarm.app.ACTION_VIEW"
        const val EXTRA_TYPE = "alarm_type"
        const val TYPE_INTERVAL = "interval"
        const val TYPE_HOURLY = "hourly"
        const val TYPE_MONTHLY = "monthly"
        const val TYPE_LUNAR = "lunar"
        const val TYPE_HOLIDAY = "holiday"
        const val TYPE_LOCATION = "location"
        private const val UI_STATE_PREFS = "pixso_ui_alarm_state"
    }

    private enum class Tab { ALARMS, FUNCTIONS, CALENDAR, MINE }

    private enum class FeatureType(
        val title: String,
        val tag: String,
        val accent: Int,
        val tint: Int,
        val iconRes: Int,
        val desc: String
    ) {
        REGULAR(
            "常规日期闹钟",
            "常规日期",
            0xFF2E6FF5.toInt(),
            0xFFEEF3FF.toInt(),
            R.drawable.ic_pixso_feature_regular,
            "每天、每周、法定工作日、每月、每年"
        ),
        SPECIAL(
            "特殊周期闹钟",
            "特殊周期",
            0xFFF07C1A.toInt(),
            0xFFFFF5EC.toInt(),
            R.drawable.ic_pixso_feature_special,
            "每 N 分钟、小时、天、周、月、年"
        ),
        SHIFT(
            "轮班闹钟",
            "轮班",
            0xFF1DB366.toInt(),
            0xFFEDFBF3.toInt(),
            R.drawable.ic_pixso_feature_shift,
            "每轮 N 天循环，适合倒班和夜班"
        ),
        LUNAR(
            "农历日期闹钟",
            "农历",
            0xFFA850C8.toInt(),
            0xFFFDF3FF.toInt(),
            R.drawable.ic_pixso_feature_lunar,
            "农历生日、纪念日、传统节日提醒"
        ),
        MEDICINE(
            "吃药提醒",
            "吃药",
            0xFFE8658D.toInt(),
            0xFFFFF0F5.toInt(),
            R.drawable.ic_pixso_feature_medicine,
            "定时语音播报，提醒按时吃药"
        )
    }

    private data class DemoAlarm(
        val title: String,
        val type: FeatureType,
        val time: String,
        val next: String,
        val rule: String,
        val active: Boolean
    )

    private data class AlarmInstance(
        val id: String = java.util.UUID.randomUUID().toString(),
        val type: FeatureType,
        val title: String,
        val hour: Int = 8,
        val minute: Int = 0,
        val dateMs: Long = 0L,
        val active: Boolean = true,
        val config: Map<String, String> = emptyMap(),
        val createdAt: Long = System.currentTimeMillis(),
        val saved: Boolean = true
    )

    private val root by lazy { LinearLayout(this) }
    private val content by lazy { FrameLayout(this) }
    private val statePrefs by lazy { getSharedPreferences(UI_STATE_PREFS, Context.MODE_PRIVATE) }
    private val navItems = mutableMapOf<Tab, LinearLayout>()
    private var currentTab = Tab.ALARMS

    private var sortByTime: Boolean
        get() = statePrefs.getBoolean("sort_by_time", true)
        set(value) { statePrefs.edit().putBoolean("sort_by_time", value).apply() }

    private var editHour = 8
    private var editMinute = 0
    private var shiftCycleDays = 4
    private val shiftDaySummaries = mutableListOf("07:30", "07:30 +1", "19:30", "休")
    private var selectedCalendarDay: Calendar = Calendar.getInstance()
    private val alarmInstances = mutableListOf<AlarmInstance>()
    private var ringtoneSummary = "默认铃声"
    private var selectedRingtoneUri: String? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isPlayingPreview = false
    private var currentEditingInstanceId: String? = null
    private var isAlarmDeleteMode = false
    private val checkedInstanceIds = mutableSetOf<String>()
    private var vibrationEnabled = true
    private var ringDurationMinutes = 1
    private var snoozeMinutes = 5
    private var snoozeEnabled = true
    // Countdown refresh
    private val countdownViews = mutableMapOf<String, TextView>()
    private val nextRingMsCache = mutableMapOf<String, Long>()
    private val alarmCardViews = mutableMapOf<String, View>()
    private var deleteToolbarRef: View? = null
    private val countdownHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var countdownRunning = false
    private var regularRepeatSummary = "每周一至五"
    private val regularRepeatSelections = mutableSetOf("每周一", "每周二", "每周三", "每周四", "每周五")
    private var specialRepeatValue = 40
    private var specialRepeatUnit = "天"
    private var specialRepeatHours = 2
    private var specialRepeatMinutes = 30
    private val specialWeekdaySelections = mutableSetOf("一", "三", "五")
    private var lunarDateSummary = "五月十八"
    private var lunarRepeatSummary = "每年"
    private var lunarAdvanceDays = 3
    private var lunarAdvanceEnabled = false
    private var medicineDrugName = ""
    private var medicineDosage = ""
    private var medicineVoiceEnabled = true
    private var ttsCheckDone = false
    private var ttsAvailable = false
    private val medicineTimes = mutableListOf("08:00", "20:00")
    private val medicineTimeLabels = mutableListOf("早饭後", "晚饭後")
    private var editTitle: String? = null
    private var editDateMs: Long? = null
    private var editMedicineName: String? = null

    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            selectedRingtoneUri = uri?.toString()
            ringtoneSummary = if (uri != null) getDisplayNameFromUri(uri) else "系统默认"
            saveUiState()
            currentEditingInstanceId?.let { renderEdit(it) }
        }
    }

    private val localAudioPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            try {
                val flags = result.data?.flags ?: 0
                val takeFlags = flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
                if (takeFlags != 0) {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } catch (_: SecurityException) {
            }
            selectedRingtoneUri = uri.toString()
            ringtoneSummary = getDisplayNameFromUri(uri)
            saveUiState()
            currentEditingInstanceId?.let { renderEdit(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge for Android 15+ (targetSdk 35). Let content draw behind
        // system bars but add padding so nothing is obscured.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        loadUiState()
        buildShell()
        selectTab(Tab.ALARMS)
        maybeShowReliabilityStarter()
    }

    override fun onResume() {
        super.onResume()
        startCountdownRefresh()
        if (currentTab == Tab.MINE) renderMine()

        // Post-fire verification: alert user if any alarm was missed
        val missed = AlarmScheduler.findMissedAlarms(this)
        if (missed.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("闹钟可能未按时触发")
                .setMessage("以下闹钟应已触发但系统未报告：\n${missed.joinToString("\n") { "• $it" }}\n\n建议检查权限设置，确保闹钟能准时响起。")
                .setPositiveButton("检查权限") { _, _ ->
                    startActivity(Intent(this, ReliabilityCheckActivity::class.java))
                }
                .setNegativeButton("知道了", null)
                .show()
        }
    }

    override fun onPause() {
        super.onPause()
        stopRingtonePreview()
        stopCountdownRefresh()
    }

    private fun buildShell() {
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xFFF5F6F8.toInt())
        root.layoutParams = LinearLayout.LayoutParams(-1, -1)

        content.layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        root.addView(content)
        root.addView(buildBottomNav())
        setContentView(root)

        // Handle system bar insets so content is not obscured by status bar or nav bar
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            root.setPadding(
                statusBars.left,
                statusBars.top,
                statusBars.right,
                (navBars.bottom + ime.bottom).coerceAtLeast(0)
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun buildBottomNav(): View {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(0, dp(4), 0, dp(4))
            layoutParams = LinearLayout.LayoutParams(-1, dp(60))
        }

        listOf(
            Tab.ALARMS to (R.drawable.ic_pixso_nav_alarm to "闹钟"),
            Tab.FUNCTIONS to (R.drawable.ic_pixso_nav_grid to "功能"),
            Tab.CALENDAR to (R.drawable.ic_pixso_nav_calendar to "日历"),
            Tab.MINE to (R.drawable.ic_pixso_nav_user to "我的")
        ).forEach { (tab, pair) ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
                setOnClickListener { selectTab(tab) }
            }
            item.addView(ImageView(this).apply {
                setImageResource(pair.first)
                setColorFilter(0xFF9CA3AF.toInt())
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
            item.addView(TextView(this).apply {
                text = pair.second
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                setPadding(0, dp(3), 0, 0)
            })
            navItems[tab] = item
            nav.addView(item)
        }
        return nav
    }

    private fun selectTab(tab: Tab) {
        currentTab = tab
        if (tab != Tab.ALARMS) { isAlarmDeleteMode = false; checkedInstanceIds.clear() }
        navItems.forEach { (itemTab, view) ->
            val selected = itemTab == tab
            val color = if (selected) 0xFF4A6CF7.toInt() else 0xFF9CA3AF.toInt()
            (0 until view.childCount).forEach { idx ->
                (view.getChildAt(idx) as? TextView)?.setTextColor(color)
                (view.getChildAt(idx) as? ImageView)?.setColorFilter(color)
            }
        }
        when (tab) {
            Tab.ALARMS -> renderAlarmList()
            Tab.FUNCTIONS -> renderFunctions()
            Tab.CALENDAR -> renderCalendar()
            Tab.MINE -> renderMine()
        }
    }

    private fun setContent(view: View) {
        content.removeAllViews()
        content.addView(view)
    }

    private fun baseScroll(title: String, subtitle: String? = null, rightText: String? = null, topPadding: Int = 0, onRight: (() -> Unit)? = null): LinearLayout {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            setBackgroundColor(0xFFF5F6F8.toInt())
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(22) + topPadding, dp(16), dp(18))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(16))
        }
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            addView(TextView(this@AlarmListActivity).apply {
                text = title
                textSize = 23f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFF111827.toInt())
            })
            if (subtitle != null) addView(TextView(this@AlarmListActivity).apply {
                text = subtitle
                textSize = 13f
                setTextColor(0xFF111827.toInt())
                setPadding(0, dp(8), 0, 0)
            })
        })
        if (rightText != null) {
            header.addView(TextView(this).apply {
                text = rightText
                textSize = if (rightText == "+") 24f else 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFF4A6CF7.toInt())
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(8), dp(4), dp(8))
                setOnClickListener { onRight?.invoke() }
            })
        }
        column.addView(header)
        scroll.addView(column)
        setContent(scroll)
        return column
    }

    private fun renderAlarmList() {
        countdownViews.clear()
        nextRingMsCache.clear()
        alarmCardViews.clear()
        val topPadding = if (isAlarmDeleteMode) dp(52) else 0
        val column = baseScroll("闹钟", topPadding = topPadding)
        // Fixed delete toolbar outside ScrollView — always visible at top
        if (isAlarmDeleteMode) {
            deleteToolbarRef = deleteToolbar()
            content.addView(deleteToolbarRef, FrameLayout.LayoutParams(-1, -2).apply {
                gravity = Gravity.TOP
            })
        }
        // Sort: active alarms by next ring time, inactive at bottom
        val ordered = alarmInstances.sortedWith(compareBy<AlarmInstance> { !it.active }
            .thenBy { nextRingTimeMs(it) ?: Long.MAX_VALUE })
        val cards = ordered.map { inst ->
            val displayTitle = if (inst.type == FeatureType.MEDICINE) {
                val drug = inst.config["drugName"] ?: ""
                val dosage = inst.config["dosage"] ?: ""
                when {
                    drug.isNotEmpty() && dosage.isNotEmpty() -> "💊 $drug · $dosage"
                    drug.isNotEmpty() -> "💊 $drug"
                    else -> inst.title
                }
            } else inst.title
            val displayTime = if (inst.type == FeatureType.MEDICINE) {
                val timesRaw = inst.config["medTimes"] ?: ""
                val times = timesRaw.split("|").filter { it.isNotBlank() }
                if (times.size > 1) times.joinToString("  ") else String.format("%02d:%02d", inst.hour, inst.minute)
            } else String.format("%02d:%02d", inst.hour, inst.minute)
            inst.id to DemoAlarm(
                title = displayTitle,
                type = inst.type,
                time = displayTime,
                next = alarmInstanceNextText(inst),
                rule = alarmInstanceRuleText(inst),
                active = inst.active
            )
        }
        // Center-bottom add button with custom-drawn "+" for pixel-perfect centering
        if (!isAlarmDeleteMode) {
            val btnSize = dp(52)
            val plusPaint = android.graphics.Paint().apply {
                color = 0xFFFFFFFF.toInt()
                style = android.graphics.Paint.Style.FILL
                isAntiAlias = true
            }
            val armW = dp(3).toFloat()
            val armLen = dp(22).toFloat()
            val cx = btnSize / 2f
            val cy = btnSize / 2f
            // Horizontal bar
            val hRect = android.graphics.RectF(cx - armLen / 2f, cy - armW / 2f, cx + armLen / 2f, cy + armW / 2f)
            // Vertical bar
            val vRect = android.graphics.RectF(cx - armW / 2f, cy - armLen / 2f, cx + armW / 2f, cy + armLen / 2f)
            content.addView(object : View(this) {
                override fun onDraw(canvas: android.graphics.Canvas) {
                    canvas.drawRoundRect(hRect, armW / 2f, armW / 2f, plusPaint)
                    canvas.drawRoundRect(vRect, armW / 2f, armW / 2f, plusPaint)
                }
            }.apply {
                background = rounded(0xFF4A6CF7.toInt(), dp(26))
                isClickable = true
                isFocusable = true
                isHapticFeedbackEnabled = false
                isSoundEffectsEnabled = false
                setOnClickListener {
                    isAlarmDeleteMode = false
                    checkedInstanceIds.clear()
                    renderFunctions()
                }
            }, FrameLayout.LayoutParams(btnSize, btnSize).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(24)
            })
        }
        if (cards.isEmpty()) {
            column.addView(TextView(this).apply {
                text = "暂无闹钟"
                textSize = 15f
                setTextColor(0xFF111827.toInt())
                gravity = Gravity.CENTER
                setPadding(0, dp(40), 0, dp(8))
            })
        } else {
            cards.forEach { (id, item) ->
                column.addView(alarmCard(id, item))
            }
        }
    }

    private fun alarmCard(instanceId: String, item: DemoAlarm): View {
        return card().apply {
            alarmCardViews[instanceId] = this
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                leftMargin = dp(8)
                rightMargin = dp(8)
                bottomMargin = dp(14)
            }
            setPadding(dp(15), dp(12), dp(15), dp(12))
            isClickable = true
            isFocusable = true
            isHapticFeedbackEnabled = false
            isSoundEffectsEnabled = false
            setOnClickListener {
                if (isAlarmDeleteMode) {
                    if (instanceId in checkedInstanceIds) checkedInstanceIds.remove(instanceId) else checkedInstanceIds.add(instanceId)
                    updateCardCheckbox(instanceId)
                    updateDeleteToolbarState()
                } else {
                    renderEdit(instanceId)
                }
            }
            setOnLongClickListener {
                if (!isAlarmDeleteMode) {
                    isAlarmDeleteMode = true
                    checkedInstanceIds.add(instanceId)
                    renderAlarmList()
                }
                true
            }
            val top = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(TextView(context).apply {
                text = item.title
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFF111827.toInt())
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                isHapticFeedbackEnabled = false
                isSoundEffectsEnabled = false
            })
            if (isAlarmDeleteMode) {
                val checked = instanceId in checkedInstanceIds
                top.addView(TextView(context).apply {
                    text = if (checked) "✓" else ""
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(if (checked) 0xFFFFFFFF.toInt() else 0xFFCBD5E1.toInt())
                    background = rounded(if (checked) 0xFF4A6CF7.toInt() else 0x00FFFFFF, dp(18), if (checked) 0xFF4A6CF7.toInt() else 0xFFCBD5E1.toInt())
                }, LinearLayout.LayoutParams(dp(28), dp(28)))
            } else {
                top.addView(typeBadge(item.type))
                top.addView(MaterialSwitch(context).apply {
                    isChecked = item.active
                    minWidth = dp(46)
                    minimumWidth = dp(46)
                    scaleX = 0.7f
                    scaleY = 0.7f
                    setOnClickListener {
                        val nextChecked = isChecked
                        val idx = alarmInstances.indexOfFirst { it.id == instanceId }
                        if (idx >= 0) {
                            alarmInstances[idx] = alarmInstances[idx].copy(active = nextChecked)
                        }
                        saveUiState()
                        if (nextChecked) {
                            val inst = alarmInstances.firstOrNull { it.id == instanceId }
                            val scheduledCount = if (inst != null) replaceScheduledAlarmsForInstance(inst) else 0
                            Toast.makeText(this@AlarmListActivity, saveResultText(item.type, scheduledCount), Toast.LENGTH_SHORT).show()
                            maybeShowReliabilityReminder()
                        } else {
                            cancelScheduledAlarmsForInstance(instanceId)
                        }
                        renderAlarmList()
                    }
                })
            }
            val topLayer = FrameLayout(context).apply {
                addView(top, FrameLayout.LayoutParams(-1, -2))
            }
            addView(topLayer)
            addView(TextView(context).apply {
                text = item.time
                textSize = if (item.type == FeatureType.MEDICINE && item.time.length > 5) 24f else 30f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (item.active) 0xFF111827.toInt() else 0xFF9CA3AF.toInt())
                includeFontPadding = false
                setPadding(0, dp(7), 0, 0)
            })
            val nextText = TextView(context).apply {
                text = item.next
                textSize = 13.5f
                setTextColor(if (item.active) 0xFF4B5563.toInt() else 0xFF9CA3AF.toInt())
            }
            countdownViews[instanceId] = nextText
            addView(nextText)
            addView(TextView(context).apply {
                text = item.rule
                textSize = 12.5f
                setTextColor(0xFF8EA0B8.toInt())
                setPadding(0, dp(3), 0, 0)
            })
        }
    }

    private fun renderFunctions() {
        selectNavOnly(Tab.FUNCTIONS)
        val column = baseScroll("功能", "选择要创建的闹钟类型")
        FeatureType.values().forEach { feature ->
            column.addView(functionCard(feature))
        }
    }

    private fun selectNavOnly(tab: Tab) {
        currentTab = tab
        navItems.forEach { (itemTab, view) ->
            val color = if (itemTab == tab) 0xFF4A6CF7.toInt() else 0xFF9CA3AF.toInt()
            (0 until view.childCount).forEach { idx ->
                (view.getChildAt(idx) as? TextView)?.setTextColor(color)
                (view.getChildAt(idx) as? ImageView)?.setColorFilter(color)
            }
        }
    }

    private fun functionCard(feature: FeatureType): View {
        return card().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), dp(13), dp(12), dp(13))
            isHapticFeedbackEnabled = false
            isSoundEffectsEnabled = false
            setOnClickListener {
                val tempId = java.util.UUID.randomUUID().toString()
                val newInstance = AlarmInstance(
                    id = tempId,
                    type = feature,
                    title = defaultAlarmName(feature),
                    dateMs = System.currentTimeMillis(),
                    saved = false
                )
                alarmInstances.add(newInstance)
                renderEdit(tempId, isNew = true)
            }
            addView(iconTile(feature))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), 0, dp(8), 0)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                addView(TextView(context).apply {
                    text = feature.title
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(0xFF111827.toInt())
                })
                addView(TextView(context).apply {
                    text = feature.desc
                    textSize = 13f
                    setTextColor(0xFF8EA0B8.toInt())
                    setPadding(0, dp(6), 0, 0)
                })
            })
            addView(TextView(context).apply {
                text = "›"
                textSize = 26f
                setTextColor(0xFFC7D0DD.toInt())
            })
        }
    }

    private fun renderEdit(instanceId: String, isNew: Boolean = false) {
        val instance = alarmInstances.firstOrNull { it.id == instanceId } ?: return
        val isSameInstance = instanceId == currentEditingInstanceId
        currentEditingInstanceId = instanceId
        val feature = instance.type
        // Only load from stored instance on first entry, not on re-render within the same edit session.
        // Otherwise dialog changes (repeat, date, etc.) get overwritten by stale instance data.
        if (!isSameInstance) {
            editTitle = null
            editDateMs = null
            editMedicineName = null
            editHour = instance.hour
            editMinute = instance.minute
            loadConfigFromInstance(instance)
            // For medicine, sync editHour/Minute from first time slot
            if (feature == FeatureType.MEDICINE && medicineTimes.isNotEmpty()) {
                val parts = medicineTimes[0].split(":")
                editHour = parts.getOrNull(0)?.toIntOrNull() ?: editHour
                editMinute = parts.getOrNull(1)?.toIntOrNull() ?: editMinute
            }
        }
        selectNavOnly(Tab.FUNCTIONS)
        val rootEdit = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        rootEdit.addView(topBar(feature.title, feature.accent, onCancel = {
            // Discard new (unsaved) instance on cancel
            if (isNew) {
                alarmInstances.removeAll { it.id == instanceId }
                cancelScheduledAlarmsForInstance(instanceId)
                saveUiState()
            }
            editTitle = null; editDateMs = null; editMedicineName = null
            currentEditingInstanceId = null
            selectTab(Tab.FUNCTIONS)
        }) {
            // Save: serialize edit state back into instance
            val updatedConfig = buildConfigForType(feature)
            val saveTitle = if (feature == FeatureType.MEDICINE && instanceTitle(instanceId) == null) {
                medicineDisplayTitle()
            } else {
                instanceTitle(instanceId) ?: instance.title
            }
            val (saveHour, saveMinute) = if (feature == FeatureType.MEDICINE) {
                val firstTime = medicineTimes.firstOrNull()?.split(":") ?: listOf("8", "00")
                (firstTime.getOrNull(0)?.toIntOrNull() ?: 8) to (firstTime.getOrNull(1)?.toIntOrNull() ?: 0)
            } else editHour to editMinute
            val updatedInstance = instance.copy(
                title = saveTitle,
                hour = saveHour,
                minute = saveMinute,
                dateMs = instanceDateMs(instanceId),
                active = true,
                config = updatedConfig,
                saved = true
            )
            val idx = alarmInstances.indexOfFirst { it.id == instanceId }
            if (idx >= 0) alarmInstances[idx] = updatedInstance
            isAlarmDeleteMode = false
            saveUiState()
            val scheduledCount = replaceScheduledAlarmsForInstance(updatedInstance)
            Toast.makeText(this, saveResultText(feature, scheduledCount), Toast.LENGTH_SHORT).show()
            maybeShowReliabilityReminder()
            editTitle = null; editDateMs = null; editMedicineName = null
            currentEditingInstanceId = null
            selectTab(Tab.ALARMS)
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(24))
        }
        if (feature == FeatureType.SHIFT) {
            val instTitle = instanceTitle(instanceId) ?: instance.title
            val titleRow = settingsRow("闹钟名称", instTitle, true) { showTitleDialog(instanceId) }
            titleRow.tag = "title_$instanceId"
            column.addView(titleRow)
            val dateRow1 = settingsRow("开始日期", alarmInstanceDateText(instance), true) { showDateDialog(instanceId, "开始日期") }
            dateRow1.tag = "date_$instanceId"
            column.addView(dateRow1)
            column.addView(settingsRow("周期", "每轮 $shiftCycleDays 天", true) { showShiftCycleDialog() })
            val ringRow1 = ringtoneSettingsRow(instanceId)
            ringRow1.tag = "ringtone_$instanceId"
            column.addView(ringRow1)
            column.addView(vibrationButtonRow(instanceId))
            column.addView(sectionTitle("排班设置"))
            column.addView(TextView(this).apply {
                text = "当前每轮 $shiftCycleDays 天，下方按周期显示第1天到第${shiftCycleDays}天"
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(0xFF94A3B8.toInt())
                setPadding(0, 0, 0, dp(8))
            })
            column.addView(shiftDayGrid())
        } else {
            if (feature != FeatureType.MEDICINE) {
                column.addView(timeWheels(editHour, editMinute) { h, m ->
                    editHour = h; editMinute = m
                })
            }
            if (feature != FeatureType.MEDICINE) {
                val instTitle = instanceTitle(instanceId) ?: instance.title
                val titleRow = settingsRow("闹钟名称", instTitle, true) { showTitleDialog(instanceId) }
                titleRow.tag = "title_$instanceId"
                column.addView(titleRow)
            }
            when (feature) {
                FeatureType.REGULAR -> {
                    val dateRowReg = settingsRow("响铃日期", alarmInstanceDateText(instance), true) { showDateDialog(instanceId, "响铃日期") }
                    dateRowReg.tag = "date_$instanceId"
                    column.addView(dateRowReg)
                    val repeatRowReg = settingsRow("重复", regularRepeatSummary, true) { showRegularRepeatDialog(instanceId) }
                    repeatRowReg.tag = "repeat_$instanceId"
                    column.addView(repeatRowReg)
                }
                FeatureType.SPECIAL -> {
                    val dateRowSpec = settingsRow("开始日期", alarmInstanceDateText(instance), true) { showDateDialog(instanceId, "开始日期") }
                    dateRowSpec.tag = "date_$instanceId"
                    column.addView(dateRowSpec)
                    column.addView(settingsRow("重复周期", specialRepeatText(), true) { showSpecialRepeatDialog(reset = true) })
                }
                FeatureType.LUNAR -> {
                    column.addView(settingsRow("农历日期", lunarDateSummary, true) { showLunarDateDialog(feature.accent) })
                    val repeatRowLunar = settingsRow("重复", lunarRepeatSummary, true) { showLunarRepeatDialog(instanceId) }
                    repeatRowLunar.tag = "repeat_$instanceId"
                    column.addView(repeatRowLunar)
                    column.addView(lunarAdvanceRow(instanceId))
                }
                FeatureType.MEDICINE -> {
                    column.addView(medicineDetailRow("药品名称", medicineDrugName, "drug_name_$instanceId", "如：降压药、阿莫西林") {
                        medicineDrugName = it; saveUiState()
                    })
                    column.addView(medicineDetailRow("用法用量", medicineDosage, "dosage_$instanceId", "如：每次1片、每日3次") {
                        medicineDosage = it; saveUiState()
                    })
                    // Quick presets
                    column.addView(medicinePresetRow(feature.accent))
                    // Time slots
                    column.addView(sectionTitle("服药时间"))
                    medicineTimes.forEachIndexed { idx, time ->
                        column.addView(medicineTimeSlotRow(idx, time, medicineTimeLabels.getOrElse(idx) { "" }, instanceId))
                    }
                    column.addView(addMedicineTimeButton(instanceId))
                    column.addView(medicineVoiceRow(instanceId))
                }
                FeatureType.SHIFT -> Unit
            }
            if (feature != FeatureType.MEDICINE) {
                val ringRow2 = ringtoneSettingsRow(instanceId)
                ringRow2.tag = "ringtone_$instanceId"
                column.addView(ringRow2)
                column.addView(vibrationButtonRow(instanceId))
            }
            val durationLabel = if (feature == FeatureType.MEDICINE) "播报时长" else "响铃时长"
            val ringDurationRow = settingsRow(durationLabel, "${ringDurationMinutes}分钟", true) {
                showNumberOptionDialog(durationLabel, ringDurationMinutes, 1..10, "分钟", feature.accent) {
                    ringDurationMinutes = it
                    saveUiState()
                    // Update row in-place instead of rebuilding the whole page
                    val contentView = findViewById<View>(android.R.id.content)
                    (contentView?.findViewWithTag<View>("ring_duration_$instanceId") as? LinearLayout)?.let { row ->
                        (row.getChildAt(1) as? TextView)?.text = "${ringDurationMinutes}分钟"
                    }
                }
            }
            ringDurationRow.tag = "ring_duration_$instanceId"
            column.addView(ringDurationRow)
            column.addView(snoozeSettingsRow(instanceId))
        }
        scroll.addView(column)
        rootEdit.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContent(rootEdit)
    }

    private fun topBar(title: String, accent: Int, onCancel: () -> Unit, onSave: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(26), dp(16), 0)
                layoutParams = LinearLayout.LayoutParams(-1, dp(72))
                addView(TextView(context).apply {
                    text = "取消"
                    textSize = 15f
                    setTextColor(0xFF8EA0B8.toInt())
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(dp(70), -1)
                    setOnClickListener { onCancel() }
                })
                addView(TextView(context).apply {
                    text = title
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(0xFF111827.toInt())
                    layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
                })
                addView(TextView(context).apply {
                    text = "保存"
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(accent)
                    gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
                    layoutParams = LinearLayout.LayoutParams(dp(70), -1)
                    setOnClickListener { onSave() }
                })
            })
            addView(View(context).apply {
                setBackgroundColor(accent)
                alpha = 0.9f
                layoutParams = LinearLayout.LayoutParams(-1, dp(2))
            })
        }
    }

    private fun timeWheels(initialHour: Int, initialMinute: Int, onChange: (Int, Int) -> Unit): View {
        var hour = initialHour
        var minute = initialMinute
        val hourWheel = WheelView(this, 48, 3).apply {
            isCyclic = true
            items = (0..23).map { String.format("%02d", it) }
            currentIndex = hour
            onIndexChanged = { hour = it; onChange(hour, minute) }
        }
        val minuteWheel = WheelView(this, 48, 3).apply {
            isCyclic = true
            items = (0..59).map { String.format("%02d", it) }
            currentIndex = minute
            onIndexChanged = { minute = it; onChange(hour, minute) }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(40), dp(10), dp(40), dp(10))
            addView(hourWheel, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(context).apply {
                text = ":"
                textSize = 34f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFF111827.toInt())
                gravity = Gravity.CENTER
                background = null
                isHapticFeedbackEnabled = false
                isSoundEffectsEnabled = false
            }, LinearLayout.LayoutParams(dp(36), -2))
            addView(minuteWheel, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun shiftDayGrid(): View {
        syncShiftDaysToCycle()
        return GridLayout(this).apply {
            columnCount = 4
            setPadding(0, dp(8), 0, dp(16))
            shiftDaySummaries.forEachIndexed { index, text ->
                addView(dayCard(index, text), GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(72)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(4), dp(4), dp(4), dp(8))
                })
            }
            addView(addDayCard(), GridLayout.LayoutParams().apply {
                width = 0
                height = dp(72)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(8))
            })
        }
    }

    private fun dayCard(index: Int, summary: String): View {
        val isRest = summary == "休"
        val displaySummary = shiftDayCardSummary(summary)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            background = rounded(if (isRest) 0xFFF8FAFC.toInt() else 0xFFFFFFFF.toInt(), dp(10), 0xFFE8EDF5.toInt())
            isHapticFeedbackEnabled = false
            isSoundEffectsEnabled = false
            setOnClickListener { showShiftDayDialog(index) }
            addView(TextView(context).apply {
                text = "第${index + 1}天"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setTextColor(0xFF111827.toInt())
            })
            addView(TextView(context).apply {
                text = displaySummary
                textSize = if (displaySummary.contains("\n")) 14f else 15f
                maxLines = 2
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setTextColor(if (isRest) 0xFF111827.toInt() else FeatureType.SHIFT.accent)
                setPadding(0, dp(5), 0, 0)
            })
        }
    }

    private fun shiftDayCardSummary(summary: String): String {
        if (summary == "休") return summary
        val times = summary.split("、").map { it.trim() }.filter { it.isNotEmpty() }
        return when {
            times.isEmpty() -> "休"
            times.size == 1 -> times.first()
            times.size == 2 -> times.joinToString("\n")
            else -> "${times.first()}\n共${times.size}次"
        }
    }

    private fun addDayCard(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            background = rounded(0x00FFFFFF, dp(10), 0xFFE0E7EF.toInt(), true)
            isHapticFeedbackEnabled = false
            isSoundEffectsEnabled = false
            setOnClickListener {
                shiftDaySummaries.add("休")
                shiftCycleDays = shiftDaySummaries.size
                saveUiState()
                currentEditingInstanceId?.let { renderEdit(it) }
            }
            addView(TextView(context).apply {
                text = "+"
                textSize = 26f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFFCBD5E1.toInt())
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                layoutParams = LinearLayout.LayoutParams(-1, -2)
                includeFontPadding = false
            })
            addView(TextView(context).apply {
                text = "添加"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFFCBD5E1.toInt())
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                layoutParams = LinearLayout.LayoutParams(-1, -2)
                includeFontPadding = false
            })
        }
    }

    private fun showShiftDayDialog(index: Int) {
        val dialog = BottomSheetDialog(this)
        val column = bottomSheetBase("第${index + 1}天", FeatureType.SHIFT.accent, onCancel = { dialog.dismiss() }) {
            dialog.dismiss()
            currentEditingInstanceId?.let { renderEdit(it) }
        }
        val enabledSwitch = MaterialSwitch(this).apply {
            text = "开启提醒"
            textSize = 15f
            isChecked = shiftDaySummaries[index] != "休"
            setTextColor(0xFF111827.toInt())
        }
        column.addView(enabledSwitch, LinearLayout.LayoutParams(-1, dp(54)))
        val dayTimes = if (shiftDaySummaries[index] == "休") mutableListOf("07:30") else shiftDaySummaries[index].split("、").toMutableList()
        val ctx = this
        fun rebuildDialog(dialog: BottomSheetDialog) {
            applyShiftDaySummaryChange(index, dayTimes.joinToString("、"))
            saveUiState()
            dialog.dismiss()
            currentEditingInstanceId?.let { renderEdit(it) }
        }
        val times = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        dayTimes.forEachIndexed { timeIndex, time ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(10), dp(4), dp(10))
                background = rounded(0xFFFFFFFF.toInt(), dp(0))
            }
            row.addView(labelTextView("提醒时间"), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(TextView(ctx).apply {
                text = time
                textSize = 14f
                setTextColor(0xFF8EA0B8.toInt())
                gravity = Gravity.END
                setPadding(0, 0, dp(4), 0)
                isClickable = true; isFocusable = true
                setOnClickListener {
                    showTimeOnlyDialog(time, FeatureType.SHIFT.accent) { next ->
                        dayTimes[timeIndex] = next
                        rebuildDialog(dialog)
                    }
                }
            })
            // Delete ×
            row.addView(TextView(ctx).apply {
                text = "×"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFFCBD5E1.toInt())
                gravity = Gravity.CENTER
                setPadding(dp(10), 0, dp(10), 0)
                isClickable = true; isFocusable = true
                setOnClickListener {
                    if (dayTimes.size > 1) {
                        dayTimes.removeAt(timeIndex)
                        rebuildDialog(dialog)
                    } else {
                        Toast.makeText(ctx, "至少保留1个提醒时间", Toast.LENGTH_SHORT).show()
                    }
                }
            })
            times.addView(row)
        }
        column.addView(times)
        column.addView(TextView(this).apply {
            text = "+  添加提醒时间"
            textSize = 15f
            setTextColor(FeatureType.SHIFT.accent)
            setPadding(dp(18), dp(16), dp(18), dp(20))
            setOnClickListener {
                showTimeOnlyDialog("07:30", FeatureType.SHIFT.accent) { next ->
                    if (shiftDaySummaries[index] == "休") dayTimes.clear()
                    dayTimes.add(next)
                    applyShiftDaySummaryChange(index, dayTimes.joinToString("、"))
                    saveUiState()
                    dialog.dismiss()
                    currentEditingInstanceId?.let { renderEdit(it) }
                }
            }
        })
        enabledSwitch.setOnCheckedChangeListener { _, checked ->
            applyShiftDaySummaryChange(index, if (checked) "07:30" else "休")
            saveUiState()
        }
        dialog.setContentView(column)
        dialog.show()
    }

    private fun applyShiftDaySummaryChange(index: Int, summary: String) {
        val updated = UiAlarmSchedulePlanner.applyShiftDaySummaryChange(shiftDaySummaries, index, summary)
        shiftDaySummaries.clear()
        shiftDaySummaries.addAll(updated)
    }

    private fun showTimeOnlyDialog(current: String, accent: Int, onDone: ((String) -> Unit)? = null) {
        val parts = current.split(":")
        var selectedHour = parts.getOrNull(0)?.toIntOrNull() ?: 7
        var selectedMinute = parts.getOrNull(1)?.take(2)?.toIntOrNull() ?: 30
        val dialog = BottomSheetDialog(this)
        val column = bottomSheetBase("编辑提醒时间", accent, onCancel = { dialog.dismiss() }) {
            onDone?.invoke(String.format("%02d:%02d", selectedHour, selectedMinute))
            saveUiState()
            dialog.dismiss()
        }
        column.addView(timeWheels(selectedHour, selectedMinute) { h, m ->
            selectedHour = h
            selectedMinute = m
        })
        dialog.setContentView(column)
        dialog.show()
    }

    private fun showShiftCycleDialog() {
        val dialog = BottomSheetDialog(this)
        val column = bottomSheetBase("周期", FeatureType.SHIFT.accent, onCancel = { dialog.dismiss() }) {
            syncShiftDaysToCycle()
            saveUiState()
            dialog.dismiss()
            currentEditingInstanceId?.let { renderEdit(it) }
        }
        val wheel = WheelView(this, 54, 5).apply {
            items = (1..31).map { it.toString() }
            currentIndex = (shiftCycleDays - 1).coerceAtLeast(0)
            onIndexChanged = { idx -> shiftCycleDays = idx + 1 }
        }
        column.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(TextView(context).apply {
                text = "每一轮"
                textSize = 17f
                setTextColor(0xFF8EA0B8.toInt())
            })
            addView(wheel, LinearLayout.LayoutParams(dp(112), -2))
            addView(TextView(context).apply {
                text = "天"
                textSize = 17f
                setTextColor(0xFF8EA0B8.toInt())
            })
        })
        dialog.setContentView(column)
        dialog.show()
    }

    private fun showSpecialRepeatDialog(reset: Boolean = true) {
        if (reset) {
            pendingSpecialRepeatValue = specialRepeatValue.coerceIn(specialRepeatRange(specialRepeatUnit))
            pendingSpecialRepeatUnit = specialRepeatUnit
        }
        val dialog = BottomSheetDialog(this)
        fun commitAndClose() {
            if (pendingSpecialRepeatUnit != "时分") {
                pendingSpecialRepeatValue = pendingSpecialRepeatValue.coerceIn(specialRepeatRange(pendingSpecialRepeatUnit))
                specialRepeatValue = pendingSpecialRepeatValue
            } else if (specialRepeatHours == 0 && specialRepeatMinutes == 0) {
                specialRepeatMinutes = 30
            }
            specialRepeatUnit = pendingSpecialRepeatUnit
            saveUiState()
            dialog.dismiss()
            currentEditingInstanceId?.let { renderEdit(it) }
        }
        fun rebuildContent(): LinearLayout {
            val column = bottomSheetBase("重复周期", FeatureType.SPECIAL.accent, onCancel = { dialog.dismiss() }) {
                commitAndClose()
            }
            if (pendingSpecialRepeatUnit == "时分") {
                column.addView(hourMinuteRepeatWheels())
            } else {
                val repeatRange = specialRepeatRange(pendingSpecialRepeatUnit)
                pendingSpecialRepeatValue = pendingSpecialRepeatValue.coerceIn(repeatRange)
                column.addView(slotNumberRepeatWheels(repeatRange, pendingSpecialRepeatUnit))
            }
            val unitRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, dp(8))
            }
            val weekChoices = GridLayout(this).apply {
                columnCount = 4
                setPadding(0, dp(8), 0, dp(8))
                visibility = View.GONE
            }
            listOf("时分", "天", "周", "月", "年").forEach { label ->
                unitRow.addView(pill(label, label == pendingSpecialRepeatUnit, FeatureType.SPECIAL.accent).apply {
                    setOnClickListener {
                        pendingSpecialRepeatUnit = label
                        if (label != "时分") pendingSpecialRepeatValue = pendingSpecialRepeatValue.coerceIn(specialRepeatRange(label))
                        dialog.setContentView(rebuildContent())
                    }
                })
            }
            column.addView(unitRow)
            if (pendingSpecialRepeatUnit == "周") {
                column.addView(sectionTitle("选择星期"))
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { day ->
                    weekChoices.addView(pill(day, day in specialWeekdaySelections, FeatureType.SPECIAL.accent).apply {
                        setOnClickListener {
                            if (day in specialWeekdaySelections) specialWeekdaySelections.remove(day) else specialWeekdaySelections.add(day)
                            saveUiState()
                            dialog.setContentView(rebuildContent())
                        }
                    }, GridLayout.LayoutParams().apply {
                        width = 0
                        height = dp(42)
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        setMargins(dp(6), dp(5), dp(6), dp(5))
                    })
                }
                weekChoices.visibility = View.VISIBLE
                column.addView(weekChoices)
                column.addView(TextView(this).apply {
                    text = "每 N 周支持周一到周日完整选择"
                    textSize = 12f
                    setTextColor(0xFF94A3B8.toInt())
                    setPadding(dp(18), dp(8), dp(18), dp(16))
                })
            }
            column.addView(TextView(this).apply {
                text = specialRepeatRangeHint(pendingSpecialRepeatUnit)
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(0xFF94A3B8.toInt())
                setPadding(dp(18), dp(8), dp(18), dp(10))
            })
            return column
        }
        dialog.setContentView(rebuildContent())
        dialog.show()
    }

    private var pendingSpecialRepeatValue = 40
    private var pendingSpecialRepeatUnit = "天"

    private fun hourMinuteRepeatWheels(): View {
        val hourWheel = WheelView(this, 52, 5).apply {
            items = (0..72).map { it.toString() }
            currentIndex = specialRepeatHours.coerceIn(0, 72)
            onIndexChanged = { specialRepeatHours = it }
        }
        val minuteWheel = WheelView(this, 52, 5).apply {
            items = (0..59).map { it.toString() }
            currentIndex = specialRepeatMinutes.coerceIn(0, 59)
            onIndexChanged = { specialRepeatMinutes = it }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(18), 0, dp(18), dp(6))
            addView(TextView(context).apply { text = "每"; textSize = 16f; setTextColor(0xFF8EA0B8.toInt()) })
            addView(hourWheel, LinearLayout.LayoutParams(dp(86), -2))
            addView(TextView(context).apply { text = "小时"; textSize = 16f; setTextColor(0xFF111827.toInt()) })
            addView(minuteWheel, LinearLayout.LayoutParams(dp(86), -2))
            addView(TextView(context).apply { text = "分钟"; textSize = 16f; setTextColor(0xFF111827.toInt()) })
        }
    }

    private fun slotNumberRepeatWheels(range: IntRange, unit: String): View {
        if (unit == "天" && range.last == 365) {
            var isRendering = false
            var selectedValue = pendingSpecialRepeatValue.coerceIn(1, 365)
            lateinit var hundredsWheel: WheelView
            lateinit var tensWheel: WheelView
            lateinit var onesWheel: WheelView
            var renderValue: (Int) -> Unit = {}

            fun updateFromWheels() {
                if (isRendering) return
                val hundreds = hundredsWheel.currentIndex
                val tens = tensWheel.items[tensWheel.currentIndex].toInt()
                val ones = onesWheel.items[onesWheel.currentIndex].toInt()
                renderValue((hundreds * 100 + tens * 10 + ones).coerceIn(1, 365))
            }

            fun setWheelDigits(wheel: WheelView, digits: IntRange, selectedDigit: Int) {
                val values = digits.map { it.toString() }
                wheel.items = values
                wheel.currentIndex = values.indexOf(selectedDigit.toString()).coerceAtLeast(0)
            }

            renderValue = { value: Int ->
                selectedValue = value.coerceIn(1, 365)
                val hundreds = selectedValue / 100
                val tens = (selectedValue % 100) / 10
                val ones = selectedValue % 10
                val maxTens = if (hundreds == 3) 6 else 9
                val maxOnes = if (hundreds == 3 && tens == 6) 5 else 9

                isRendering = true
                setWheelDigits(hundredsWheel, 0..3, hundreds)
                setWheelDigits(tensWheel, 0..maxTens, tens)
                setWheelDigits(onesWheel, 0..maxOnes, ones)
                isRendering = false

                pendingSpecialRepeatValue = selectedValue
            }

            return LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(18), 0, dp(18), dp(6))
                addView(TextView(context).apply { text = "每"; textSize = 16f; setTextColor(0xFF8EA0B8.toInt()) })

                hundredsWheel = WheelView(this@AlarmListActivity, 52, 3).apply {
                    isCyclic = true
                    onIndexChanged = { updateFromWheels() }
                }
                tensWheel = WheelView(this@AlarmListActivity, 52, 3).apply {
                    isCyclic = true
                    onIndexChanged = { updateFromWheels() }
                }
                onesWheel = WheelView(this@AlarmListActivity, 52, 3).apply {
                    isCyclic = true
                    onIndexChanged = { updateFromWheels() }
                }

                addView(hundredsWheel, LinearLayout.LayoutParams(dp(58), -2))
                addView(tensWheel, LinearLayout.LayoutParams(dp(58), -2))
                addView(onesWheel, LinearLayout.LayoutParams(dp(58), -2))
                addView(TextView(context).apply {
                    text = unit
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(0xFF111827.toInt())
                    setPadding(dp(8), 0, 0, 0)
                })

                renderValue(selectedValue)
            }
        }

        if (range.last <= 99) {
            return LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(18), 0, dp(18), dp(6))
                addView(TextView(context).apply { text = "每"; textSize = 16f; setTextColor(0xFF8EA0B8.toInt()) })
                addView(WheelView(this@AlarmListActivity, 52, 5).apply {
                    isCyclic = true
                    items = range.map { it.toString() }
                    currentIndex = (pendingSpecialRepeatValue.coerceIn(range) - range.first).coerceAtLeast(0)
                    onIndexChanged = { index ->
                        pendingSpecialRepeatValue = range.first + index
                    }
                }, LinearLayout.LayoutParams(dp(96), -2))
                addView(TextView(context).apply {
                    text = unit
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(0xFF111827.toInt())
                    setPadding(dp(8), 0, 0, 0)
                })
            }
        }

        var hundreds = pendingSpecialRepeatValue / 100
        var tens = (pendingSpecialRepeatValue / 10) % 10
        var ones = pendingSpecialRepeatValue % 10

        fun updateValue() {
            val raw = hundreds * 100 + tens * 10 + ones
            pendingSpecialRepeatValue = raw.coerceIn(range)
        }

        fun digitWheel(initial: Int, onDigit: (Int) -> Unit): WheelView =
            WheelView(this, 52, 5).apply {
                isCyclic = true
                items = (0..9).map { it.toString() }
                currentIndex = initial.coerceIn(0, 9)
                onIndexChanged = {
                    onDigit(it)
                    updateValue()
                }
            }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(18), 0, dp(18), dp(6))
            addView(TextView(context).apply { text = "每"; textSize = 16f; setTextColor(0xFF8EA0B8.toInt()) })
            addView(digitWheel(hundreds) { hundreds = it }, LinearLayout.LayoutParams(dp(58), -2))
            addView(digitWheel(tens) { tens = it }, LinearLayout.LayoutParams(dp(58), -2))
            addView(digitWheel(ones) { ones = it }, LinearLayout.LayoutParams(dp(58), -2))
            addView(TextView(context).apply {
                text = unit
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFF111827.toInt())
                setPadding(dp(8), 0, 0, 0)
            })
        }
    }

    private fun specialRepeatRange(unit: String): IntRange =
        when (unit) {
            "天" -> 1..365
            "周" -> 1..10
            "月" -> 1..48
            "年" -> 1..10
            else -> 1..365
        }

    private fun specialRepeatRangeHint(unit: String): String =
        when (unit) {
            "时分" -> "小时 0-72，分钟 0-59；不能同时为 0"
            "天" -> "适合复诊、还款、保养等间隔提醒：1-365 天"
            "周" -> "适合隔周、每几周固定星期提醒：1-10 周"
            "月" -> "适合账单、纪念日等长期提醒：1-48 月"
            "年" -> "适合周年类提醒：1-10 年"
            else -> ""
        }

    private fun showRegularRepeatDialog(instanceId: String) {
        val inst = alarmInstances.firstOrNull { it.id == instanceId } ?: return
        val feature = inst.type
        val dialog = BottomSheetDialog(this)
        fun commitAndClose() {
            regularRepeatSummary = when {
                regularRepeatSummary in listOf("每天", "法定工作日", "每月", "每年") -> regularRepeatSummary
                regularRepeatSelections.size == 7 -> "每天"
                regularRepeatSelections.isEmpty() -> "不重复"
                else -> regularRepeatSelections.joinToString("、") { it.removePrefix("每周") }.let { "每周$it" }
            }
            saveUiState()
            dialog.dismiss()
            // Update repeat row in-place
            val contentView = findViewById<View>(android.R.id.content)
            (contentView?.findViewWithTag<View>("repeat_$instanceId") as? LinearLayout)?.let { row ->
                (row.getChildAt(1) as? TextView)?.text = regularRepeatSummary
            }
        }
        fun rebuildContent(): LinearLayout {
            val column = bottomSheetBase("重复", feature.accent, onCancel = { dialog.dismiss() }) {
                commitAndClose()
            }
            listOf("每周一", "每周二", "每周三", "每周四", "每周五", "每周六", "每周日", "每天", "法定工作日", "每月", "每年")
                .forEach { label ->
                    val selected = label in regularRepeatSelections || label == regularRepeatSummary
                    column.addView(regularRepeatOptionRow(label, selected, feature.accent) {
                        if (label.startsWith("每周")) {
                            regularRepeatSummary = ""
                            if (label in regularRepeatSelections) regularRepeatSelections.remove(label) else regularRepeatSelections.add(label)
                        } else {
                            regularRepeatSelections.clear()
                            regularRepeatSummary = label
                        }
                        saveUiState()
                        dialog.setContentView(rebuildContent())
                    })
                }
            return column
        }
        dialog.setContentView(rebuildContent())
        dialog.show()
    }

    private fun showLunarDateDialog(accent: Int) {
        val dialog = BottomSheetDialog(this)
        val months = listOf("正月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "冬月", "腊月")
        val days = listOf("初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十", "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十", "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十")
        var monthIndex = months.indexOfFirst { lunarDateSummary.startsWith(it) }.coerceAtLeast(4)
        var dayIndex = days.indexOfFirst { lunarDateSummary.endsWith(it) }.coerceAtLeast(17)
        val column = bottomSheetBase("农历日期", accent, onCancel = { dialog.dismiss() }) {
            lunarDateSummary = "${months[monthIndex]}${days[dayIndex]}"
            saveUiState()
            dialog.dismiss()
            currentEditingInstanceId?.let { renderEdit(it) }
        }
        val monthWheel = WheelView(this, 52, 5).apply {
            items = months
            currentIndex = monthIndex
            onIndexChanged = { monthIndex = it }
        }
        val dayWheel = WheelView(this, 52, 5).apply {
            items = days
            currentIndex = dayIndex
            onIndexChanged = { dayIndex = it }
        }
        column.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(6), dp(28), dp(18))
            addView(monthWheel, LinearLayout.LayoutParams(0, -2, 1f))
            addView(dayWheel, LinearLayout.LayoutParams(0, -2, 1f))
        })
        dialog.setContentView(column)
        dialog.show()
    }

    private fun showLunarRepeatDialog(instanceId: String) {
        val inst = alarmInstances.firstOrNull { it.id == instanceId } ?: return
        showOptionDialog("农历重复", listOf("每年", "只提醒一次"), lunarRepeatSummary, inst.type.accent) {
            lunarRepeatSummary = it
            saveUiState()
            // Update repeat row in-place
            val contentView = findViewById<View>(android.R.id.content)
            (contentView?.findViewWithTag<View>("repeat_$instanceId") as? LinearLayout)?.let { row ->
                (row.getChildAt(1) as? TextView)?.text = lunarRepeatSummary
            }
        }
    }

    private fun showTitleDialog(instanceId: String) {
        val inst = alarmInstances.firstOrNull { it.id == instanceId } ?: return
        val currentTitle = editTitle ?: inst.title
        val input = EditText(this).apply {
            setText(currentTitle)
            selectAll()
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("闹钟名称")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("完成") { _, _ ->
                val next = input.text?.toString()?.trim().orEmpty()
                if (next.isNotEmpty()) {
                    editTitle = next
                }
                // Update title row in-place instead of rebuilding the whole page
                val contentView = findViewById<View>(android.R.id.content)
                val row = contentView?.findViewWithTag<View>("title_$instanceId")
                if (row is LinearLayout && row.childCount > 1) {
                    val valueTv = row.getChildAt(1)
                    if (valueTv is TextView) {
                        valueTv.text = instanceTitle(instanceId) ?: inst.title
                    }
                }
            }
            .show()
    }

    private fun showDateDialog(instanceId: String, title: String) {
        val inst = alarmInstances.firstOrNull { it.id == instanceId } ?: return
        val cal = Calendar.getInstance().apply {
            if (inst.dateMs > 0L) timeInMillis = inst.dateMs
        }
        val picker = DatePicker(this).apply {
            init(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), null)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(picker)
            .setNegativeButton("取消", null)
            .setPositiveButton("完成") { _, _ ->
                val newDateMs = Calendar.getInstance().apply {
                    set(Calendar.YEAR, picker.year)
                    set(Calendar.MONTH, picker.month)
                    set(Calendar.DAY_OF_MONTH, picker.dayOfMonth)
                }.timeInMillis
                editDateMs = newDateMs
                saveUiState()
                // Update date row in-place
                val contentView = findViewById<View>(android.R.id.content)
                val updatedText = alarmInstanceDateText(inst)
                (contentView?.findViewWithTag<View>("date_$instanceId") as? LinearLayout)?.let { row ->
                    (row.getChildAt(1) as? TextView)?.text = updatedText
                }
            }
            .show()
    }

    private fun showRingtoneDialog(instanceId: String) {
        currentEditingInstanceId = instanceId
        val inst = alarmInstances.firstOrNull { it.id == instanceId } ?: return
        val dialog = BottomSheetDialog(this)
        fun rebuildContent(): LinearLayout {
            val column = bottomSheetBase("铃声", inst.type.accent, onCancel = {
                stopRingtonePreview()
                dialog.dismiss()
            }) {
                stopRingtonePreview()
                dialog.dismiss()
                // Update ringtone row in-place instead of rebuilding the whole page
                val contentView = findViewById<View>(android.R.id.content)
                (contentView?.findViewWithTag<View>("ringtone_$instanceId") as? LinearLayout)?.let { row ->
                    if (row.childCount > 1) (row.getChildAt(1) as? TextView)?.text = ringtoneSummary
                    if (row.childCount > 2) (row.getChildAt(2) as? TextView)?.text = if (isPlayingPreview) "■" else "▷"
                }
            }
            column.addView(settingsRow("当前铃声", ringtoneSummary, false))
            column.addView(settingsRow("选择系统铃声", "", true) {
                stopRingtonePreview()
                dialog.dismiss()
                openSystemRingtonePicker()
            })
            column.addView(settingsRow("选择本地音频", "", true) {
                stopRingtonePreview()
                dialog.dismiss()
                openLocalAudioPicker()
            })
            column.addView(settingsRow(if (isPlayingPreview) "停止试听" else "试听铃声", if (isPlayingPreview) "■" else "▶", false) {
                toggleRingtonePreview()
                dialog.setContentView(rebuildContent())
            })
            return column
        }
        dialog.setContentView(rebuildContent())
        dialog.show()
    }

    private fun openSystemRingtonePicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择闹钟铃声")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            selectedRingtoneUri?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
        }
        try {
            ringtonePickerLauncher.launch(findPreferredRingtonePackage(intent)?.let { Intent(intent).setPackage(it) } ?: intent)
        } catch (_: ActivityNotFoundException) {
            openLocalAudioPicker()
        }
    }

    private fun findPreferredRingtonePackage(intent: Intent): String? {
        val handlers = packageManager.queryIntentActivities(intent, 0)
        if (handlers.isEmpty()) return null
        val preferredPackages = listOf(
            "com.android.thememanager",
            "com.android.soundpicker",
            "com.google.android.soundpicker",
            "com.android.settings"
        )
        preferredPackages.firstOrNull { preferred ->
            handlers.any { it.activityInfo?.packageName == preferred }
        }?.let { return it }
        val systemHandlers = handlers.filter { resolveInfo ->
            val appInfo = resolveInfo.activityInfo?.applicationInfo ?: return@filter false
            appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
        }
        return if (systemHandlers.size == 1) systemHandlers.first().activityInfo.packageName else null
    }

    private fun openLocalAudioPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            localAudioPickerLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "这台手机暂时无法打开本地音乐", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleRingtonePreview() {
        if (isPlayingPreview) stopRingtonePreview() else startRingtonePreview()
    }

    private fun startRingtonePreview() {
        try {
            mediaPlayer?.release()
            mediaPlayer = null
            val uri = selectedRingtoneUri?.let { Uri.parse(it) } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmListActivity, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setOnCompletionListener { stopRingtonePreview() }
                prepare()
                start()
            }
            isPlayingPreview = true
            Toast.makeText(this, "正在试听铃声", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "无法播放铃声：${e.message}", Toast.LENGTH_LONG).show()
            stopRingtonePreview()
        }
    }

    private fun stopRingtonePreview() {
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        isPlayingPreview = false
    }

    private fun getDisplayNameFromUri(uri: Uri): String {
        return try {
            RingtoneManager.getRingtone(this, uri)?.getTitle(this)
        } catch (_: Exception) {
            null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "自定义铃声"
    }

    private fun showNumberOptionDialog(title: String, current: Int, range: IntRange, unit: String, accent: Int, onDone: (Int) -> Unit) {
        val dialog = BottomSheetDialog(this)
        var selected = current.coerceIn(range)
        val column = bottomSheetBase(title, accent, onCancel = { dialog.dismiss() }) {
            onDone(selected)
            saveUiState()
            dialog.dismiss()
        }
        val wheel = WheelView(this, 52, 5).apply {
            items = range.map { it.toString() }
            currentIndex = selected - range.first
            onIndexChanged = { selected = range.first + it }
        }
        column.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(wheel, LinearLayout.LayoutParams(dp(112), -2))
            addView(TextView(context).apply {
                text = unit
                textSize = 17f
                setTextColor(0xFF8EA0B8.toInt())
            })
        })
        dialog.setContentView(column)
        dialog.show()
    }

    private fun showOptionDialog(title: String, options: List<String>, selected: String, accent: Int, onSelected: (String) -> Unit) {
        val dialog = BottomSheetDialog(this)
        fun rebuildContent(currentSelected: String): LinearLayout {
            val column = bottomSheetBase(title, accent, onCancel = { dialog.dismiss() }) { dialog.dismiss() }
            options.forEach { option ->
                column.addView(regularRepeatOptionRow(option, option == currentSelected, accent) {
                    onSelected(option)
                    dialog.setContentView(rebuildContent(option))
                })
            }
            return column
        }
        dialog.setContentView(rebuildContent(selected))
        dialog.show()
    }

    private fun showReliabilityTips() {
        AlertDialog.Builder(this)
            .setTitle("保持闹钟可靠")
            .setMessage(
                "1. 请勿在系统设置中「强制停止」本应用，否则闹钟将失效。\n\n" +
                "2. 重启手机后请至少解锁一次屏幕，否则闹钟可能无法触发。\n\n" +
                "3. 睡前建议保持手机充电，或使用飞行模式代替关机。\n\n" +
                "4. 定期检查「必要权限」页面，确保通知、自启动、电池优化等权限未被系统自动关闭。\n\n" +
                "5. 如闹钟未按时响起，请打开「必要权限」页面运行一次测试响铃。"
            )
            .setPositiveButton("知道了", null)
            .setNegativeButton("检查权限") { _, _ ->
                startActivity(Intent(this, ReliabilityCheckActivity::class.java))
            }
            .show()
    }

    private fun showHelpAndPrivacyDialog() {
        AlertDialog.Builder(this)
            .setTitle("使用手册与隐私安全")
            .setMessage(
                "使用说明：在“功能”页选择闹钟类型，设置时间、日期、重复规则、铃声、震动后保存；在“闹钟”页可启停或再次编辑。\n\n" +
                    "必要权限：通知、悬浮窗、电池优化、全屏提醒等只用于提升锁屏和后台响铃可靠性，可在“必要权限”页检查。\n\n" +
                    "隐私原则：当前基础功能以本地使用为主，不需要登录账号，不上传闹钟内容，不申请通讯录、短信、通话记录、相机、麦克风等无关权限。\n\n" +
                    "当前版本：${currentVersionName()}"
            )
            .setPositiveButton("我知道了", null)
            .show()
    }

    private fun bottomSheetBase(title: String, accent: Int, onCancel: () -> Unit, onDone: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(20))
            background = rounded(0xFFFFFFFF.toInt(), dp(20))
            addView(View(context).apply {
                background = rounded(0xFFE5E7EB.toInt(), dp(2))
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(4)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(12)
                }
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), 0, dp(18), 0)
                addView(TextView(context).apply {
                    text = "取消"
                    textSize = 15f
                    setTextColor(0xFF8EA0B8.toInt())
                    setOnClickListener { onCancel() }
                }, LinearLayout.LayoutParams(dp(84), dp(48)))
                addView(TextView(context).apply {
                    text = title
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(0xFF111827.toInt())
                }, LinearLayout.LayoutParams(0, dp(48), 1f))
                addView(TextView(context).apply {
                    text = "完成"
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
                    setTextColor(accent)
                    setOnClickListener { onDone() }
                }, LinearLayout.LayoutParams(dp(84), dp(48)))
            })
        }
    }

    private fun renderCalendar() {
        val column = baseScroll("日历", rightText = "今天") {
            selectedCalendarDay = Calendar.getInstance()
            renderCalendar()
        }
        column.removeAllViews()
        val now = selectedCalendarDay.clone() as Calendar
        val shiftStartMs = alarmInstances.firstOrNull { it.type == FeatureType.SHIFT }?.dateMs ?: System.currentTimeMillis()
        column.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
            addView(TextView(context).apply {
                text = "‹  ${SimpleDateFormat("yyyy年M月", Locale.CHINA).format(now.time)}  ›"
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFF111827.toInt())
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            addView(TextView(context).apply {
                text = "今天"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(FeatureType.SHIFT.accent)
                setOnClickListener {
                    selectedCalendarDay = Calendar.getInstance()
                    renderCalendar()
                }
            })
        })
        column.addView(calendarGrid(now, shiftStartMs))
        column.addView(card().apply {
            setPadding(dp(16), dp(14), dp(16), dp(14))
            addView(TextView(context).apply {
                text = "${now.get(Calendar.MONTH) + 1}月${now.get(Calendar.DAY_OF_MONTH)}日 ${weekName(now)}"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFF111827.toInt())
            })
            val reminderLines = UiAlarmSchedulePlanner.shiftCalendarReminderLines(
                startDateMs = shiftStartMs,
                cycleDays = shiftCycleDays,
                daySummaries = shiftDaySummaries,
                targetDateMs = now.timeInMillis
            )
            if (reminderLines.isEmpty()) {
                addView(TextView(context).apply {
                    text = "本日休息"
                    textSize = 14.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(0xFFCBD5E1.toInt())
                    setPadding(0, dp(14), 0, 0)
                })
            } else {
                reminderLines.forEachIndexed { index, line ->
                    addView(TextView(context).apply {
                        text = "提醒时间：$line"
                        textSize = 14.5f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(FeatureType.SHIFT.accent)
                        setPadding(0, if (index == 0) dp(14) else dp(12), 0, 0)
                    })
                }
            }
        })
    }

    private fun calendarGrid(month: Calendar, shiftStartMs: Long): View {
        val grid = GridLayout(this).apply {
            columnCount = 7
            background = rounded(0xFFFFFFFF.toInt(), dp(8))
            setPadding(dp(8), dp(8), dp(8), dp(10))
        }
        listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { index, label ->
            grid.addView(calendarCell(label, "", false, false, index >= 5, true), gridParams())
        }
        val cal = month.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val firstOffset = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        repeat(firstOffset) { grid.addView(calendarCell("", "", false, false), gridParams()) }
        val max = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (day in 1..max) {
            val summary = shiftDayCardSummary(
                UiAlarmSchedulePlanner.shiftCalendarSummary(
                    startDateMs = shiftStartMs,
                    cycleDays = shiftCycleDays,
                    daySummaries = shiftDaySummaries,
                    targetDateMs = cal.timeInMillis
                )
            )
            val selected = day == selectedCalendarDay.get(Calendar.DAY_OF_MONTH) &&
                cal.get(Calendar.MONTH) == selectedCalendarDay.get(Calendar.MONTH) &&
                cal.get(Calendar.YEAR) == selectedCalendarDay.get(Calendar.YEAR)
            val weekend = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
            val cellDay = cal.clone() as Calendar
            grid.addView(calendarCell(day.toString(), summary, selected, summary == "休", weekend, false).apply {
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedCalendarDay = cellDay
                    renderCalendar()
                }
            }, gridParams())
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return grid
    }

    private fun gridParams(): GridLayout.LayoutParams =
        GridLayout.LayoutParams().apply {
            width = 0
            height = dp(54)
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(0, 0, 0, dp(3))
        }

    private fun calendarCell(day: String, summary: String, selected: Boolean, rest: Boolean, weekend: Boolean = false, header: Boolean = false): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            background = if (selected) rounded(0x00FFFFFF, dp(8), FeatureType.SHIFT.accent) else null
            isHapticFeedbackEnabled = false
            isSoundEffectsEnabled = false
            setPadding(dp(1), 0, dp(1), 0)
            addView(TextView(context).apply {
                text = day
                textSize = if (header) 13f else 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                includeFontPadding = false
                setTextColor(when {
                    header && weekend -> 0xFFFF7A59.toInt()
                    header -> 0xFF64748B.toInt()
                    rest -> 0xFF111827.toInt()
                    weekend -> 0xFFE8795E.toInt()
                    else -> 0xFF111827.toInt()
                })
            }, LinearLayout.LayoutParams(-1, -2))
            addView(TextView(context).apply {
                text = summary
                textSize = 10.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                includeFontPadding = false
                setTextColor(if (rest) 0xFFCBD5E1.toInt() else FeatureType.SHIFT.accent)
            }, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun renderMine() {
        val column = baseScroll("我的")
        column.addView(card().apply {
            setPadding(dp(16), dp(15), dp(16), dp(15))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(this@AlarmListActivity, ReliabilityCheckActivity::class.java))
            }
            addView(TextView(context).apply {
                text = "必要权限"
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFF111827.toInt())
            })
            addView(TextView(context).apply {
                text = if (collectCoreReliabilityIssues().isEmpty()) "核心权限状态正常" else "建议检查通知、悬浮窗、电池优化等权限"
                textSize = 13.5f
                setTextColor(0xFF8EA0B8.toInt())
                setPadding(0, dp(8), 0, dp(14))
            })
            addView(buttonText("去检查权限", 0xFF4A6CF7.toInt()) {
                startActivity(Intent(this@AlarmListActivity, ReliabilityCheckActivity::class.java))
            })
        })
        column.addView(card().apply {
            setPadding(dp(16), dp(15), dp(16), dp(15))
            isClickable = true
            isFocusable = true
            setOnClickListener { showReliabilityTips() }
            addView(TextView(context).apply {
                text = "⏰ 保持闹钟可靠"
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFF111827.toInt())
            })
            addView(TextView(context).apply {
                text = "了解如何确保闹钟准时响起，避免常见问题。"
                textSize = 13.5f
                setTextColor(0xFF8EA0B8.toInt())
                setPadding(0, dp(8), 0, 0)
            })
        })
        column.addView(card().apply {
            setPadding(dp(16), dp(15), dp(16), dp(15))
            isClickable = true
            isFocusable = true
            setOnClickListener { showHelpAndPrivacyDialog() }
            addView(TextView(context).apply {
                text = "使用手册与隐私安全"
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFF111827.toInt())
            })
            addView(TextView(context).apply {
                text = "查看使用说明、隐私原则、权限用途和版本信息。"
                textSize = 13.5f
                setTextColor(0xFF8EA0B8.toInt())
                setPadding(0, dp(8), 0, 0)
            })
        })
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(0xFFFFFFFF.toInt(), dp(12))
        elevation = dp(1).toFloat()
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(11)
        }
    }

    private fun typeBadge(type: FeatureType): TextView = TextView(this).apply {
        text = type.tag
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(type.accent)
        background = rounded(type.tint, dp(5))
        setPadding(dp(6), dp(3), dp(6), dp(3))
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
            rightMargin = dp(6)
        }
    }

    private fun iconTile(feature: FeatureType): View = ImageView(this).apply {
        setImageResource(feature.iconRes)
        layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
    }

    private fun regularRepeatOptionRow(label: String, selected: Boolean, accent: Int, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            isClickable = true
            isFocusable = true
            isHapticFeedbackEnabled = false
            isSoundEffectsEnabled = false
            setOnClickListener { onClick() }
            addView(TextView(context).apply {
                text = label
                textSize = 15f
                setTextColor(0xFF111827.toInt())
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(context).apply {
                text = if (selected) "✓" else ""
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(if (selected) 0xFFFFFFFF.toInt() else 0xFFCBD5E1.toInt())
                background = rounded(if (selected) accent else 0x00FFFFFF, dp(18), if (selected) accent else 0xFFCBD5E1.toInt())
            }, LinearLayout.LayoutParams(dp(28), dp(28)))
        }
    }

    private fun vibrationButtonRow(instanceId: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = rounded(0xFFFFFFFF.toInt(), dp(0))
            isHapticFeedbackEnabled = false
            isSoundEffectsEnabled = false
            addView(labelTextView("响铃时振动"), LinearLayout.LayoutParams(0, -2, 1f))
            addView(MaterialSwitch(context).apply {
                isChecked = vibrationEnabled
                setOnCheckedChangeListener { _, checked ->
                    vibrationEnabled = checked
                    saveUiState()
                }
            })
            setOnClickListener {
                val switch = getChildAt(1) as? MaterialSwitch ?: return@setOnClickListener
                switch.isChecked = !switch.isChecked
            }
        }
    }

    private fun snoozeSettingsRow(instanceId: String): View {
        val inst = alarmInstances.firstOrNull { it.id == instanceId } ?: return View(this)
        val ctx = this
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = rounded(0xFFFFFFFF.toInt(), dp(0))
            isHapticFeedbackEnabled = false
            isSoundEffectsEnabled = false
        }
        val snoozeLabel = if (inst.type == FeatureType.MEDICINE) "再次播报" else "再次响铃"
        val snoozeDialogTitle = if (inst.type == FeatureType.MEDICINE) "再次播报间隔" else "再次响铃间隔"
        // Reusable update function — only rebuilds this row, not the whole page
        fun rebuildRow() {
            row.removeAllViews()
            row.addView(labelTextView(snoozeLabel), LinearLayout.LayoutParams(0, -2, 1f))
            if (snoozeEnabled) {
                row.addView(TextView(ctx).apply {
                    text = "${snoozeMinutes}分钟"
                    textSize = 14f
                    setTextColor(0xFF8EA0B8.toInt())
                    gravity = Gravity.RIGHT
                    setPadding(0, 0, dp(16), 0)
                    isClickable = true
                    isFocusable = true
                    isHapticFeedbackEnabled = false
                    isSoundEffectsEnabled = false
                    setOnClickListener {
                        showNumberOptionDialog(snoozeDialogTitle, snoozeMinutes, 1..30, "分钟", inst.type.accent) {
                            snoozeMinutes = it
                            saveUiState()
                            rebuildRow()
                        }
                    }
                })
            }
            row.addView(MaterialSwitch(ctx).apply {
                isChecked = snoozeEnabled
                setOnCheckedChangeListener { _, checked ->
                    snoozeEnabled = checked
                    saveUiState()
                    rebuildRow()
                }
            })
            // Row tap opens minutes dialog (not toggle switch)
            row.setOnClickListener {
                if (snoozeEnabled) {
                    showNumberOptionDialog(snoozeDialogTitle, snoozeMinutes, 1..30, "分钟", inst.type.accent) {
                        snoozeMinutes = it
                        saveUiState()
                        rebuildRow()
                    }
                }
            }
        }
        rebuildRow()
        return row
    }

    private fun lunarAdvanceRow(instanceId: String): View {
        val inst = alarmInstances.firstOrNull { it.id == instanceId } ?: return View(this)
        val ctx = this
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = rounded(0xFFFFFFFF.toInt(), dp(0))
            isHapticFeedbackEnabled = false
            isSoundEffectsEnabled = false
        }
        fun rebuildRow() {
            row.removeAllViews()
            row.addView(labelTextView("提前提醒"), LinearLayout.LayoutParams(0, -2, 1f))
            if (lunarAdvanceEnabled) {
                row.addView(TextView(ctx).apply {
                    text = "提前${lunarAdvanceDays}天"
                    textSize = 14f
                    setTextColor(0xFF8EA0B8.toInt())
                    gravity = Gravity.RIGHT
                    setPadding(0, 0, dp(16), 0)
                    isClickable = true
                    isFocusable = true
                    isHapticFeedbackEnabled = false
                    isSoundEffectsEnabled = false
                    setOnClickListener {
                        showNumberOptionDialog("提前提醒天数", lunarAdvanceDays, 1..30, "天", inst.type.accent) {
                            lunarAdvanceDays = it
                            saveUiState()
                            rebuildRow()
                        }
                    }
                })
            }
            row.addView(MaterialSwitch(ctx).apply {
                isChecked = lunarAdvanceEnabled
                setOnCheckedChangeListener { _, checked ->
                    lunarAdvanceEnabled = checked
                    saveUiState()
                    rebuildRow()
                }
            })
            row.setOnClickListener {
                if (lunarAdvanceEnabled) {
                    showNumberOptionDialog("提前提醒天数", lunarAdvanceDays, 1..30, "天", inst.type.accent) {
                        lunarAdvanceDays = it
                        saveUiState()
                        rebuildRow()
                    }
                }
            }
        }
        rebuildRow()
        return row
    }

    private fun settingsRow(label: String, value: String, arrow: Boolean, onClick: (() -> Unit)? = null): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = rounded(0xFFFFFFFF.toInt(), dp(0))
            if (onClick != null) {
                isClickable = true
                isFocusable = true
                isHapticFeedbackEnabled = false
                isSoundEffectsEnabled = false
                setOnClickListener { onClick() }
            }
            addView(labelTextView(label), LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(context).apply {
                text = value
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(if (value == "✓") 0xFF4A6CF7.toInt() else 0xFF8EA0B8.toInt())
                gravity = Gravity.END
            }, LinearLayout.LayoutParams(0, -2, 1f))
            if (arrow) addView(TextView(context).apply {
                text = "›"
                textSize = 22f
                setTextColor(0xFFC7D0DD.toInt())
                setPadding(dp(4), 0, 0, 0)
            })
        }
    }

    private fun ringtoneSettingsRow(instanceId: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = rounded(0xFFFFFFFF.toInt(), dp(0))
            isClickable = true
            isFocusable = true
            isHapticFeedbackEnabled = false
            isSoundEffectsEnabled = false
            setOnClickListener { openSystemRingtonePicker() }
            addView(labelTextView("铃声"), LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(context).apply {
                text = ringtoneSummary
                textSize = 14f
                setTextColor(0xFF8EA0B8.toInt())
                gravity = Gravity.RIGHT
                maxLines = 1
            })
            addView(TextView(context).apply {
                text = if (isPlayingPreview) "■" else "▷"
                textSize = 18f
                setTextColor(0xFFB8C0CC.toInt())
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                isHapticFeedbackEnabled = false
                isSoundEffectsEnabled = false
                setOnClickListener {
                    toggleRingtonePreview()
                    // Update button text in-place (no need to rebuild whole page)
                    this@apply.text = if (isPlayingPreview) "■" else "▷"
                }
            }, LinearLayout.LayoutParams(dp(30), dp(36)))
            addView(TextView(context).apply {
                text = "›"
                textSize = 22f
                setTextColor(0xFFC7D0DD.toInt())
                setPadding(dp(4), 0, 0, 0)
            })
        }
    }

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(0xFF94A3B8.toInt())
        setPadding(0, dp(12), 0, dp(8))
    }

    private fun defaultAlarmName(feature: FeatureType): String =
        when (feature) {
            FeatureType.REGULAR -> "工作日提醒"
            FeatureType.SPECIAL -> "特殊周期提醒"
            FeatureType.SHIFT -> "轮班闹钟"
            FeatureType.LUNAR -> "农历生日提醒"
            FeatureType.MEDICINE -> "吃药提醒"
        }

    // alarmTitle removed — use instance.title directly

    private fun medicineNameText(instanceId: String): String =
        editMedicineName?.ifEmpty { "未设置" }
            ?: alarmInstances.firstOrNull { it.id == instanceId }?.config?.get("medicineName")?.ifEmpty { "未设置" }
            ?: "未设置"

    private fun showMedicineNameDialog(instanceId: String) {
        val inst = alarmInstances.firstOrNull { it.id == instanceId } ?: return
        val currentMed = inst.config["medicineName"] ?: ""
        val input = EditText(this).apply {
            setText(currentMed)
            hint = "如：阿莫西林、降压药"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("药品名称")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                val next = input.text.toString().trim()
                editMedicineName = next
            }
            .show()
    }

    /** Reusable row: label + editable value with dialog + auto-update. One source of truth. */
    private fun medicineDetailRow(
        label: String, currentValue: String, tag: String,
        hint: String, onChanged: (String) -> Unit
    ): View {
        val display = currentValue.ifEmpty { "未设置" }
        val row = settingsRow(label, display, true) {
            showMedicineDetailDialog(label, currentValue, hint) { newValue ->
                onChanged(newValue)
                // Update row text in-place
                (findViewById<View>(android.R.id.content)?.findViewWithTag<View>(tag) as? LinearLayout)?.let { r ->
                    (r.getChildAt(1) as? TextView)?.text = newValue.ifEmpty { "未设置" }
                }
            }
        }
        row.tag = tag
        return row
    }

    private fun showMedicineDetailDialog(title: String, current: String, placeholder: String, onDone: (String) -> Unit) {
        val input = EditText(this).apply {
            setText(current)
            hint = placeholder
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("完成") { _, _ ->
                val next = input.text?.toString()?.trim().orEmpty()
                onDone(next)
            }
            .show()
    }

    private fun medicinePresetRow(accent: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(10), dp(16), dp(6))
        }
        row.addView(labelTextView("快捷设置"), LinearLayout.LayoutParams(0, -2, 1f))
        listOf(
            "每日一次" to { applyMedicinePreset(1) },
            "每日两次" to { applyMedicinePreset(2) },
            "每日三次" to { applyMedicinePreset(3) }
        ).forEach { (label, action) ->
            row.addView(pill(label, medicineTimes.size == label.last().toString().toIntOrNull(), accent).apply {
                setOnClickListener { action(); renderEdit(currentEditingInstanceId ?: return@setOnClickListener) }
            })
        }
        return row
    }

    private fun applyMedicinePreset(count: Int) {
        medicineTimes.clear()
        medicineTimeLabels.clear()
        when (count) {
            1 -> { medicineTimes.add("08:00"); medicineTimeLabels.add("早饭後") }
            2 -> { medicineTimes.addAll(listOf("08:00", "20:00")); medicineTimeLabels.addAll(listOf("早饭後", "晚饭後")) }
            3 -> { medicineTimes.addAll(listOf("08:00", "12:30", "19:00")); medicineTimeLabels.addAll(listOf("早饭後", "午饭后", "晚饭後")) }
        }
        saveUiState()
    }

    private fun medicineTimeSlotRow(idx: Int, time: String, label: String, instanceId: String): View {
        val ctx = this
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(8), dp(16), dp(8))
            background = rounded(0xFFFFFFFF.toInt(), dp(0))
            // Time display
            addView(TextView(ctx).apply {
                text = "🕐 $time"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFF111827.toInt())
                setPadding(0, 0, dp(8), 0)
                isClickable = true; isFocusable = true
                setOnClickListener { showMedicineTimePicker(idx, instanceId) }
            })
            // Label
            addView(TextView(ctx).apply {
                text = label
                textSize = 13f
                setTextColor(0xFF8EA0B8.toInt())
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                isClickable = true; isFocusable = true
                setOnClickListener { showMedicineLabelDialog(idx, instanceId) }
            })
            // Delete (only if more than 1)
            if (medicineTimes.size > 1) {
                addView(TextView(ctx).apply {
                    text = "✕"
                    textSize = 16f
                    setTextColor(0xFFCBD5E1.toInt())
                    setPadding(dp(10), dp(4), dp(0), dp(4))
                    setOnClickListener {
                        medicineTimes.removeAt(idx)
                        medicineTimeLabels.removeAt(idx.coerceAtMost(medicineTimeLabels.size - 1))
                        saveUiState()
                        renderEdit(instanceId)
                    }
                })
            }
        }
    }

    private fun addMedicineTimeButton(instanceId: String): View {
        return TextView(this).apply {
            text = "+ 添加服药时间"
            textSize = 14f
            setTextColor(0xFF4A6CF7.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener {
                medicineTimes.add("12:00")
                medicineTimeLabels.add("")
                saveUiState()
                renderEdit(instanceId)
            }
        }
    }

    private fun showMedicineTimePicker(idx: Int, instanceId: String) {
        val parts = medicineTimes[idx].split(":")
        var h = parts.getOrNull(0)?.toIntOrNull() ?: 8
        var m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val dialog = BottomSheetDialog(this)
        val column = bottomSheetBase("服药时间", 0xFF4A6CF7.toInt(), onCancel = { dialog.dismiss() }) {
            medicineTimes[idx] = String.format("%02d:%02d", h, m)
            saveUiState()
            dialog.dismiss()
            renderEdit(instanceId)
        }
        column.addView(timeWheels(h, m) { nh, nm -> h = nh; m = nm })
        dialog.setContentView(column)
        dialog.show()
    }

    private fun showMedicineLabelDialog(idx: Int, instanceId: String) {
        val current = medicineTimeLabels.getOrElse(idx) { "" }
        showMedicineDetailDialog("时段标签", current, "如：早饭後、睡前") { label ->
            if (idx < medicineTimeLabels.size) medicineTimeLabels[idx] = label
            else medicineTimeLabels.add(label)
            saveUiState()
            renderEdit(instanceId)
        }
    }

    private fun checkTtsAvailability() {
        if (ttsCheckDone) return
        var checker: android.speech.tts.TextToSpeech? = null
        checker = android.speech.tts.TextToSpeech(this) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                checker?.setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                val langResult = checker?.setLanguage(java.util.Locale.CHINESE) ?: android.speech.tts.TextToSpeech.LANG_MISSING_DATA
                ttsAvailable = langResult != android.speech.tts.TextToSpeech.LANG_MISSING_DATA && langResult != android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED
            }
            ttsCheckDone = true
        }
    }

    private fun openTtsSettings() {
        try {
            startActivity(Intent("com.android.settings.TTS_SETTINGS"))
        } catch (_: Exception) {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, "请手动打开 设置→无障碍→文字转语音", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun medicineVoiceRow(instanceId: String): View {
        val ctx = this
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = rounded(0xFFFFFFFF.toInt(), dp(0))
        }
        fun rebuildRow() {
            row.removeAllViews()
            row.addView(labelTextView("语音播报"), LinearLayout.LayoutParams(0, -2, 1f))
            if (!medicineVoiceEnabled) {
                row.addView(TextView(ctx).apply {
                    text = "仅振动"
                    textSize = 13f
                    setTextColor(0xFF8EA0B8.toInt())
                    gravity = Gravity.RIGHT
                    setPadding(0, 0, dp(10), 0)
                })
            }
            row.addView(MaterialSwitch(ctx).apply {
                isChecked = medicineVoiceEnabled
                setOnCheckedChangeListener { _, checked ->
                    medicineVoiceEnabled = checked
                    saveUiState()
                    rebuildRow()
                }
            })
        }
        rebuildRow()
        return row
    }

    private fun medicineDisplayTitle(): String {
        val drug = medicineDrugName
        val dosage = medicineDosage
        return when {
            drug.isNotEmpty() && dosage.isNotEmpty() -> "💊 $drug · $dosage"
            drug.isNotEmpty() -> "💊 $drug"
            else -> "吃药提醒"
        }
    }

    private fun timeText(): String = String.format("%02d:%02d", editHour, editMinute)

    private fun shiftPreviewTimeText(): String = UiAlarmSchedulePlanner.shiftPreviewTimeText(shiftDaySummaries)

    private fun specialRepeatText(): String {
        if (specialRepeatUnit == "时分") {
            return when {
                specialRepeatHours > 0 && specialRepeatMinutes > 0 -> "每 $specialRepeatHours 小时 $specialRepeatMinutes 分钟"
                specialRepeatHours > 0 -> "每 $specialRepeatHours 小时"
                else -> "每 $specialRepeatMinutes 分钟"
            }
        }
        return if (specialRepeatUnit == "周" && specialWeekdaySelections.isNotEmpty()) {
            "每 $specialRepeatValue 周 ${specialWeekdaySelections.joinToString("、")}"
        } else {
            "每 $specialRepeatValue $specialRepeatUnit"
        }
    }

    private fun syncShiftDaysToCycle() {
        shiftCycleDays = shiftCycleDays.coerceIn(1, 31)
        while (shiftDaySummaries.size < shiftCycleDays) shiftDaySummaries.add("休")
        while (shiftDaySummaries.size > shiftCycleDays) shiftDaySummaries.removeAt(shiftDaySummaries.lastIndex)
    }

    private fun replaceScheduledAlarmsForInstance(instance: AlarmInstance): Int {
        cancelScheduledAlarmsForInstance(instance.id)
        val plans = schedulePlansForInstance(instance)
        plans.forEach { plan ->
            AlarmScheduler.schedule(
                this,
                AlarmScheduler.AlarmData(
                    id = plan.id,
                    hour = plan.hour,
                    minute = plan.minute,
                    intervalDays = plan.intervalDays,
                    repeatMinutes = plan.repeatMinutes,
                    repeatMonths = plan.repeatMonths,
                    startDateMs = plan.startDateMs,
                    ringtoneUri = plan.ringtoneUri,
                    label = plan.label,
                    note = plan.note,
                    medicineName = plan.medicineName,
                    vibrate = plan.vibrate,
                    isActive = true
                )
            )
        }
        return plans.size
    }

    private fun cancelScheduledAlarmsForInstance(instanceId: String) {
        val prefix = "alarm_ui_${instanceId}_"
        AlarmScheduler.getAllAlarms(this)
            .filter { it.id.startsWith(prefix) }
            .forEach { AlarmScheduler.deleteAlarm(this, it.id) }
    }

    private fun schedulePlansForInstance(instance: AlarmInstance): List<UiAlarmSchedulePlanner.SchedulePlan> {
        if (!instance.active) return emptyList()
        val label = instance.title
        val dateMs = instance.dateMs
        val (hour, minute) = instance.hour to instance.minute
        val medName = instance.config["medicineName"] ?: ""
        val rawPlans = when (instance.type) {
            FeatureType.REGULAR -> {
                val raw = instance.config["repeatSelections"]?.takeIf { it.isNotBlank() }
                    ?: regularRepeatSelections.joinToString("|")
                val selections = raw.split("|").filter { it.isNotBlank() }.toMutableSet()
                // Special rules (每天/每月/每年) are stored in repeatSummary but clear repeatSelections.
                // Must merge them back into selections so planRegular can generate actual alarm plans.
                val summary = instance.config["repeatSummary"] ?: regularRepeatSummary
                if (summary in setOf("每天", "每月", "每年", "法定工作日")) {
                    selections.add(summary)
                }
                UiAlarmSchedulePlanner.planRegular(
                    enabled = true, hour = hour, minute = minute, label = label,
                    ringtoneUri = selectedRingtoneUri, vibrate = vibrationEnabled,
                    selectedRules = selections, selectedDateMs = dateMs
                )
            }
            FeatureType.SPECIAL -> {
                val unit = instance.config["repeatUnit"] ?: specialRepeatUnit
                val value = (instance.config["repeatValue"] ?: specialRepeatValue.toString()).toIntOrNull() ?: specialRepeatValue
                val sHours = (instance.config["repeatHours"] ?: specialRepeatHours.toString()).toIntOrNull() ?: specialRepeatHours
                val sMinutes = (instance.config["repeatMinutes"] ?: specialRepeatMinutes.toString()).toIntOrNull() ?: specialRepeatMinutes
                val weekdays = (instance.config["weekdaySelections"] ?: specialWeekdaySelections.joinToString("|"))
                    .split("|").filter { it.isNotBlank() }.toSet()
                UiAlarmSchedulePlanner.planSpecial(
                    enabled = true, hour = hour, minute = minute, label = label,
                    ringtoneUri = selectedRingtoneUri, vibrate = vibrationEnabled,
                    unit = unit, value = value, hours = sHours, minutes = sMinutes,
                    selectedWeekdays = weekdays, startDateMs = dateMs, medicineName = medName
                )
            }
            FeatureType.SHIFT -> {
                val cycleDays = (instance.config["cycleDays"] ?: shiftCycleDays.toString()).toIntOrNull() ?: shiftCycleDays
                val summaries = (instance.config["daySummaries"] ?: shiftDaySummaries.joinToString("|"))
                    .split("|").filter { it.isNotBlank() }.toMutableList()
                UiAlarmSchedulePlanner.planShift(
                    enabled = true, cycleDays = cycleDays, startDateMs = dateMs,
                    daySummaries = summaries, label = label,
                    ringtoneUri = selectedRingtoneUri, vibrate = vibrationEnabled, medicineName = medName
                )
            }
            FeatureType.LUNAR -> {
                val repeat = instance.config["lunarRepeat"] ?: lunarRepeatSummary
                val repeatMonths = when (repeat) {
                    "每年" -> 12
                    "每月" -> 1
                    else -> 0
                }
                if (repeatMonths == 0) {
                    emptyList()
                } else {
                    val lunarDate = instance.config["lunarDate"] ?: lunarDateSummary
                    val advanceEnabled = instance.config["lunarAdvanceEnabled"]?.toBooleanStrictOrNull() ?: lunarAdvanceEnabled
                    val advanceDays = instance.config["lunarAdvanceDays"]?.toIntOrNull() ?: lunarAdvanceDays
                    val advanceMs = if (advanceEnabled) advanceDays.coerceIn(1, 30) * 24L * 3600 * 1000L else 0L
                    val parsed = LunarHelper.parseLunarDate(lunarDate)
                    val rawAnchorMs = if (parsed != null) {
                        LunarHelper.nextLunarOccurrence(
                            lunarMonth = parsed.first,
                            lunarDay = parsed.second,
                            isLeapMonth = false,
                            hour = hour, minute = minute,
                            repeatMonths = repeatMonths,
                            nowMs = System.currentTimeMillis()
                        )
                    } else {
                        if (dateMs > 0L) dateMs else System.currentTimeMillis()
                    }
                    val anchorMs = (rawAnchorMs - advanceMs).coerceAtLeast(System.currentTimeMillis() + 60_000L)
                    listOf(
                        UiAlarmSchedulePlanner.SchedulePlan(
                            id = "alarm_ui_lunar",
                            hour = hour, minute = minute,
                            intervalDays = 0,
                            startDateMs = anchorMs,
                            label = label,
                            note = "农历$lunarDate",
                            ringtoneUri = selectedRingtoneUri,
                            vibrate = vibrationEnabled,
                            repeatMonths = repeatMonths
                        )
                    )
                }
            }
            FeatureType.MEDICINE -> {
                val voiceOn = instance.config["voiceEnabled"]?.toBooleanStrictOrNull() ?: medicineVoiceEnabled
                val medName = if (voiceOn) (instance.config["drugName"] ?: medicineDrugName) else ""
                val timesRaw = instance.config["medTimes"] ?: medicineTimes.joinToString("|")
                val labelsRaw = instance.config["medLabels"] ?: medicineTimeLabels.joinToString("|")
                val times = timesRaw.split("|").filter { it.isNotBlank() }
                val labels = labelsRaw.split("|").filter { it.isNotBlank() }
                // Anchor to yesterday midnight so calculateNextTime(intervalDays=1) starts from today
                val anchorMs = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                times.mapIndexed { idx, timeStr ->
                    val parts = timeStr.split(":")
                    val h = parts.getOrNull(0)?.toIntOrNull() ?: instance.hour
                    val m = parts.getOrNull(1)?.toIntOrNull() ?: instance.minute
                    val timeLabel = labels.getOrElse(idx) { "" }
                    val note = if (timeLabel.isNotEmpty()) "吃药 · $timeLabel" else "吃药提醒"
                    UiAlarmSchedulePlanner.SchedulePlan(
                        id = "alarm_ui_medicine_$idx",
                        hour = h, minute = m,
                        intervalDays = 1,
                        startDateMs = anchorMs,
                        label = label,
                        note = note,
                        ringtoneUri = selectedRingtoneUri,
                        vibrate = vibrationEnabled,
                        medicineName = medName
                    )
                }
            }
        }
        // Make planner IDs instance-unique
        return rawPlans.map { plan ->
            plan.copy(id = "alarm_ui_${instance.id}_${plan.id.removePrefix("alarm_ui_")}")
        }
    }

    private fun saveResultText(feature: FeatureType, scheduledCount: Int): String =
        if (scheduledCount > 0) {
            "${feature.title} 已保存并开启提醒"
        } else {
            "${feature.title} 已保存，当前规则暂未接入系统提醒"
        }

    // ── Bulk delete support ──
    private fun deleteToolbar(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setBackgroundColor(0xFFFFFFFF.toInt())
            addView(TextView(context).apply {
                text = "取消"
                textSize = 16f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(0xFF8EA0B8.toInt())
                setPadding(dp(12), dp(8), dp(16), dp(8))
                setOnClickListener {
                    isAlarmDeleteMode = false
                    checkedInstanceIds.clear()
                    renderAlarmList()
                }
            })
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(4), dp(20), dp(4))
                isClickable = true
                isFocusable = true
                isHapticFeedbackEnabled = false
                isSoundEffectsEnabled = false
                val accentColor = if (checkedInstanceIds.isEmpty()) 0xFFCBD5E1.toInt() else 0xFF4A6CF7.toInt()
                addView(TextView(context).apply {
                    text = "🗑"
                    textSize = 22f
                    gravity = Gravity.CENTER
                })
                addView(TextView(context).apply {
                    text = "删除"
                    textSize = 14f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(accentColor)
                })
                setOnClickListener {
                    if (checkedInstanceIds.isNotEmpty()) deleteCheckedAlarms()
                }
            })
        }
    }

    private fun updateCardCheckbox(instanceId: String) {
        val card = alarmCardViews[instanceId] as? ViewGroup ?: return
        val checked = instanceId in checkedInstanceIds

        // 安全地查找复选框
        val checkbox = when {
            card.childCount >= 1 -> {
                val topLayer = card.getChildAt(0) as? ViewGroup ?: return
                if (topLayer.childCount >= 1) {
                    val top = topLayer.getChildAt(0) as? LinearLayout ?: return
                    // In delete mode, top has [title, checkbox] at indices 0 and 1
                    if (top.childCount >= 2) top.getChildAt(1) as? TextView else null
                } else null
            }
            else -> null
        } ?: return

        checkbox.text = if (checked) "✓" else ""
        val bg = if (checked) 0xFF4A6CF7.toInt() else 0x00FFFFFF
        val border = if (checked) 0xFF4A6CF7.toInt() else 0xFFCBD5E1.toInt()
        checkbox.background = rounded(bg, dp(18), border)
        checkbox.setTextColor(if (checked) 0xFFFFFFFF.toInt() else 0xFFCBD5E1.toInt())
    }

    private fun updateDeleteToolbarState() {
        val toolbar = deleteToolbarRef as? LinearLayout ?: return
        val accentColor = if (checkedInstanceIds.isEmpty()) 0xFFCBD5E1.toInt() else 0xFF4A6CF7.toInt()
        // Delete button is the 3rd child (index 2): [取消, spacer, deleteBlock]
        val deleteBlock = toolbar.getChildAt(2) as? LinearLayout ?: return
        (0 until deleteBlock.childCount).forEach { idx ->
            (deleteBlock.getChildAt(idx) as? TextView)?.setTextColor(accentColor)
        }
    }

    private fun deleteCheckedAlarms() {
        val toDelete = checkedInstanceIds.toList()
        for (instanceId in toDelete) {
            cancelScheduledAlarmsForInstance(instanceId)
            alarmInstances.removeAll { it.id == instanceId }
        }
        checkedInstanceIds.clear()
        isAlarmDeleteMode = false
        saveUiState()
        renderAlarmList()
        Toast.makeText(this, "已删除 ${toDelete.size} 个闹钟", Toast.LENGTH_SHORT).show()
    }

    private fun deleteInstanceWithUndo(instanceId: String) {
        val instance = alarmInstances.firstOrNull { it.id == instanceId } ?: return
        val hadActive = instance.active
        alarmInstances.removeAll { it.id == instanceId }
        isAlarmDeleteMode = false
        cancelScheduledAlarmsForInstance(instanceId)
        saveUiState()
        renderAlarmList()
        Snackbar.make(content, "已删除闹钟", Snackbar.LENGTH_LONG)
            .setAction("撤销") {
                alarmInstances.add(instance.copy(active = hadActive))
                saveUiState()
                if (hadActive) replaceScheduledAlarmsForInstance(instance)
                renderAlarmList()
            }
            .show()
    }

    // ════════════════════════════════════════
    //  SERIALIZATION HELPERS
    // ════════════════════════════════════════

    private fun serializeInstancesToJson(instances: List<AlarmInstance>): String {
        val arr = org.json.JSONArray()
        for (inst in instances) {
            // Never persist unsaved (in-edit) instances — they would become
            // stale zombies on next load if the user cancels without saving.
            if (!inst.saved) continue
            val obj = org.json.JSONObject().apply {
                put("id", inst.id)
                put("type", inst.type.name)
                put("title", inst.title)
                put("hour", inst.hour)
                put("minute", inst.minute)
                put("dateMs", inst.dateMs)
                put("active", inst.active)
                put("config", org.json.JSONObject(inst.config))
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun parseInstancesFromJson(json: String): List<AlarmInstance> {
        val arr = org.json.JSONArray(json)
        val result = mutableListOf<AlarmInstance>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val configObj = obj.optJSONObject("config") ?: org.json.JSONObject()
            val config = mutableMapOf<String, String>()
            configObj.keys().forEach { key -> config[key] = configObj.getString(key) }
            result.add(AlarmInstance(
                id = obj.getString("id"),
                type = FeatureType.valueOf(obj.getString("type")),
                title = obj.getString("title"),
                hour = obj.optInt("hour", 8),
                minute = obj.optInt("minute", 0),
                dateMs = obj.optLong("dateMs", 0L),
                active = obj.optBoolean("active", true),
                config = config
            ))
        }
        return result
    }

    private fun migrateFromOldFormat() {
        // Build one instance per FeatureType from old SharedPreferences keys
        for (feature in FeatureType.values()) {
            val oldTitle = statePrefs.getString("title_${feature.name}", null) ?: continue
            val hour = statePrefs.getInt("timeHour_${feature.name}", editHour)
            val minute = statePrefs.getInt("timeMinute_${feature.name}", editMinute)
            val dateMs = statePrefs.getLong("date_${feature.name}", 0L)
            val wasActive = statePrefs.getBoolean("active_${feature.name}", true)
            // If the feature was hidden (soft-deleted), mark inactive
            val hiddenStr = statePrefs.getString("hiddenFeatureTypes", "") ?: ""
            val wasHidden = hiddenStr.split("|").any { it == feature.name }
            val medName = statePrefs.getString("med_${feature.name}", "") ?: ""

            val config = mutableMapOf<String, String>()
            when (feature) {
                FeatureType.REGULAR -> {
                    config["repeatSummary"] = statePrefs.getString("regularRepeatSummary", "每周一、三、五") ?: "每周一、三、五"
                    config["repeatSelections"] = statePrefs.getString("regularRepeatSelections", "每周一|每周三|每周五") ?: "每周一|每周三|每周五"
                }
                FeatureType.SPECIAL -> {
                    config["repeatValue"] = (statePrefs.getInt("specialRepeatValue", 40)).toString()
                    config["repeatUnit"] = statePrefs.getString("specialRepeatUnit", "天") ?: "天"
                    config["repeatHours"] = (statePrefs.getInt("specialRepeatHours", 2)).toString()
                    config["repeatMinutes"] = (statePrefs.getInt("specialRepeatMinutes", 30)).toString()
                    config["weekdaySelections"] = statePrefs.getString("specialWeekdaySelections", "一|三|五") ?: "一|三|五"
                    if (medName.isNotEmpty()) config["medicineName"] = medName
                }
                FeatureType.SHIFT -> {
                    config["cycleDays"] = (statePrefs.getInt("shiftCycleDays", 4)).toString()
                    config["daySummaries"] = statePrefs.getString("shiftDaySummaries", "07:30|07:30 +1|19:30|休") ?: "07:30|07:30 +1|19:30|休"
                    if (medName.isNotEmpty()) config["medicineName"] = medName
                }
                FeatureType.LUNAR -> {
                    config["lunarDate"] = statePrefs.getString("lunarDateSummary", "五月十八") ?: "五月十八"
                    config["lunarRepeat"] = statePrefs.getString("lunarRepeatSummary", "每年") ?: "每年"
                }
                FeatureType.MEDICINE -> {
                    config["drugName"] = statePrefs.getString("medicineDrugName", medicineDrugName) ?: medicineDrugName
                    config["dosage"] = statePrefs.getString("medicineDosage", medicineDosage) ?: medicineDosage
                    config["voiceEnabled"] = statePrefs.getBoolean("medicineVoiceEnabled", medicineVoiceEnabled).toString()
                    config["medTimes"] = statePrefs.getString("medTimes", medicineTimes.joinToString("|")) ?: medicineTimes.joinToString("|")
                    config["medLabels"] = statePrefs.getString("medLabels", medicineTimeLabels.joinToString("|")) ?: medicineTimeLabels.joinToString("|")
                }
            }

            alarmInstances.add(AlarmInstance(
                id = java.util.UUID.randomUUID().toString(),
                type = feature,
                title = oldTitle,
                hour = hour,
                minute = minute,
                dateMs = dateMs,
                active = !wasHidden,
                config = config
            ))
        }
        // Persist migrated instances immediately
        statePrefs.edit()
            .putString("alarm_instances_json", serializeInstancesToJson(alarmInstances))
            .apply()
    }

    private fun buildConfigForType(type: FeatureType): Map<String, String> = when (type) {
        FeatureType.REGULAR -> mapOf(
            "repeatSummary" to regularRepeatSummary,
            "repeatSelections" to regularRepeatSelections.joinToString("|")
        )
        FeatureType.SPECIAL -> mapOf(
            "repeatValue" to specialRepeatValue.toString(),
            "repeatUnit" to specialRepeatUnit,
            "repeatHours" to specialRepeatHours.toString(),
            "repeatMinutes" to specialRepeatMinutes.toString(),
            "weekdaySelections" to specialWeekdaySelections.joinToString("|"),
            "medicineName" to (medNameForType(FeatureType.SPECIAL))
        )
        FeatureType.SHIFT -> mapOf(
            "cycleDays" to shiftCycleDays.toString(),
            "daySummaries" to shiftDaySummaries.joinToString("|"),
            "medicineName" to (medNameForType(FeatureType.SHIFT))
        )
        FeatureType.LUNAR -> mapOf(
            "lunarDate" to lunarDateSummary,
            "lunarRepeat" to lunarRepeatSummary,
            "lunarAdvanceDays" to lunarAdvanceDays.toString(),
            "lunarAdvanceEnabled" to lunarAdvanceEnabled.toString()
        )
        FeatureType.MEDICINE -> mapOf(
            "drugName" to medicineDrugName,
            "dosage" to medicineDosage,
            "voiceEnabled" to medicineVoiceEnabled.toString(),
            "medTimes" to medicineTimes.joinToString("|"),
            "medLabels" to medicineTimeLabels.joinToString("|")
        )
    }

    private fun medNameForType(type: FeatureType): String {
        // Check edit buffer for the currently editing instance
        if (editMedicineName != null && currentEditingInstanceId != null) {
            val editing = alarmInstances.firstOrNull { it.id == currentEditingInstanceId }
            if (editing?.type == type) return editMedicineName ?: ""
        }
        return alarmInstances.find { it.type == type && it.config.containsKey("medicineName") }
            ?.config?.get("medicineName") ?: ""
    }

    private fun loadConfigFromInstance(instance: AlarmInstance) {
        val c = instance.config
        when (instance.type) {
            FeatureType.REGULAR -> {
                regularRepeatSummary = c["repeatSummary"] ?: regularRepeatSummary
                c["repeatSelections"]?.takeIf { it.isNotBlank() }?.let {
                    regularRepeatSelections.clear()
                    regularRepeatSelections.addAll(it.split("|").filter { s -> s.isNotBlank() })
                }
            }
            FeatureType.SPECIAL -> {
                specialRepeatValue = c["repeatValue"]?.toIntOrNull() ?: specialRepeatValue
                specialRepeatUnit = c["repeatUnit"] ?: specialRepeatUnit
                specialRepeatHours = c["repeatHours"]?.toIntOrNull() ?: specialRepeatHours
                specialRepeatMinutes = c["repeatMinutes"]?.toIntOrNull() ?: specialRepeatMinutes
                c["weekdaySelections"]?.let {
                    specialWeekdaySelections.clear()
                    specialWeekdaySelections.addAll(it.split("|").filter { s -> s.isNotBlank() })
                }
            }
            FeatureType.SHIFT -> {
                shiftCycleDays = c["cycleDays"]?.toIntOrNull() ?: shiftCycleDays
                c["daySummaries"]?.let {
                    shiftDaySummaries.clear()
                    shiftDaySummaries.addAll(it.split("|").filter { s -> s.isNotBlank() })
                }
            }
            FeatureType.LUNAR -> {
                lunarDateSummary = c["lunarDate"] ?: lunarDateSummary
                lunarRepeatSummary = c["lunarRepeat"] ?: lunarRepeatSummary
                lunarAdvanceDays = c["lunarAdvanceDays"]?.toIntOrNull() ?: lunarAdvanceDays
                lunarAdvanceEnabled = c["lunarAdvanceEnabled"]?.toBooleanStrictOrNull() ?: lunarAdvanceEnabled
            }
            FeatureType.MEDICINE -> {
                medicineDrugName = c["drugName"] ?: medicineDrugName
                medicineDosage = c["dosage"] ?: medicineDosage
                medicineVoiceEnabled = c["voiceEnabled"]?.toBooleanStrictOrNull() ?: medicineVoiceEnabled
                c["medTimes"]?.takeIf { it.isNotBlank() }?.let {
                    medicineTimes.clear(); medicineTimes.addAll(it.split("|").filter { s -> s.isNotBlank() })
                }
                c["medLabels"]?.takeIf { it.isNotBlank() }?.let {
                    medicineTimeLabels.clear(); medicineTimeLabels.addAll(it.split("|").filter { s -> s.isNotBlank() })
                }
            }
        }
    }

    private fun alarmInstanceDateText(instance: AlarmInstance): String {
        val dateMs = instanceDateMs(instance.id)
        if (dateMs <= 0L) return "未设置"
        val cal = Calendar.getInstance().apply { timeInMillis = dateMs }
        val sdf = SimpleDateFormat("yyyy/M/d", Locale.CHINA)
        val weekDay = listOf("日", "一", "二", "三", "四", "五", "六")[cal.get(Calendar.DAY_OF_WEEK) - 1]
        return "${sdf.format(cal.time)} 周$weekDay"
    }

    private fun alarmInstanceRuleText(instance: AlarmInstance): String {
        return when (instance.type) {
            FeatureType.REGULAR -> instance.config["repeatSummary"] ?: regularRepeatSummary
            FeatureType.SPECIAL -> {
                val unit = instance.config["repeatUnit"] ?: specialRepeatUnit
                val value = instance.config["repeatValue"] ?: specialRepeatValue.toString()
                if (unit == "时分") {
                    val h = instance.config["repeatHours"]?.toIntOrNull() ?: specialRepeatHours
                    val m = instance.config["repeatMinutes"]?.toIntOrNull() ?: specialRepeatMinutes
                    when {
                        h > 0 && m > 0 -> "每${h}小时${m}分钟"
                        h > 0 -> "每${h}小时"
                        else -> "每${m}分钟"
                    }
                } else if (unit == "周") {
                    val weekdays = instance.config["weekdaySelections"]?.split("|")?.joinToString("、") ?: ""
                    "每${value}周（周$weekdays）"
                } else {
                    "每${value}$unit"
                }
            }
            FeatureType.SHIFT -> "每轮 ${instance.config["cycleDays"]?.toIntOrNull() ?: shiftCycleDays} 天"
            FeatureType.LUNAR -> {
                val date = instance.config["lunarDate"] ?: lunarDateSummary
                val repeat = instance.config["lunarRepeat"] ?: lunarRepeatSummary
                val advanceEnabled = instance.config["lunarAdvanceEnabled"]?.toBooleanStrictOrNull() ?: false
                val advanceDays = instance.config["lunarAdvanceDays"]?.toIntOrNull() ?: 0
                if (advanceEnabled && advanceDays > 0) {
                    "$date · $repeat · 提前${advanceDays}天"
                } else {
                    "$date · $repeat"
                }
            }
            FeatureType.MEDICINE -> {
                val voiceEnabled = instance.config["voiceEnabled"]?.toBooleanStrictOrNull() ?: medicineVoiceEnabled
                val timesRaw = instance.config["medTimes"] ?: medicineTimes.joinToString("|")
                val count = timesRaw.split("|").filter { it.isNotBlank() }.size
                val timeDesc = when {
                    count <= 1 -> "每日 1 次"
                    else -> "每日 $count 次"
                }
                if (voiceEnabled) "$timeDesc · 语音播报" else timeDesc
            }
        }
    }

    private fun instanceTitle(instanceId: String): String? {
        return editTitle ?: alarmInstances.firstOrNull { it.id == instanceId }?.title
    }

    private fun instanceDateMs(instanceId: String): Long {
        return editDateMs ?: alarmInstances.firstOrNull { it.id == instanceId }?.dateMs ?: 0L
    }

    private fun alarmInstanceNextText(instance: AlarmInstance): String {
        if (!instance.active) return "已关闭"
        val nextMs = nextRingTimeMs(instance) ?: return "待计算"
        return nextRingDisplayText(instance.id, nextMs)
    }

    private fun datePrefixFor(nextMs: Long, showYear: Boolean): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = nextMs }
        val year = cal.get(java.util.Calendar.YEAR) % 100
        val month = cal.get(java.util.Calendar.MONTH) + 1
        val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = String.format("%02d", cal.get(java.util.Calendar.MINUTE))
        return if (showYear) {
            "下次提醒：${year}年${month}月${day}日 ${hour}:${minute}"
        } else {
            "下次提醒：${month}月${day}日 ${hour}:${minute}"
        }
    }

    private fun nextRingDisplayText(instanceId: String, nextMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val countdown = countdownText(nextMs, nowMs)
        val showYear = countdown.contains("年")
        val prefix = datePrefixFor(nextMs, showYear)
        return "$prefix  |  $countdown"
    }

    private fun nextRingTimeMs(instance: AlarmInstance): Long? {
        val plans = schedulePlansForInstance(instance.copy(active = true))
        if (plans.isEmpty()) return null
        val now = System.currentTimeMillis()
        return plans.minOfOrNull { plan ->
            when {
                plan.note == "法定工作日" -> LegalWorkdayHelper.nextWorkdayAtOrAfter(
                    now, plan.hour, plan.minute
                )
                plan.repeatMonths > 0 -> AlarmTimeCalculator.calculateNextMonthInterval(
                    plan.startDateMs, plan.repeatMonths, now
                )
                plan.repeatMinutes > 0 -> AlarmTimeCalculator.calculateNextMinuteInterval(
                    plan.startDateMs, plan.repeatMinutes, now
                )
                else -> AlarmTimeCalculator.calculateNextTime(
                    plan.hour, plan.minute, plan.intervalDays, plan.startDateMs, now
                ).nextTimeMs
            }
        }
    }

    private fun countdownText(nextMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val diffMs = nextMs - nowMs
        if (diffMs <= 0L) return "即将响铃"

        val totalSeconds = diffMs / 1000L
        val rawMonths = totalSeconds / (30L * 24 * 3600)
        val years = rawMonths / 12
        val months = rawMonths % 12
        val remainderAfterMonths = totalSeconds % (30L * 24 * 3600)
        val days = remainderAfterMonths / (24L * 3600)
        val remainderAfterDays = remainderAfterMonths % (24L * 3600)
        val hours = remainderAfterDays / 3600
        val remainderAfterHours = remainderAfterDays % 3600
        val minutes = remainderAfterHours / 60
        val seconds = remainderAfterHours % 60

        val parts = mutableListOf<String>()
        if (years > 0) parts.add("${years}年")
        if (months > 0) parts.add("${months}个月")
        if (days > 0) parts.add("${days}天")
        // Only show sub-day units when no years
        if (years <= 0 && hours > 0) parts.add("${hours}小时")
        // Sub-month precision only when no months/years present
        if (years <= 0 && rawMonths <= 0 && minutes > 0) parts.add("${minutes}分钟")
        // Seconds only shown when within 24h — otherwise meaningless flicker
        if (years <= 0 && rawMonths <= 0 && days == 0L) parts.add("${seconds}秒")

        return "还有 " + parts.joinToString("")
    }

    private fun startCountdownRefresh() {
        if (countdownRunning) return
        countdownRunning = true
        countdownHandler.post(object : Runnable {
            override fun run() {
                if (!countdownRunning) return
                refreshAllCountdowns()
                countdownHandler.postDelayed(this, 1_000L)
            }
        })
    }

    private fun stopCountdownRefresh() {
        countdownRunning = false
        countdownHandler.removeCallbacksAndMessages(null)
    }

    private fun refreshAllCountdowns() {
        if (countdownViews.isEmpty()) return
        val now = System.currentTimeMillis()
        alarmInstances.forEach { inst ->
            countdownViews[inst.id]?.let { view ->
                if (inst.active) {
                    // Recompute next ring time only when cache is stale
                    var nextMs = nextRingMsCache[inst.id]
                    if (nextMs == null || nextMs <= now) {
                        nextMs = nextRingTimeMs(inst)
                        if (nextMs != null) nextRingMsCache[inst.id] = nextMs
                    }
                    if (nextMs != null) {
                        view.text = nextRingDisplayText(inst.id, nextMs, now)
                        // Urgency: red < 10min, amber < 60min, else gray
                        val remaining = nextMs - now
                        view.setTextColor(when {
                            remaining <= 600_000L -> 0xFFFF7043.toInt()   // red within 10min
                            remaining <= 3_600_000L -> 0xFFF59E0B.toInt() // amber within 60min
                            else -> 0xFF6B7280.toInt()                    // normal gray
                        })
                    } else {
                        view.text = "待计算"
                        view.setTextColor(0xFF6B7280.toInt())
                    }
                }
            }
        }
    }

    private fun loadUiState() {
        editHour = statePrefs.getInt("editHour", editHour)
        editMinute = statePrefs.getInt("editMinute", editMinute)
        shiftCycleDays = statePrefs.getInt("shiftCycleDays", shiftCycleDays)
        statePrefs.getString("shiftDaySummaries", null)?.let {
            shiftDaySummaries.clear()
            shiftDaySummaries.addAll(it.split("|").filter { item -> item.isNotBlank() })
        }
        ringtoneSummary = statePrefs.getString("ringtoneSummary", ringtoneSummary) ?: ringtoneSummary
        selectedRingtoneUri = statePrefs.getString("selectedRingtoneUri", selectedRingtoneUri)
        vibrationEnabled = statePrefs.getBoolean("vibrationEnabled", vibrationEnabled)
        ringDurationMinutes = statePrefs.getInt("ringDurationMinutes", ringDurationMinutes)
        snoozeMinutes = statePrefs.getInt("snoozeMinutes", snoozeMinutes)
        snoozeEnabled = statePrefs.getBoolean("snoozeEnabled", snoozeEnabled)
        // Load edit-buffer singletons (pre-fill for new alarm creation)
        regularRepeatSummary = statePrefs.getString("regularRepeatSummary", regularRepeatSummary) ?: regularRepeatSummary
        statePrefs.getString("regularRepeatSelections", null)?.takeIf { it.isNotBlank() }?.let {
            regularRepeatSelections.clear()
            regularRepeatSelections.addAll(it.split("|").filter { item -> item.isNotBlank() })
        }
        specialRepeatValue = statePrefs.getInt("specialRepeatValue", specialRepeatValue)
        specialRepeatUnit = statePrefs.getString("specialRepeatUnit", specialRepeatUnit) ?: specialRepeatUnit
        if (specialRepeatUnit == "分钟" || specialRepeatUnit == "小时") specialRepeatUnit = "时分"
        specialRepeatHours = statePrefs.getInt("specialRepeatHours", specialRepeatHours)
        specialRepeatMinutes = statePrefs.getInt("specialRepeatMinutes", specialRepeatMinutes)
        specialRepeatValue = specialRepeatValue.coerceIn(specialRepeatRange(specialRepeatUnit))
        statePrefs.getString("specialWeekdaySelections", null)?.let {
            specialWeekdaySelections.clear()
            specialWeekdaySelections.addAll(it.split("|").filter { item -> item.isNotBlank() })
        }
        lunarDateSummary = statePrefs.getString("lunarDateSummary", lunarDateSummary) ?: lunarDateSummary
        lunarRepeatSummary = statePrefs.getString("lunarRepeatSummary", lunarRepeatSummary) ?: lunarRepeatSummary
        lunarAdvanceDays = statePrefs.getInt("lunarAdvanceDays", lunarAdvanceDays)
        lunarAdvanceEnabled = statePrefs.getBoolean("lunarAdvanceEnabled", lunarAdvanceEnabled)
        medicineDrugName = statePrefs.getString("medicineDrugName", medicineDrugName) ?: medicineDrugName
        medicineDosage = statePrefs.getString("medicineDosage", medicineDosage) ?: medicineDosage
        medicineVoiceEnabled = statePrefs.getBoolean("medicineVoiceEnabled", medicineVoiceEnabled)
        statePrefs.getString("medTimes", null)?.takeIf { it.isNotBlank() }?.let {
            medicineTimes.clear(); medicineTimes.addAll(it.split("|").filter { s -> s.isNotBlank() })
        }
        statePrefs.getString("medLabels", null)?.takeIf { it.isNotBlank() }?.let {
            medicineTimeLabels.clear(); medicineTimeLabels.addAll(it.split("|").filter { s -> s.isNotBlank() })
        }
        // Load alarm instances
        val json = statePrefs.getString("alarm_instances_json", null)
        if (json != null) {
            alarmInstances.clear()
            alarmInstances.addAll(parseInstancesFromJson(json))
        } else {
            migrateFromOldFormat()
        }
    }

    private fun saveUiState() {
        statePrefs.edit()
            .putInt("editHour", editHour)
            .putInt("editMinute", editMinute)
            .putInt("shiftCycleDays", shiftCycleDays)
            .putString("shiftDaySummaries", shiftDaySummaries.joinToString("|"))
            .putString("ringtoneSummary", ringtoneSummary)
            .putString("selectedRingtoneUri", selectedRingtoneUri)
            .putBoolean("vibrationEnabled", vibrationEnabled)
            .putInt("ringDurationMinutes", ringDurationMinutes)
            .putInt("snoozeMinutes", snoozeMinutes)
            .putBoolean("snoozeEnabled", snoozeEnabled)
            .putString("regularRepeatSummary", regularRepeatSummary)
            .putString("regularRepeatSelections", regularRepeatSelections.joinToString("|"))
            .putInt("specialRepeatValue", specialRepeatValue)
            .putString("specialRepeatUnit", specialRepeatUnit)
            .putInt("specialRepeatHours", specialRepeatHours)
            .putInt("specialRepeatMinutes", specialRepeatMinutes)
            .putString("specialWeekdaySelections", specialWeekdaySelections.joinToString("|"))
            .putString("lunarDateSummary", lunarDateSummary)
            .putString("lunarRepeatSummary", lunarRepeatSummary)
            .putInt("lunarAdvanceDays", lunarAdvanceDays)
            .putBoolean("lunarAdvanceEnabled", lunarAdvanceEnabled)
            .putString("medicineDrugName", medicineDrugName)
            .putString("medicineDosage", medicineDosage)
            .putBoolean("medicineVoiceEnabled", medicineVoiceEnabled)
            .putString("medTimes", medicineTimes.joinToString("|"))
            .putString("medLabels", medicineTimeLabels.joinToString("|"))
            .putString("alarm_instances_json", serializeInstancesToJson(alarmInstances))
            .apply()
    }

    private fun pill(text: String, selected: Boolean, accent: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        gravity = Gravity.CENTER
        isHapticFeedbackEnabled = false
        isSoundEffectsEnabled = false
        setTextColor(if (selected) 0xFFFFFFFF.toInt() else 0xFF64748B.toInt())
        background = rounded(if (selected) accent else 0xFFF1F5F9.toInt(), dp(16))
        setPadding(dp(12), dp(7), dp(12), dp(7))
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
    }

    private fun buttonText(text: String, accent: Int, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 14.5f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        isHapticFeedbackEnabled = false
        isSoundEffectsEnabled = false
        setTextColor(0xFFFFFFFF.toInt())
        background = rounded(accent, dp(10))
        setPadding(0, dp(11), 0, dp(11))
        setOnClickListener { onClick() }
    }

    private fun rounded(color: Int, radius: Int, strokeColor: Int? = null, dashed: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            if (strokeColor != null) {
                if (dashed) setStroke(dp(1), strokeColor, dp(6).toFloat(), dp(4).toFloat())
                else setStroke(dp(1), strokeColor)
            }
        }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    private fun todayText(): String = SimpleDateFormat("yyyy-M-d E", Locale.CHINA).format(Calendar.getInstance().time)

    private fun weekName(calendar: Calendar): String = SimpleDateFormat("E", Locale.CHINA).format(calendar.time)

    @Suppress("DEPRECATION")
    private fun currentVersionName(): String =
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.13"

    private fun labelTextView(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFF111827.toInt())
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun collectCoreReliabilityIssues(): List<String> {
        val issues = mutableListOf<String>()
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) issues += "通知"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) issues += "悬浮窗"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerMgr = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerMgr.isIgnoringBatteryOptimizations(packageName)) issues += "电池优化"
        }
        return issues
    }

    /**
     * First-launch onboarding: guide the user through the 3 most critical permissions
     * one step at a time. Each step can be skipped. Steps only show when the
     * corresponding permission is actually missing.
     */
    private fun maybeShowReliabilityStarter() {
        val prefs = getSharedPreferences("reliability_setup", Context.MODE_PRIVATE)
        if (prefs.getBoolean("onboarding_completed", false)) return

        // Collect steps that are not yet satisfied
        val steps = mutableListOf<Pair<String, () -> Unit>>()

        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            steps.add("通知权限" to {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:$packageName"))
                }
                try { startActivity(intent) } catch (_: Exception) {}
            })
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                steps.add("电池优化" to {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:$packageName"))
                    try { startActivity(intent) } catch (_: Exception) {}
                })
            }
        }

        // Always include the final "complete setup" step
        steps.add("完成设置" to {
            startActivity(Intent(this, ReliabilityCheckActivity::class.java))
        })

        if (steps.isEmpty()) {
            prefs.edit().putBoolean("onboarding_completed", true).apply()
            return
        }

        showOnboardingStep(prefs, steps, 0)
    }

    private fun showOnboardingStep(
        prefs: android.content.SharedPreferences,
        steps: List<Pair<String, () -> Unit>>,
        index: Int
    ) {
        if (index >= steps.size) {
            prefs.edit().putBoolean("onboarding_completed", true).apply()
            return
        }

        val (title, action) = steps[index]
        val isLast = index == steps.size - 1

        val descriptions = mapOf(
            "通知权限" to "闹钟触发时需要通知权限来显示响铃提醒和备注信息。",
            "电池优化" to "关闭电池优化可以防止锁屏后系统自动关闭闹钟，确保准时响铃。",
            "完成设置" to "最后一步：检查所有权限是否就绪，并测试闹钟是否正常。"
        )

        AlertDialog.Builder(this)
            .setTitle("${index + 1}/${steps.size}  $title")
            .setMessage(descriptions[title] ?: "")
            .setPositiveButton(if (isLast) "去完成" else "去设置") { _, _ ->
                action()
                showOnboardingStep(prefs, steps, index + 1)
            }
            .setNegativeButton(if (isLast) "稍后" else "跳过") { _, _ ->
                showOnboardingStep(prefs, steps, index + 1)
            }
            .setCancelable(false)
            .show()
    }

    private fun maybeShowReliabilityReminder() {
        val issues = collectCoreReliabilityIssues()
        if (issues.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("建议先完善必要权限")
            .setMessage("当前还需检查：${issues.joinToString("、")}。这些权限会影响锁屏、后台和低电量场景下的闹钟提醒可靠性。")
            .setPositiveButton("去检查") { _, _ ->
                startActivity(Intent(this, ReliabilityCheckActivity::class.java))
            }
            .setNegativeButton("稍后", null)
            .show()
    }
}
