package com.cyclealarm.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class ReliabilityCheckActivity : AppCompatActivity() {

    companion object {
        private const val REQ_POST_NOTIFICATIONS = 7301
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reliability_check)

        findViewById<View>(R.id.btnBackReliability).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnNotificationAction).setOnClickListener { handleNotificationAction() }
        findViewById<Button>(R.id.btnOverlayAction).setOnClickListener { openOverlaySettings() }
        findViewById<Button>(R.id.btnBackgroundPopupAction).setOnClickListener { openBackgroundPopupSettings() }
        findViewById<Button>(R.id.btnAutoStartAction).setOnClickListener { openAutoStartSettings() }
        findViewById<Button>(R.id.btnBatteryAction).setOnClickListener { handleBatteryAction() }
        findViewById<Button>(R.id.btnFullScreenAction).setOnClickListener { openFullScreenSettings() }
        findViewById<Button>(R.id.btnTestAlarm).setOnClickListener { scheduleTestAlarm() }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_POST_NOTIFICATIONS) refreshStatus()
    }

    private fun refreshStatus() {
        val notificationOk = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val overlayOk = isOverlayAllowed()
        val batteryOk = isIgnoringBatteryOptimizations()
        val needsVendorLockScreenCheck = needsVendorLockScreenCheck()

        setCheckText(
            R.id.tvNotificationStatus,
            "通知提醒",
            notificationOk,
            "响铃时显示备注。",
            "用于显示响铃和备注。"
        )
        setPermissionButtonState(R.id.btnNotificationAction, notificationOk)

        setCheckText(
            R.id.tvOverlayStatus,
            "悬浮窗",
            overlayOk,
            "必要权限，若不开启闹钟无法响铃。",
            "必要权限，若不开启闹钟无法响铃。"
        )
        setPermissionButtonState(R.id.btnOverlayAction, overlayOk)

        setPermissionText(
            R.id.tvBackgroundPopupStatus,
            "后台弹出页面",
            "保证闹钟在后台能正常响铃。"
        )
        setPermissionButtonState(R.id.btnBackgroundPopupAction, false)

        setPermissionText(
            R.id.tvAutoStartStatus,
            "自启动",
            "重启手机后闹钟正常响铃。"
        )
        setPermissionButtonState(R.id.btnAutoStartAction, false)

        val batteryText = "防止锁屏后APP被系统关闭，请将后台保护设为“无限制”，保证闹钟准时响铃。"
        setCheckText(R.id.tvBatteryStatus, "后台保护", batteryOk, batteryText, batteryText)
        setPermissionButtonState(R.id.btnBatteryAction, batteryOk)

        setPermissionText(
            R.id.tvFullScreenStatus,
            "锁屏显示",
            "允许锁屏时显示提醒。"
        )
        findViewById<Button>(R.id.btnFullScreenAction).text = "去设置"
        setPermissionButtonState(
            R.id.btnFullScreenAction,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && isFullScreenAllowed()
        )

        if (needsVendorLockScreenCheck) {
            setPermissionText(
                R.id.tvTestAlarmStatus,
                "测试响铃",
                "确认按时响、会振动、锁屏能看到备注。"
            )
        } else {
            setPermissionText(
                R.id.tvTestAlarmStatus,
                "测试响铃",
                "验证铃声、振动和锁屏显示。"
            )
        }
    }

    private fun setCheckText(
        viewId: Int,
        title: String,
        ok: Boolean,
        okText: String,
        failText: String
    ) {
        val detail = if (ok) okText else failText
        setPermissionText(viewId, title, detail)
    }

    private fun setPermissionText(viewId: Int, title: String, detail: String) {
        val text = "$title\n$detail"
        val styled = SpannableString(text)
        val split = title.length
        styled.setSpan(
            ForegroundColorSpan(Color.rgb(26, 26, 26)),
            0,
            split,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        styled.setSpan(
            AbsoluteSizeSpan(14, true),
            0,
            split,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        styled.setSpan(
            ForegroundColorSpan(Color.rgb(136, 136, 136)),
            split + 1,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        styled.setSpan(
            AbsoluteSizeSpan(12, true),
            split + 1,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        findViewById<TextView>(viewId).text = styled
    }

    private fun setPermissionButtonState(buttonId: Int, ok: Boolean) {
        val button = findViewById<Button>(buttonId)
        button.text = "去设置"
        button.backgroundTintList = ColorStateList.valueOf(
            if (ok) Color.rgb(210, 210, 210) else Color.rgb(61, 139, 255)
        )
    }

    private fun isExactAlarmAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmMgr = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmMgr.canScheduleExactAlarms()
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerMgr = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerMgr.isIgnoringBatteryOptimizations(packageName)
    }

    private fun isAlarmVolumeAudible(): Boolean {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.getStreamVolume(AudioManager.STREAM_ALARM) > 0
    }

    private fun isOverlayAllowed(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    private fun isFullScreenAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val nm = getSystemService(NotificationManager::class.java)
        return nm.canUseFullScreenIntent()
    }

    private fun needsVendorLockScreenCheck(): Boolean {
        val maker = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
        return maker.contains("xiaomi") ||
            maker.contains("redmi") ||
            maker.contains("poco") ||
            maker.contains("oppo") ||
            maker.contains("vivo") ||
            maker.contains("realme") ||
            maker.contains("huawei") ||
            maker.contains("honor") ||
            maker.contains("oneplus")
    }

    private fun handleNotificationAction() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_POST_NOTIFICATIONS)
        } else {
            openNotificationSettings()
        }
    }

    private fun handleOneTapSetup() {
        when {
            !NotificationManagerCompat.from(this).areNotificationsEnabled() -> handleNotificationAction()
            !isOverlayAllowed() -> openOverlaySettings()
            !isExactAlarmAllowed() -> openExactAlarmSettings()
            !isAlarmVolumeAudible() -> openSoundSettings()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isIgnoringBatteryOptimizations() -> {
                handleBatteryAction()
            }
            else -> scheduleTestAlarm()
        }
    }

    private fun scheduleTestAlarm() {
        val triggerAt = System.currentTimeMillis() + 10_000L
        AlarmScheduler.scheduleReliabilityTest(this, triggerAt)
        Toast.makeText(this, "已安排 10 秒后测试响铃", Toast.LENGTH_LONG).show()
    }

    private fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            appDetailsIntent()
        }
        startSafely(intent)
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startSafely(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        } else {
            Toast.makeText(this, "当前系统不需要单独开启精确闹钟", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSoundSettings() {
        startSafely(Intent(Settings.ACTION_SOUND_SETTINGS))
    }

    private fun openOverlaySettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        } else {
            appDetailsIntent()
        }
        startSafely(intent)
    }

    private fun openBackgroundPopupSettings() {
        if (openXiaomiPermissionEditor()) return
        if (openOppoPermissionSettings()) return
        if (openVivoPermissionSettings()) return
        if (openHuaweiPermissionSettings()) return
        startSafely(appDetailsIntent())
    }

    private fun openAutoStartSettings() {
        if (openXiaomiAutoStartSettings()) return
        if (openOppoAutoStartSettings()) return
        if (openVivoAutoStartSettings()) return
        if (openHuaweiAutoStartSettings()) return
        startSafely(appDetailsIntent())
    }

    private fun handleBatteryAction() {
        // Try the system dialog first — much higher conversion than opening settings page
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:$packageName"))
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {
                // Fall through to settings page
            }
        }
        openBatterySettings()
    }

    private fun openBatterySettings() {
        if (openXiaomiBatterySettings()) return
        if (openOppoBatterySettings()) return
        if (openVivoBatterySettings()) return
        if (openHuaweiBatterySettings()) return

        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } else {
            appDetailsIntent()
        }
        startSafely(intent)
    }

    private fun openXiaomiBatterySettings(): Boolean {
        val appLabel = getString(R.string.app_name)
        val directIntent = Intent().apply {
            component = ComponentName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
            )
            putExtra("package_name", packageName)
            putExtra("package_label", appLabel)
        }
        if (startIfAvailable(directIntent)) return true

        val listIntent = Intent().apply {
            component = ComponentName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity"
            )
        }
        return startIfAvailable(listIntent)
    }

    private fun openXiaomiPermissionEditor(): Boolean {
        val directIntent = Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity"
            )
            putExtra("extra_pkgname", packageName)
            putExtra("package_name", packageName)
        }
        if (startIfAvailable(directIntent)) return true

        val appPermissionIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity"
            )
            putExtra("extra_pkgname", packageName)
        }
        return startIfAvailable(appPermissionIntent)
    }

    private fun openXiaomiAutoStartSettings(): Boolean {
        val directIntent = Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        }
        return startIfAvailable(directIntent)
    }

    // ── OPPO / Realme / OnePlus ──

    private fun openOppoAutoStartSettings(): Boolean {
        val intents = listOf(
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startupapp.StartupAppListActivity"
            ),
            ComponentName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity"
            )
        )
        for (cn in intents) {
            if (startIfAvailable(Intent().setComponent(cn))) return true
        }
        return false
    }

    private fun openOppoBatterySettings(): Boolean {
        val intents = listOf(
            Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.powersaver.PowerSaverAdapterActivity"
                )
            },
            Intent().apply {
                component = ComponentName(
                    "com.oppo.safe",
                    "com.oppo.safe.powersaver.PowerSaverAdapterActivity"
                )
            },
            Intent().apply {
                action = "com.coloros.safecenter.action.POWER_SAVER"
            }
        )
        for (intent in intents) {
            if (startIfAvailable(intent)) return true
        }
        return false
    }

    private fun openOppoPermissionSettings(): Boolean {
        val intents = listOf(
            Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.floatwindow.FloatWindowListActivity"
                )
            },
            Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.PermissionAppManageActivity"
                )
            }
        )
        for (intent in intents) {
            if (startIfAvailable(intent)) return true
        }
        return false
    }

    // ── vivo / iQOO ──

    private fun openVivoAutoStartSettings(): Boolean {
        val intents = listOf(
            Intent().apply {
                component = ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            },
            Intent().apply {
                component = ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
                )
            },
            Intent().apply {
                component = ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.PurviewTabActivity"
                )
            }
        )
        for (intent in intents) {
            if (startIfAvailable(intent)) return true
        }
        return false
    }

    private fun openVivoBatterySettings(): Boolean {
        val intents = listOf(
            Intent().apply {
                component = ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgPowerManagerActivity"
                )
            },
            Intent().apply {
                component = ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.BgPowerManagerActivity"
                )
            }
        )
        for (intent in intents) {
            if (startIfAvailable(intent)) return true
        }
        return false
    }

    private fun openVivoPermissionSettings(): Boolean {
        val intent = Intent().apply {
            component = ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity"
            )
            putExtra("packagename", packageName)
        }
        return startIfAvailable(intent)
    }

    // ── 华为 / Honor ──

    private fun openHuaweiAutoStartSettings(): Boolean {
        val intents = listOf(
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            },
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
            },
            Intent().apply {
                component = ComponentName(
                    "com.honor.systemmanager",
                    "com.honor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            }
        )
        for (intent in intents) {
            if (startIfAvailable(intent)) return true
        }
        return false
    }

    private fun openHuaweiBatterySettings(): Boolean {
        val intents = listOf(
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.power.ui.HwPowerManagerActivity"
                )
            },
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
            }
        )
        for (intent in intents) {
            if (startIfAvailable(intent)) return true
        }
        return false
    }

    private fun openHuaweiPermissionSettings(): Boolean {
        val intents = listOf(
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.permission.ui.PermissionSettingActivity"
                )
            },
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
                )
            }
        )
        for (intent in intents) {
            if (startIfAvailable(intent)) return true
        }
        return false
    }

    private fun openFullScreenSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT")
                .setData(Uri.parse("package:$packageName"))
        } else {
            appDetailsIntent()
        }
        startSafely(intent)
    }

    private fun appDetailsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:$packageName"))
    }

    private fun startSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(appDetailsIntent())
        }
    }

    private fun startIfAvailable(intent: Intent): Boolean {
        return try {
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
