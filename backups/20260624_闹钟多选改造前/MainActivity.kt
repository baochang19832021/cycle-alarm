package com.cyclealarm.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.media.RingtoneManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.cyclealarm.app.WheelView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// SimpleAlarmItem / SimpleAlarmAdapter for VIEW mode
data class SimpleAlarmItem(
    val id: String,
    val hour: Int,
    val minute: Int,
    val infoText: String,
    var isActive: Boolean
)

class SimpleAlarmAdapter(
    private var items: List<SimpleAlarmItem>,
    private val onClick: (SimpleAlarmItem) -> Unit,
    private val onDeleteClick: (SimpleAlarmItem) -> Unit,
    private val onToggle: (SimpleAlarmItem, Boolean) -> Unit
) : RecyclerView.Adapter<SimpleAlarmAdapter.SimpleViewHolder>() {

    inner class SimpleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTime: TextView = itemView.findViewById(R.id.tvItemTime)
        val tvAmPm: TextView = itemView.findViewById(R.id.tvItemAmPm)
        val tvInfo: TextView = itemView.findViewById(R.id.tvItemInfo)
        val switchEnabled: MaterialSwitch = itemView.findViewById(R.id.switchItemEnabled)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SimpleViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alarm_card, parent, false)
        return SimpleViewHolder(view)
    }

    override fun onBindViewHolder(holder: SimpleViewHolder, position: Int) {
        val item = items[position]
        holder.tvTime.text = String.format("%02d:%02d", item.hour, item.minute)
        holder.tvAmPm.text = if (item.hour < 12) "上午" else "下午"
        holder.tvInfo.text = item.infoText
        holder.switchEnabled.setOnCheckedChangeListener(null)
        holder.switchEnabled.isChecked = item.isActive
        val colorActive = 0xFF1A1A1A.toInt()
        val colorInactive = 0xFFBBBBBB.toInt()
        holder.tvTime.setTextColor(if (item.isActive) colorActive else colorInactive)
        holder.tvInfo.setTextColor(if (item.isActive) 0xFFAAAAAA.toInt() else 0xFFCCCCCC.toInt())
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener {
            onDeleteClick(item)
            true
        }
        holder.switchEnabled.setOnCheckedChangeListener { _, isChecked -> onToggle(item, isChecked) }
    }

    override fun getItemCount(): Int = items.size
}

// MainActivity with VIEW/EDIT dual mode
class MainActivity : AppCompatActivity() {

    private var isViewMode = false
    private var editAlarmId: String? = null

    private var selectedHour = 8
    private var selectedMinute = 0
    private var selectedDays = 40
    private var selectedStartDate: Calendar? = null
    private var selectedNote: String = ""
    private var selectedRingtoneUri: String? = null
    private var selectedVibrate: Boolean = true

    private var mediaPlayer: MediaPlayer? = null
    private var isPlayingPreview = false
    private lateinit var btnPreviewRingtone: ImageButton

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) Toast.makeText(this, "需要通知权限才能提醒", Toast.LENGTH_LONG).show()
    }

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
            val displayName = if (uri != null) getFileNameFromUri(uri) else "系统默认"
            findViewById<TextView>(R.id.tvRingtoneValue).text = displayName
            updatePreviewButtonVisibility()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        isViewMode = intent.action == AlarmListActivity.ACTION_VIEW

        if (isViewMode) {
            setupViewMode()
        } else {
            setupEditMode()
        }
    }

    private fun setupViewMode() {
        findViewById<LinearLayout>(R.id.layoutViewMode).visibility = View.VISIBLE
        findViewById<LinearLayout>(R.id.layoutEditMode).visibility = View.GONE

        val alarmType = intent.getStringExtra(AlarmListActivity.EXTRA_TYPE) ?: ""
        findViewById<TextView>(R.id.tvSubTitle).text = "闹钟"

        val rvList = findViewById<RecyclerView>(R.id.rvAlarmList)
        rvList.layoutManager = LinearLayoutManager(this)
        loadViewModeAlarms(rvList, alarmType)

        findViewById<FloatingActionButton>(R.id.fabAddAlarm).setOnClickListener {
            switchToEditMode(null)
        }
    }

    private fun loadViewModeAlarms(rvList: RecyclerView, alarmType: String) {
        val all = AlarmScheduler.getAllAlarms(this)
        val filtered = when (alarmType) {
            AlarmListActivity.TYPE_INTERVAL -> all
            else -> emptyList()
        }

        if (filtered.isEmpty()) {
            rvList.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun getItemCount() = 1
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val tv = TextView(parent.context).apply {
                        text = "还没有闹钟"
                        setTextColor(0xFFCCCCCC.toInt())
                        textSize = 15f
                        gravity = Gravity.CENTER
                        setPadding(48, 120, 48, 48)
                    }
                    return object : RecyclerView.ViewHolder(tv) {}
                }
                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
            }
            return
        }

        val items = filtered.map { data ->
            val cal = Calendar.getInstance().apply { timeInMillis = data.nextTimeMs }
            SimpleAlarmItem(
                id = data.id,
                hour = cal.get(Calendar.HOUR_OF_DAY),
                minute = cal.get(Calendar.MINUTE),
                infoText = buildInfoText(data),
                isActive = data.isActive && data.nextTimeMs > System.currentTimeMillis()
            )
        }

        rvList.adapter = SimpleAlarmAdapter(items,
            onClick = { item -> switchToEditMode(item.id) },
            onDeleteClick = { item ->
                AlertDialog.Builder(this)
                    .setTitle("删除闹钟")
                    .setMessage("确定要删除这个闹钟吗？")
                    .setPositiveButton("删除") { _, _ ->
                        AlarmScheduler.deleteAlarm(this, item.id)
                        loadViewModeAlarms(rvList, alarmType)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            },
            onToggle = { item, isChecked ->
                if (!isChecked) AlarmScheduler.cancelById(this, item.id)
                else AlarmScheduler.rescheduleNext(this, item.id)
                loadViewModeAlarms(rvList, alarmType)
            }
        )
    }

    private fun switchToEditMode(alarmId: String?) {
        isViewMode = false
        editAlarmId = alarmId
        findViewById<LinearLayout>(R.id.layoutViewMode).visibility = View.GONE
        findViewById<LinearLayout>(R.id.layoutEditMode).visibility = View.VISIBLE
        rebuildEditMode()
    }

    private fun setupEditMode() {
        findViewById<LinearLayout>(R.id.layoutViewMode).visibility = View.GONE
        findViewById<LinearLayout>(R.id.layoutEditMode).visibility = View.VISIBLE
        rebuildEditMode()
    }

    private fun rebuildEditMode() {
        findViewById<View>(R.id.btnCancelEdit).setOnClickListener { finish() }

        // Title
        findViewById<TextView>(R.id.tvEditTitle).text =
            if (editAlarmId != null) "编辑闹钟" else "新建闹钟"

        // Permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmMgr = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmMgr.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle("需要精确闹钟权限")
                    .setMessage("请前往 设置 → 应用 → 周期闹钟 → 闹钟和提醒 → 允许。")
                    .setPositiveButton("去设置") { _, _ ->
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }

        val tvStartDateValue = findViewById<TextView>(R.id.tvStartDateValue)
        val tvNoteValue     = findViewById<TextView>(R.id.tvNoteValue)
        val tvIntervalValue = findViewById<TextView>(R.id.tvIntervalValue)
        val tvRingtoneValue = findViewById<TextView>(R.id.tvRingtoneValue)
        val switchVibrate   = findViewById<MaterialSwitch>(R.id.switchVibrate)

        btnPreviewRingtone = findViewById(R.id.btnPreviewRingtone)
        btnPreviewRingtone.setOnClickListener { toggleRingtonePreview() }
        updatePreviewButtonVisibility()

        // Inline Time Picker Wheels
        val wheelContainer = findViewById<FrameLayout>(R.id.wheelContainer)
        wheelContainer.removeAllViews()

        val wheelsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val hourGroup = FrameLayout(this)
        val hourWheel = WheelView(this, 52, 3).apply {
            isCyclic = true
            items = (0..23).map { String.format("%02d", it) }
            currentIndex = selectedHour
            onIndexChanged = { idx ->
                selectedHour = idx
                refreshNextAlarmSubtitle()
            }
        }
        val hourLabel = TextView(this).apply {
            text = "时"
            textSize = 18f
            setTextColor(android.graphics.Color.parseColor("#666666"))
            gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_VERTICAL or Gravity.RIGHT
            )
            setPadding(dp(4), 0, dp(8), 0)
        }
        hourGroup.addView(hourWheel)
        hourGroup.addView(hourLabel)
        hourGroup.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val minuteGroup = FrameLayout(this)
        val minuteWheel = WheelView(this, 52, 3).apply {
            isCyclic = true
            items = (0..59).map { String.format("%02d", it) }
            currentIndex = selectedMinute
            onIndexChanged = { idx ->
                selectedMinute = idx
                refreshNextAlarmSubtitle()
            }
        }
        val minuteLabel = TextView(this).apply {
            text = "分"
            textSize = 18f
            setTextColor(android.graphics.Color.parseColor("#666666"))
            gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_VERTICAL or Gravity.RIGHT
            )
            setPadding(dp(4), 0, dp(8), 0)
        }
        minuteGroup.addView(minuteWheel)
        minuteGroup.addView(minuteLabel)
        minuteGroup.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        wheelsRow.addView(hourGroup)
        wheelsRow.addView(minuteGroup)
        wheelContainer.addView(wheelsRow)

        // Load existing data if editing
        if (editAlarmId != null) {
            val existing = AlarmScheduler.loadAlarm(this, editAlarmId!!)
            if (existing != null) {
                selectedHour = existing.hour
                selectedMinute = existing.minute
                selectedDays = existing.intervalDays
                selectedStartDate = if (existing.startDateMs > 0L)
                    Calendar.getInstance().apply { timeInMillis = existing.startDateMs }
                else null
                selectedNote = existing.note
                selectedRingtoneUri = existing.ringtoneUri
                selectedVibrate = existing.vibrate
            }
        } else {
            val first = AlarmScheduler.getAllAlarms(this).firstOrNull()
            if (first != null) selectedVibrate = first.vibrate
        }

        hourWheel.currentIndex = selectedHour
        minuteWheel.currentIndex = selectedMinute

        // Update UI
        tvStartDateValue.text  = formatStartDate(selectedStartDate)
        tvNoteValue.text       = if (selectedNote.isEmpty()) "无" else selectedNote
        selectedDays = selectedDays.coerceIn(0, 365)
        tvIntervalValue.text    = if (selectedDays == 0) "0 天（当天一次）" else "$selectedDays 天"
        switchVibrate.isChecked = selectedVibrate
        if (selectedRingtoneUri != null) {
            tvRingtoneValue.text = getFileNameFromUri(Uri.parse(selectedRingtoneUri!!))
        } else {
            tvRingtoneValue.text = "系统默认"
        }
        refreshNextAlarmSubtitle()
        updatePreviewButtonVisibility()

        // Click Handlers
        val onIntervalClick: View.OnClickListener = View.OnClickListener { showIntervalPicker() }
        tvIntervalValue.setOnClickListener(onIntervalClick)
        findViewById<View>(R.id.rowInterval).setOnClickListener(onIntervalClick)

        val onStartDateClick: View.OnClickListener = View.OnClickListener { showDatePicker() }
        tvStartDateValue.setOnClickListener(onStartDateClick)
        findViewById<View>(R.id.rowStartDate).setOnClickListener(onStartDateClick)

        val onNoteClick: View.OnClickListener = View.OnClickListener { showNoteEditor() }
        tvNoteValue.setOnClickListener(onNoteClick)
        findViewById<View>(R.id.rowNote).setOnClickListener(onNoteClick)

        val onRingtoneClick: View.OnClickListener = View.OnClickListener { showRingtonePicker() }
        tvRingtoneValue.setOnClickListener(onRingtoneClick)
        findViewById<View>(R.id.rowRingtone).setOnClickListener(onRingtoneClick)

        findViewById<View>(R.id.btnConfirmEdit).setOnClickListener { doSaveAndEnable() }

        switchVibrate.setOnCheckedChangeListener { _, isChecked -> selectedVibrate = isChecked }

        // MIUI hint
        val prefs = getSharedPreferences(AlarmScheduler.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("miui_hint_shown", false)) {
            AlertDialog.Builder(this)
                .setTitle("小米用户注意")
                .setMessage("请前往 设置 → 应用管理 → 周期闹钟 → 省电策略 → 无限制，并允许自启动，否则闹钟可能不响。")
                .setPositiveButton("知道了") { _, _ ->
                    prefs.edit().putBoolean("miui_hint_shown", true).apply()
                }
                .show()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == AlarmService.ACTION_STOP) {
            AlarmService.stop(this)
        }
    }

    private fun buildInfoText(data: AlarmScheduler.AlarmData): String {
        return buildString {
            append(if (data.intervalDays == 0) "当天一次" else "每${data.intervalDays}天")
            append(" | ")
            val diffMs = data.nextTimeMs - System.currentTimeMillis()
            if (diffMs > 0) {
                val totalMinutes = diffMs / (1000 * 60)
                val totalDays = totalMinutes / (24 * 60)
                val hours = (totalMinutes % (24 * 60)) / 60
                val minutes = totalMinutes % 60
                when {
                    totalDays >= 30 -> {
                        val months = totalDays / 30
                        val remainingDays = totalDays % 30
                        append(
                            if (remainingDays > 0) "${months}个月${remainingDays}天后响铃"
                            else "${months}个月后响铃"
                        )
                    }
                    totalDays > 0 -> append("${totalDays}天${hours}小时后响铃")
                    hours > 0 -> append("${hours}小时${minutes}分钟后响铃")
                    else -> append("${minutes}分钟后响铃")
                }
            } else {
                append("已过期")
            }
        }
    }

    private fun showDatePicker() {
        val calendar = selectedStartDate ?: Calendar.getInstance()
        android.app.DatePickerDialog(
            this,
            { _, y, m, d ->
                selectedStartDate = Calendar.getInstance().apply {
                    set(Calendar.YEAR, y); set(Calendar.MONTH, m); set(Calendar.DAY_OF_MONTH, d)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                findViewById<TextView>(R.id.tvStartDateValue).text = formatStartDate(selectedStartDate)
                refreshNextAlarmSubtitle()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply { datePicker.minDate = 0; show() }
    }

    private fun formatStartDate(date: Calendar?): String {
        if (date == null) return "今天"
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return when (date.timeInMillis) {
            today.timeInMillis -> "今天"
            today.timeInMillis - 86_400_000L -> "昨天"
            today.timeInMillis + 86_400_000L -> "明天"
            else -> SimpleDateFormat("M月d日", Locale.CHINA).format(date.timeInMillis)
        }
    }

    private fun showIntervalPicker() {
        IntervalPickerDialog(this, selectedDays) { days ->
            selectedDays = days
            findViewById<TextView>(R.id.tvIntervalValue).text =
                if (days == 0) "0 天（当天一次）" else "$days 天"
            refreshNextAlarmSubtitle()
        }.show()
    }

    private fun showNoteEditor() {
        val editText = android.widget.EditText(this).apply {
            hint = "输入备注内容"
            setText(selectedNote)
            isSingleLine = false
            maxLines = 4
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("备注")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                selectedNote = editText.text.toString().trim()
                findViewById<TextView>(R.id.tvNoteValue).text =
                    if (selectedNote.isEmpty()) "无" else selectedNote
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showRingtonePicker() {
        AlertDialog.Builder(this)
            .setTitle("选择闹铃")
            .setItems(arrayOf("从系统铃声中选择", "系统默认闹铃")) { _, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择闹钟铃声")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            selectedRingtoneUri?.let {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
                            }
                        }
                        ringtonePickerLauncher.launch(intent)
                    }
                    1 -> {
                        selectedRingtoneUri = null
                        findViewById<TextView>(R.id.tvRingtoneValue).text = "系统默认"
                    }
                }
            }
            .show()
    }

    private fun toggleRingtonePreview() {
        if (isPlayingPreview) stopPreview() else startPreview()
    }

    private fun startPreview() {
        try {
            mediaPlayer?.release(); mediaPlayer = null
            val uri = if (selectedRingtoneUri != null) Uri.parse(selectedRingtoneUri)
                      else android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@MainActivity, uri)
                setAudioStreamType(android.media.AudioManager.STREAM_ALARM)
                setOnCompletionListener { stopPreview() }
                prepare(); start()
            }
            isPlayingPreview = true
            btnPreviewRingtone.setImageResource(android.R.drawable.ic_media_pause)
            Toast.makeText(this, "正在播放...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "无法播放: ${e.message}", Toast.LENGTH_LONG).show()
            stopPreview()
        }
    }

    private fun stopPreview() {
        try { mediaPlayer?.release() } catch (_: Exception) { }
        mediaPlayer = null; isPlayingPreview = false
        if (::btnPreviewRingtone.isInitialized)
            btnPreviewRingtone.setImageResource(android.R.drawable.ic_media_play)
    }

    private fun updatePreviewButtonVisibility() {
        if (::btnPreviewRingtone.isInitialized)
            btnPreviewRingtone.visibility = View.VISIBLE
    }

    private fun doSaveAndEnable() {
        val startLabel = when (formatStartDate(selectedStartDate)) {
            "今天" -> "从当天"; "昨天" -> "从昨天"; "明天" -> "从明天"
            else -> "从${formatStartDate(selectedStartDate)}"
        }
        val label = if (selectedDays == 0) "给妈挂号（当天一次）"
        else "给妈挂号（${startLabel}起每${selectedDays}天）"

        val data = if (editAlarmId != null) {
            AlarmScheduler.loadAlarm(this, editAlarmId!!)?.also { e ->
                e.hour = selectedHour; e.minute = selectedMinute
                e.intervalDays = selectedDays
                e.startDateMs = selectedStartDate?.timeInMillis ?: 0L
                e.ringtoneUri = selectedRingtoneUri; e.label = label
                e.note = selectedNote; e.vibrate = selectedVibrate
            } ?: AlarmScheduler.AlarmData(id = editAlarmId!!).also {
                it.hour = selectedHour; it.minute = selectedMinute
                it.intervalDays = selectedDays
                it.startDateMs = selectedStartDate?.timeInMillis ?: 0L
                it.ringtoneUri = selectedRingtoneUri; it.label = label
                it.note = selectedNote; it.vibrate = selectedVibrate
            }
        } else {
            AlarmScheduler.AlarmData(id = AlarmScheduler.newId()).also {
                it.hour = selectedHour; it.minute = selectedMinute
                it.intervalDays = selectedDays
                it.startDateMs = selectedStartDate?.timeInMillis ?: 0L
                it.ringtoneUri = selectedRingtoneUri; it.label = label
                it.note = selectedNote; it.vibrate = selectedVibrate
            }
        }

        AlarmScheduler.schedule(this, data)

        val toastText = buildString {
            append(if (editAlarmId != null) "已更新" else "已添加")
            append(" ${formatStartDate(selectedStartDate)}")
            append(if (selectedDays == 0) "当天一次提醒" else "起每${selectedDays}天提醒")
        }
        Toast.makeText(this, toastText, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun refreshNextAlarmSubtitle() {
        val header = findViewById<TextView>(R.id.tvNextAlarmHeader)
        val now = Calendar.getInstance()
        val anchor = (selectedStartDate?.clone() as? Calendar) ?: Calendar.getInstance()
        val target = (anchor.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, selectedHour)
            set(Calendar.MINUTE, selectedMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, selectedDays.coerceIn(0, 365))
        }

        val stepDays = selectedDays.coerceIn(0, 365).let { if (it == 0) 1 else it }
        while (!target.after(now)) target.add(Calendar.DAY_OF_YEAR, stepDays)

        val totalMinutes = ((target.timeInMillis - now.timeInMillis) / 60_000L).coerceAtLeast(0L)
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val minutes = totalMinutes % 60
        header.text = buildString {
            if (days > 0) append("${days}天")
            if (hours > 0) append("${hours}小时")
            if (minutes > 0 || isEmpty()) append("${minutes}分钟")
            append("后响铃")
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun getFileNameFromUri(uri: Uri): String = try {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else null
        } ?: uri.lastPathSegment
    } catch (_: Exception) { null } ?: "本地音频"

    override fun onDestroy() {
        super.onDestroy()
        stopPreview()
    }

    override fun onPause() {
        super.onPause()
        if (isPlayingPreview) stopPreview()
    }
}
