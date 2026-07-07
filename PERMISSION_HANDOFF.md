# Permission Handoff

## Source

- Source thread: `功能2：每N小时闹钟`
- Source worktree: `C:\Users\ALIENWARE\.codex\worktrees\aab8\制作手机周期闹钟`
- Migration target: current main project `D:\360MoveData\Users\ALIENWARE\Documents\制作手机周期闹钟`
- Scope: keep the permission and ringing-reliability work, not the every-N-hours alarm feature.

## Preserved Results

- Added a lightweight `必要权限` page for alarm reliability setup.
- Added a home-page entry that appears when core reliability permissions/settings may need attention.
- Preserved gradual permission guidance instead of asking for unrelated permissions at launch.
- Added a 10-second test alarm from the permission page.
- Improved alarm ringing delivery with a high-priority full-screen alarm notification before foreground service ringing.
- Added test-alarm marking so test alarms do not reschedule real alarms.
- Added stronger lock-screen wake behavior for ringing.

## Permission Items

- 通知提醒: runtime notification permission on Android 13+, or app notification settings otherwise.
- 悬浮窗: opens overlay permission settings. This is treated as important for strict ROM lock-screen/background display behavior.
- 后台弹出页面: opens MIUI permission editor when available; falls back to app details.
- 自启动: opens MIUI auto-start settings when available; falls back to app details.
- 后台保护: opens MIUI battery settings when available; falls back to battery optimization settings.
- 锁屏显示: opens Android 14+ full-screen intent settings when available; falls back to app details.
- 测试响铃: schedules a 10-second reliability test alarm.

## Migrated Files

- `app/src/main/java/com/cyclealarm/app/ReliabilityCheckActivity.kt`
- `app/src/main/res/layout/activity_reliability_check.xml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/cyclealarm/app/AlarmListActivity.kt`
- `app/src/main/res/layout/activity_alarm_list.xml`
- `app/src/main/java/com/cyclealarm/app/AlarmScheduler.kt`
- `app/src/main/java/com/cyclealarm/app/AlarmReceiver.kt`
- `app/src/main/java/com/cyclealarm/app/AlarmService.kt`
- `app/src/main/java/com/cyclealarm/app/AlarmRingingActivity.kt`
- `app/src/main/res/values/themes.xml`

## UI Copy

- Page title: `必要权限`
- Home warning text: `闹钟准时响起需要开启对应权限`
- Notice: `个别权限，APP无法自动识别是否开启。若您已开启，请放心使用。`
- 悬浮窗: `必要权限，若不开启闹钟无法响铃。`
- 后台弹出页面: `保证闹钟在后台能正常响铃。`
- 自启动: `重启手机后闹钟正常响铃。`
- 后台保护: `防止锁屏后APP被系统关闭，保证闹钟准时响铃。`
- 锁屏显示: `允许锁屏时显示提醒。`
- 测试响铃: `验证铃声、振动和锁屏显示。`

## White-Paper Baseline

Continue development from the current project, not from the archived feature thread. Treat this project as the clean mainline:

- Keep first release focused on `每N天闹钟`.
- Keep `每N小时闹钟` as a later feature unless explicitly reopened.
- Keep the reliability/permission module independent so future layout rewrites do not lose it.
- Before large layout changes, check that the IDs referenced by Kotlin still exist in XML.

## Remaining Risks

- Vendor settings pages can change by ROM version; MIUI direct intents must fall back safely.
- `SYSTEM_ALERT_WINDOW` is sensitive and needs store-review wording if kept.
- Some permissions cannot be reliably detected by Android APIs, so the page explains that completed vendor settings may still show a setup button.
- Real-device testing is still required on Xiaomi/Redmi, OPPO/vivo, Huawei/Honor, and Android 14+.
