## ▶ 当前任务 (Current Task)

**全部 7 个 Phase 已完成 + Android 15+ 兼容 🎉**

下一步建议：提交代码、真机测试、合并到 main

### 2026-07-10：修复僵尸闹钟实例 Bug

- 🔴 **Bug**：编辑中途 `saveUiState()` 把未保存的临时 `AlarmInstance` 写入 SharedPreferences。用户取消后临时实例只从内存移除，磁盘上仍保留。下次进入 App 时僵尸实例复活，产生重复卡片。
- ✅ **修复**：
  - `AlarmInstance` 添加 `saved` 字段。新建时 `saved = false`，保存时 `saved = true`
  - `serializeInstancesToJson` 只持久化 `saved == true` 的实例
  - 取消时调用 `saveUiState()` 同步清理磁盘（纵深防御）
- ✅ **防御**：`pre-check.sh` 新增 ERROR #8 检测规则
- ✅ **验证**：`pre-check.sh` + `testDebugUnitTest` + `assembleDebug` 全部通过
- ⚠️ **注意**：修复前已产生的僵尸实例需手动删除一次（长按卡片 → 删除），之后不会再出现

| 项目 | 内容 |
|------|------|
| **计划文件** | `C:\Users\ALIENWARE\.claude\plans\proud-frolicking-patterson.md` |
| **双平台计划** | `C:\Users\ALIENWARE\.claude\plans\modular-leaping-toast.md` |
| **版本** | `versionCode = 15`, `versionName = "1.0.14"` |
| **SDK** | `compileSdk = 36`, `minSdk = 21`, `targetSdk = 35` |
| **分支** | `codex/experiment-custom-ringtone-page` |
| **鸿蒙分支** | `feature/harmonyos-next`（同仓分叉，异步开发） |
| **恢复关键词** | 在新会话中输入 **`继续`** 或 **`resume`** 即可自动恢复 |

### 🅱️ 鸿蒙 NEXT → `feature/harmonyos-next` 分支

| 项目 | 内容 |
|------|------|
| **鸿蒙项目路径** | `D:\firstcc\harmonyos\`（仅存在鸿蒙分支） |
| **当前进度** | Phase 1~3：项目结构 + domain 移植 + ReminderDemo + 19 测试 |
| **下一步** | 安装 DevEco Studio 5.x → 编译运行 → 真机验证 |

## Android 版本兼容

| Android 版本 | API | 支持 |
|-------------|:---:|:---:|
| 5.0 / 5.1 | 21-22 | ✅ setAlarmClock + setExact 降级 |
| 6.0 | 23 | ✅ setExactAndAllowWhileIdle |
| 7.0 - 11 | 24-30 | ✅ 全功能 |
| 12 - 13 | 31-33 | ✅ SCHEDULE_EXACT_ALARM |
| 14 | 34 | ✅ USE_EXACT_ALARM + FSI |
| 15 | 35 | ✅ 边到边 + targetSdk 35 |
| 16 Beta | 36 | ✅ compileSdk 36 |

## 可靠性改进计划 — 全部完成

| Phase | 内容 | 状态 |
|:---:|------|:---:|
| 0 | P0 Bug 修复（setAlarmClock + showIntent + 先存后调） | ✅ |
| 1 | 调度链路加固（goAsync + 备份闹钟 + stopWithTask 移除 + 调度后验证） | ✅ |
| 2 | 国产 ROM 生存（OPPO/vivo/华为 + 电池弹窗 + DnD 穿透 + 持久通知 + 首次引导） | ✅ |
| 3 | 周期/轮班功能（服药场景 + 长按防误触 / ⏸️ 法定工作日） | ✅ |
| 5 | 响铃体验加固（音频焦点 + 来电监听 + 渐进音量 + 自动超时 + 可配置 Snooze） | ✅ |
| 6 | 监控日志（USE_EXACT_ALARM + 触发后验证 + ReliabilityLogger + 30min 健康检查 + 用户提醒） | ✅ |
| 7 | 跨平台架构（:domain 模块拆分 / 纯 JVM / 零 Android 依赖 / 测试通过） | ✅ |

---

# Current Status

## Conversation Policy

- This project should not depend on long chat history.
- New conversations should start from `CONTEXT_INDEX.md`.
- Task handling should follow `TASK_ROUTING.md` and the matching `.agents/*.md` file.
- Before edits, read `RULES_BEFORE_EDIT.md`; after edits, check `RULES_AFTER_EDIT.md`.
- When a development stage finishes, update this file with current state, important decisions, changed files, verification, and next steps.

## Current Mainline

- Active project root: `D:\360MoveData\Users\ALIENWARE\Documents\制作手机周期闹钟`
- Current app package: `com.cyclealarm.app`
- Current main feature: `每 N 天闹钟`
- Do not continue active development in the archived `功能2：每N小时闹钟` thread.
- Treat the old feature thread as a source archive only.
- `每N小时闹钟` remains disabled or deferred unless explicitly reopened.

## Latest Implemented State

- Android native project exists and is the current mainline.
- Permission and ringing-reliability work from `功能2：每N小时闹钟` has been migrated into this project.
- Home page, every-N-days alarm edit flow, alarm list, ringing page, foreground ringing service, notification flow, reboot/time-change rescheduling, and reliability permission page are present.
- App icon from the old feature thread is referenced by the manifest.

## P0 PDCA Governance Completion

- Updated `AGENTS.md` to reflect the current Android engineering state.
- Added `CONTEXT_INDEX.md` as the new-conversation entrypoint.
- Added `TASK_ROUTING.md` for task routing.
- Filled `.agents/` with:
  - `product.md`
  - `android_dev.md`
  - `ui_layout.md`
  - `reliability.md`
  - `release_compliance.md`
- Added rule files:
  - `RULES_BEFORE_EDIT.md`
  - `RULES_AFTER_EDIT.md`
  - `RULES_RELEASE.md`

## P1 Domain And Test Completion

- Added domain calculator:
  - `app/src/main/java/com/cyclealarm/app/domain/AlarmTimeCalculator.kt`
- `AlarmScheduler.schedule()` now delegates next trigger calculation to the domain calculator.
- Added JVM unit tests:
  - `app/src/test/java/com/cyclealarm/app/domain/AlarmTimeCalculatorTest.kt`
- Added JUnit test dependency in `app/build.gradle.kts`.
- Covered N=0, N=1, N=40, elapsed target advancement, missing start date, invalid interval clamping, and second/millisecond reset.

## P2 Release And Compliance Completion

- Added release checklist:
  - `RELEASE_CHECKLIST.md`
- Added permission review:
  - `PERMISSION_REVIEW.md`
- Added usage and development guide:
  - `USAGE_AND_DEVELOPMENT_GUIDE.md`
- Updated `TECH_PLAN.md` with the current domain-layer implementation status.
- Updated `STORE_READY.md` with current permission review risks and external confirmations.

## Verification

- Current stage verification passed:
  - `testDebugUnitTest`: `BUILD SUCCESSFUL`
  - `assembleDebug`: `BUILD SUCCESSFUL`

## Pixso New UI Prototype APK - 2026-07-02

- Pixso-generated new pages are now the UI direction for the app. The old page layout is no longer the visual standard.
- Replaced the launcher experience in `AlarmListActivity.kt` with a new four-tab prototype:
  - `闹钟`
  - `功能`
  - `日历`
  - `我的`
- Added Pixso-style function entries:
  - `常规日期闹钟`
  - `特殊周期闹钟`
  - `轮班闹钟`
  - `农历日期闹钟`
- Added prototype edit flows for:
  - regular date alarm repeat selection
  - special cycle repeat selection
  - shift alarm cycle and per-day reminder editing
  - current-month shift calendar overview
- Reused the existing custom `WheelView` for time and number selection. Do not replace it with Pixso's rough picker output.
- Special-cycle weekly selection must support all weekdays: Monday through Sunday.
- The old reliability chain remains in the project and must not be removed:
  - `MainActivity`
  - `AlarmScheduler`
  - `AlarmReceiver`
  - `AlarmService`
  - `AlarmRingingActivity`
  - `BootReceiver`
  - `ReliabilityCheckActivity`

## 2026-07-04 Textbook Module Alignment

- Unified all alarm edit pages to use the textbook-style `铃声` entry and the shared ringtone picker/preview flow.
- Replaced all vibration-related edit controls with the textbook-style `响铃时振动` switch on the new UI pages.
- Kept scheduler, receiver, service, and reliability logic unchanged.

Current boundary:

- This is a first new-UI APK prototype. It is installable and lets the user experience the new layout and major interactions.
- Complex new product rules are not yet all wired to real alarm scheduling.
- The existing reliable every-N-days scheduling backend is preserved for later integration with the new UI.

Verification on 2026-07-02:

- `assembleDebug`: `BUILD SUCCESSFUL`
- Debug APK generated at:
  - `app/build/outputs/apk/debug/app-debug.apk`
- Follow-up fix on 2026-07-02:
  - The new UI APK could fail to open because `AppCompatActivity` was using a pure `Theme.Material3.Light.NoActionBar` parent. Theme was changed to `Theme.MaterialComponents.Light.NoActionBar`.
  - Added a self-made desktop icon resource `app/src/main/res/drawable/app_icon_cycle_alarm.xml`.
  - A later APK using `@drawable/app_icon_cycle_alarm` as launcher icon triggered a third-party app abnormal analysis dialog on the test phone.
  - For stability, Manifest launcher icon and round icon were restored to `@mipmap/app_icon_generated`.
  - `versionCode` was raised to `2`, `versionName` to `1.0.1`.
  - Rebuilt stable debug APK successfully at `2026-07-02 20:49:14`.
- `testDebugUnitTest` was attempted after the UI change but did not reach test execution. It failed during Gradle plugin resolution in a new daemon:
  - `Plugin [id: 'com.android.application', version: '8.2.0', apply: false] was not found`
  - This is an environment/dependency resolution failure, not a domain test failure.

Follow-up visual optimization on 2026-07-02:

- Based on four real-device screenshots from the user, optimized the new Pixso-style prototype in `AlarmListActivity.kt`.
- Replaced rough text-based bottom navigation icons with lightweight custom line icons drawn in code.
- Replaced function-entry Chinese-character icon blocks with matching line icons.
- Reduced alarm-card typography, switch scale, card padding, card radius, and vertical spacing to better match the Pixso reference proportions.
- Reduced calendar title and cell density, and changed the selected day from a large filled block to a lighter outline to avoid crowding date/reminder text.
- Tightened the `我的` page card and button proportions while keeping the necessary permission entry.
- Verification:
  - `assembleDebug`: `BUILD SUCCESSFUL`
  - Debug APK path remains `app/build/outputs/apk/debug/app-debug.apk`

Second visual tuning pass on 2026-07-02:

- Continued from `CURRENT_STATUS.md` without relying on previous chat history.
- Confirmed the active workspace path seen by Codex is still:
  - `D:\360MoveData\Users\ALIENWARE\Documents\制作手机周期闹钟`
- Further tightened the Pixso-style prototype:
  - reduced bottom navigation height and icon/text size
  - reduced alarm-card padding, title size, time size, and switch scale
  - reduced function-card padding, title size, description size, and arrow size
  - reduced function icon tile size
  - added light feature-color tint to the time wheel selection band
  - added weekend color handling in the shift calendar
  - removed prior unused-parameter Kotlin warnings
- Raised debug package version to:
  - `versionCode = 4`
  - `versionName = "1.0.3"`
- Verification:
  - `assembleDebug`: `BUILD SUCCESSFUL`
  - Debug APK generated at `app/build/outputs/apk/debug/app-debug.apk`
  - APK timestamp observed locally: `2026-07-02 21:34:45`

Prototype navigation pass on 2026-07-02:

- Added clickable prototype navigation in `AlarmListActivity.kt` without wiring the new UI to real alarm scheduling.
- Added interactions:
  - alarm-list cards open their corresponding edit pages
  - all four function cards open their corresponding creation/edit pages
  - lunar alarm now has a prototype edit page and lunar date picker bottom sheet
  - regular/special/shift/lunar settings rows now provide either a bottom sheet or a prototype status hint
  - shift day cards still open per-day reminder editing
  - calendar dates can be selected and update the detail card below
  - calendar `今天` action returns to the current date
  - `我的` permission card opens `ReliabilityCheckActivity`; usage/privacy card shows a prototype hint
- Explicit boundary:
  - This is still prototype navigation and UI interaction.
  - It does not yet persist new alarm data or connect the four new alarm types to `AlarmScheduler`.
  - Existing reliable every-N-days scheduling backend remains preserved for later integration.
- Raised debug package version to:
  - `versionCode = 5`
  - `versionName = "1.0.4"`
- Verification:
  - `assembleDebug`: `BUILD SUCCESSFUL`
  - Debug APK generated at `app/build/outputs/apk/debug/app-debug.apk`
  - APK timestamp observed locally: `2026-07-02 21:51:03`

Store-prep interaction pass on 2026-07-02:

- Continued polishing the Pixso-style new UI in `AlarmListActivity.kt` for store-prep UX.
- Completed visible interaction feedback for the current new UI layer:
  - alarm-list cards open edit pages
  - alarm-list switches persist enabled/disabled state
  - save actions enable the edited alarm card and return to the alarm list
  - regular date alarm supports title, date, repeat, ringtone, vibration, ring duration, and snooze edits
  - special cycle alarm supports start date, start time, repeat value/unit, and full Monday-Sunday weekly selection
  - shift alarm supports title, start date, cycle days, ringtone, vibration, per-day enable/rest state, multiple reminder times, and add-day flow
  - lunar alarm supports title, time, lunar date, repeat, ringtone, vibration, ring duration, and snooze edits
  - `我的` usage/privacy card now opens a real local help/privacy explanation dialog instead of prototype text
- Added local `SharedPreferences` persistence for the new UI's visible editable values:
  - time
  - titles
  - enabled states
  - dates
  - repeat summaries/selections
  - ringtone/vibration/ring duration/snooze
  - shift cycle and per-day summaries
  - lunar date and repeat
- Moved reliability permission prompting into the critical path:
  - app first start can suggest checking necessary permissions
  - enabling/saving an alarm can suggest checking notification, overlay, and battery optimization state
  - `我的` page still links to `ReliabilityCheckActivity`
- Removed user-facing prototype wording from the new UI layer.
- Raised debug package version to:
  - `versionCode = 6`
  - `versionName = "1.0.5"`
- Verification:
  - `assembleDebug`: `BUILD SUCCESSFUL`
  - `testDebugUnitTest`: `BUILD SUCCESSFUL` when run with external permission after sandbox plugin-resolution failure
  - Debug APK generated at `app/build/outputs/apk/debug/app-debug.apk`
  - APK timestamp observed locally: `2026-07-02 22:38:10`
- Current boundary:
  - New UI editable state now persists locally, but the four new alarm types are not yet fully connected to `AlarmScheduler`.
  - Existing reliability chain is preserved and must remain intact.
  - This APK is suitable for continued real-device UX review, not yet a final store release package.

Implementation note:

- Because this project path contains Chinese characters, Android unit-test worker classpaths can be mis-encoded by Gradle/AGP in this local environment. `app/build.gradle.kts` now copies debug main/test Kotlin classes to an ASCII path under the user's Gradle cache before `testDebugUnitTest`, then injects that path at test execution time. This keeps unit tests runnable without moving the project directory.

Pixso icon and launcher polish pass on 2026-07-02:

- Replaced the hand-drawn in-code new-UI icons in `AlarmListActivity.kt` with Pixso-derived VectorDrawable resources:
  - bottom navigation: alarm, function grid, calendar, user
  - function cards: regular date, special cycle, shift, lunar
- Removed the old `LineIconView` / `IconKind` drawing path from `AlarmListActivity.kt`.
- Regenerated launcher PNG assets under `mipmap-*`:
  - brighter Pixso-style green background
  - white clock face
  - 9 o'clock hand shape
- Kept Manifest launcher icon references on `@mipmap/app_icon_generated`.
- Improved option feedback consistency:
  - generic option bottom sheets now use the same visible circle/check selection style as regular repeat selection
  - ringtone selection rows now use real right-arrow rows instead of arrow text as row value
- Raised debug package version to:
  - `versionCode = 7`
  - `versionName = "1.0.6"`
- Verification:
  - `assembleDebug`: `BUILD SUCCESSFUL`
  - Debug APK generated at `app/build/outputs/apk/debug/app-debug.apk`
  - APK timestamp observed locally: `2026-07-02 23:30:08`
  - `testDebugUnitTest`: attempted in sandbox, but blocked at Android Gradle Plugin resolution in a new daemon; sandbox-external rerun was not executed because the system usage limit rejected the escalation request.
- Backup:
  - `backups/apk/cycle-alarm-pixso-v1.0.6-20260702-233024-debug.apk`

New UI scheduling bridge pass on 2026-07-03:

- Added a pure Kotlin scheduling planner:
  - `app/src/main/java/com/cyclealarm/app/domain/UiAlarmSchedulePlanner.kt`
- Added planner unit tests:
  - `app/src/test/java/com/cyclealarm/app/domain/UiAlarmSchedulePlannerTest.kt`
- Connected the Pixso-style new UI to the existing reliable `AlarmScheduler` for rules that can be represented safely by the current day-interval scheduler:
  - regular daily reminders
  - regular weekly weekday reminders
  - special every-N-days reminders
  - special every-N-weeks weekday reminders
  - shift-cycle day/time reminders
- The new UI now uses deterministic `alarm_ui_*` IDs when registering alarms, so turning a feature off only cancels that feature's UI-created schedules and does not delete unrelated alarms.
- Rules that cannot be honestly represented by the current day-interval scheduler are still saved locally but are not falsely registered as system alarms yet:
  - hour/minute interval special-cycle reminders
  - monthly/yearly special-cycle reminders
  - lunar reminders
  - full legal-workday calendar logic
- Raised debug package version to:
  - `versionCode = 8`
  - `versionName = "1.0.7"`
- Verification:
  - `testDebugUnitTest`: `BUILD SUCCESSFUL` when rerun outside the sandbox after sandbox plugin-resolution failure
  - `assembleDebug`: `BUILD SUCCESSFUL`
  - Debug APK generated at `app/build/outputs/apk/debug/app-debug.apk`
- Backup:
  - `backups/apk/cycle-alarm-pixso-v1.0.7-20260703-031401-debug.apk`

Special hour/minute scheduling pass on 2026-07-03:

- Extended the existing reliable scheduler instead of creating a parallel alarm path:
  - `AlarmScheduler.AlarmData` now supports `repeatMinutes`.
  - JSON save/load remains backward compatible; old alarms default to `repeatMinutes = 0`.
  - `AlarmScheduler.schedule()` uses minute-interval calculation when `repeatMinutes > 0`.
  - `AlarmScheduler.rescheduleNext()` advances minute-interval alarms after ringing.
- Added minute-interval domain calculation:
  - `AlarmTimeCalculator.calculateNextMinuteInterval(...)`
- Updated `UiAlarmSchedulePlanner`:
  - special `时分` rules now produce real schedule plans.
  - example: every 2 hours 30 minutes becomes `repeatMinutes = 150`.
- Updated tests:
  - minute interval future/advance behavior
  - special hour/minute UI schedule planning
- Verification:
  - `assembleDebug`: `BUILD SUCCESSFUL`
  - `testDebugUnitTest`: `BUILD SUCCESSFUL`
- Backup:
  - `backups/apk/cycle-alarm-pixso-v1.0.7-20260703-031935-debug.apk`

Month/year scheduling pass on 2026-07-03:

- Added month-based interval calculation in the domain layer:
  - `AlarmTimeCalculator.calculateNextMonthInterval(...)`
  - clamps overflow dates to the last day of shorter target months, for example January 31 -> February 28/29.
- Extended `AlarmScheduler.AlarmData` with `repeatMonths`.
- `AlarmScheduler.schedule()` now supports:
  - day interval alarms
  - minute interval alarms
  - month/year interval alarms
- `AlarmScheduler.rescheduleNext()` now advances month/year interval alarms after ringing.
- Updated `UiAlarmSchedulePlanner`:
  - regular `每月`
  - regular `每年`
  - special `每 N 月`
  - special `每 N 年`
- Raised debug package version to:
  - `versionCode = 9`
  - `versionName = "1.0.8"`
- Verification:
  - `testDebugUnitTest`: `BUILD SUCCESSFUL`
  - `assembleDebug`: `BUILD SUCCESSFUL`
- Backup:
  - `backups/apk/cycle-alarm-pixso-v1.0.8-20260703-032455-debug.apk`

Launcher icon replacement pass on 2026-07-03:

- Replaced the generated launcher PNG assets with the user-provided colorful alarm icon.
- Re-cropped the icon after user feedback so the alarm body is visually centered and the lower-right source watermark is excluded.
- Updated generated PNG launcher assets under:
  - `app/src/main/res/mipmap-mdpi/app_icon_generated.png`
  - `app/src/main/res/mipmap-hdpi/app_icon_generated.png`
  - `app/src/main/res/mipmap-xhdpi/app_icon_generated.png`
  - `app/src/main/res/mipmap-xxhdpi/app_icon_generated.png`
  - `app/src/main/res/mipmap-xxxhdpi/app_icon_generated.png`
- Kept Manifest launcher icon references on `@mipmap/app_icon_generated`.
- Raised debug package version to:
  - `versionCode = 10`
  - `versionName = "1.0.9"`
- Verification:
  - `assembleDebug`: `BUILD SUCCESSFUL`
- Backup:
  - `backups/apk/cycle-alarm-pixso-v1.0.9-20260703-150032-debug.apk`

Launcher icon padding pass on 2026-07-03:

- Adjusted the launcher icon again after real-device review:
  - kept the user-provided colorful alarm icon
  - preserved visual centering
  - reduced the alarm body size inside the launcher canvas
  - added more outer whitespace so the icon is less cramped on the phone desktop
- Raised debug package version to:
  - `versionCode = 11`
  - `versionName = "1.0.10"`
- Verification:
  - `assembleDebug`: `BUILD SUCCESSFUL`
- Backup:
  - `backups/apk/cycle-alarm-pixso-v1.0.10-20260703-151203-debug.apk`

Launcher icon blue-green background pass on 2026-07-03:

- Reworked the launcher icon from the user's white alarm reference image.
- Kept the white alarm silhouette style, replaced the colorful background with a calmer blue-green gradient.
- Removed the lower-right watermark/residual area before generating app launcher resources.
- Generated multi-density launcher PNG assets under `mipmap-*`.
- Kept edge padding in the launcher image so the icon does not feel cramped on the phone desktop.
- Raised debug package version to:
  - `versionCode = 12`
  - `versionName = "1.0.11"`
- Verification:
  - `assembleDebug`: `BUILD SUCCESSFUL`
- Backup:
  - `backups/apk/cycle-alarm-pixso-v1.0.11-20260703-192118-debug.apk`

Launcher icon Alipay-blue full-bleed pass on 2026-07-03:

- Reworked the launcher icon to a full-bleed Alipay-like blue background with a white alarm silhouette.
- Removed the previous white edge/corner canvas and internal gray circular highlight.
- Generated multi-density launcher PNG assets under `mipmap-*` with blue filling the entire icon canvas.
- Raised debug package version to:
  - `versionCode = 13`
  - `versionName = "1.0.12"`
- Verification:
  - `assembleDebug`: `BUILD SUCCESSFUL`
- Backup:
  - `backups/apk/cycle-alarm-pixso-v1.0.12-20260703-195709-debug.apk`

Build recovery note on 2026-07-03:

- Gradle plugin resolution initially failed after suspected cache cleanup or interrupted build state.
- Re-running `assembleDebug` with network access restored dependency/plugin resolution.
- The remaining build blocker was not Gradle cache loss; it was an invalid backup resource file:
  - `app/src/main/res/mipmap-xxhdpi/app_icon_generated.backup.png`
- Android resource files cannot contain an extra dot in the file name and backup files must not live under `res`.
- Moved the backup icon to:
  - `backups/icons/app_icon_generated_backup.png`
- Verification:
  - `assembleDebug`: `BUILD SUCCESSFUL`
  - Debug APK generated at `app/build/outputs/apk/debug/app-debug.apk`

Special repeat picker fix on 2026-07-04:

- Adjusted the special-cycle repeat picker:
  - day range remains limited to `1-365`
  - month range remains `1-48`, now wraps from 48 back to 1
  - year range remains `1-10`, now wraps from 10 back to 1
- Reworked the day picker to match the proven interval-picker structure:
  - three linked digit wheels
  - max day locked to 365 instead of letting the UI drift to 999
  - old saved `999` values are normalized on load and when opening the dialog
- Special-cycle default title changed from `复诊提醒` to `特殊周期提醒`
- Scope:
  - UI picker behavior only
  - no scheduler, Receiver, Service, Manifest, or permission logic changed
- Verification:
  - `assembleDebug`: `BUILD SUCCESSFUL`
  - `testDebugUnitTest`: `BUILD SUCCESSFUL`

Shift edit-page delete interaction update on 2026-07-04:

- Added long-press delete mode to the shift schedule's individual day cards in `AlarmListActivity.kt`.
- Long-pressing one day card now shows a right-top `X` on that single card only; tapping it deletes that day card immediately.
- Delete action on shift day cards does not use the main alarm-list undo Snackbar.
- The add-day flow and existing shift scheduling logic remain unchanged.
- Verification:
  - `assembleDebug`: `BUILD SUCCESSFUL`

Ringtone inline preview update on 2026-07-04:

- Added textbook-style inline preview control to every new-UI alarm edit page ringtone row.
- Tapping the ringtone row still opens the ringtone selector; tapping the right-side triangle previews the current ringtone before entering the selector page.
- Reused the existing `MediaPlayer` preview flow and existing ringtone picker/local-audio logic.
- Scheduler, Receiver, Service, Manifest, and permission logic were not changed.
- Verification:
  - `assembleDebug`: `BUILD SUCCESSFUL`

Shift first-day re-enable fix on 2026-07-04:

- Fixed a shift schedule bug where the first day could become `休` after editing and then could not be restored by turning the reminder switch back on.
- Root cause:
  - `applyShiftDaySummaryChange()` preserved all existing rest days when the first day drove the batch update, including the first day itself.
  - When day 1 was already `休`, changing it back to `07:30` was overwritten by the rest-day preservation rule.
- Fix:
  - Day 1 is now always written to the newly selected summary when it is the changed day.
  - Other rest days are still preserved.
- Added regression test:
  - `firstShiftDayCanBeReenabledAfterRest`
- Verification:
  - `testDebugUnitTest`: `BUILD SUCCESSFUL`
  - `assembleDebug`: `BUILD SUCCESSFUL`

Shift and feature time isolation fix on 2026-07-04:

- Fixed the new-UI alarm edit flow so each feature keeps its own saved time instead of sharing one global hour/minute pair.
- `AlarmListActivity.kt` now uses per-feature edit time state for:
  - 常规日期闹钟
  - 特殊周期闹钟
  - 农历日期闹钟
- The shift alarm card preview now reads from the shift day summaries instead of a hardcoded time, so the list card matches the edited shift schedule.
- `showTimeOnlyDialog()` no longer writes back to the shared global edit time as the primary source of truth.
- Added a pure domain helper:
  - `UiAlarmSchedulePlanner.shiftPreviewTimeText()`
- Added a regression test:
  - `shiftPreviewTimeTextUsesFirstWorkdayTime`
- Verification:
  - `testDebugUnitTest`: `BUILD SUCCESSFUL`
  - `assembleDebug`: `BUILD SUCCESSFUL`

Shift calendar sync pass on 2026-07-04:

- The calendar page now reads the saved shift schedule instead of using hardcoded day summaries.
- Month cells derive their summary from:
  - saved shift start date
  - saved cycle days
  - saved day summaries
- The selected-day detail card now shows the actual reminder times for that day, or `本日休息` if the day is a rest day.
- Added pure helpers in `UiAlarmSchedulePlanner` for calendar month/day shift summaries so the calendar can stay UI-only.
- Added regression tests:
  - `shiftCalendarSummaryRepeatsAccordingToSavedCycle`
  - `shiftCalendarReminderLinesReturnsAllTimesForWorkday`
- Verification:
  - `testDebugUnitTest`: `BUILD SUCCESSFUL`
  - `assembleDebug`: `BUILD SUCCESSFUL`

Previous verified state:

- Debug build passed after importing the generated icon: `BUILD SUCCESSFUL`.
- Debug build passed after the permission/reliability migration: `BUILD SUCCESSFUL`.

## External Pending Work

- Real-device testing remains required on Xiaomi/Redmi, OPPO/vivo, Huawei/Honor, and Android 14+.
- Lock-screen, background, reboot, system-time-change, timezone-change, and permission-denial paths need real-device verification.
- App-market backend requirements need current manual confirmation per market.
- Privacy policy URL and user agreement URL need official hosting.
- Formal signing certificate and developer account materials must be managed outside the repo.

## Next Development Direction

- Continue from this mainline.
- Before changing layouts, preserve:
  - every-N-days alarm creation/edit/list behavior
  - notification and exact alarm handling
  - full-screen ringing page
  - foreground ringing service
  - reboot/time-change rescheduling
  - reliability permission page and test alarm
- After any large UI or reliability change, run tests/build and update this file.

## 2026-07-06 开源竞品对比分析

- 完成了 Cycle Alarm 与 5 个 GitHub 代表性开源闹钟 App 的对比分析：
  - Fossify Clock、BlackyHawky Clock、Chronos (meenbeese)、Chrono (vicolo-dev)、ShakeAlarmClock
  - 另参考 AOSP DeskClock 架构
- 完整分析文档：`BENCHMARK_ANALYSIS.md`
- 记忆文件：`memory/benchmark-analysis.md`
- 已接入文档发现链路：`CLAUDE.md`、`CONTEXT_INDEX.md`、`TASK_ROUTING.md`、`MEMORY.md`

关键发现：
- 🔴 **P0 Bug**：`AlarmScheduler.kt:211` 中 `setAlarmClock()` 版本判断错误（`S` 应为 `LOLLIPOP`），导致 Android 5.0–11 用户无法使用最可靠的闹钟 API
- 🟡 **P1 功能 gap**：Snooze 硬编码 5 分钟、无渐进音量、无自动超时关闭、无防误触
- 🏆 **独特优势**：周期闹钟算法是五个竞品都没有的能力；国产 ROM 适配（MIUI 快启广播）最全面

下次涉及架构/调度/功能规划的会话会自动加载此分析。不要重复调研。

## 2026-07-06 Phase 0 完成 — P0 Bug 修复

- 修复了 `AlarmScheduler.kt` 中 4 个 P0 Bug：
  1. **`setAlarmClock()` 版本判断**：`Build.VERSION_CODES.S` → 移除版本判断，改用已有的 `scheduleAlarmClock()` 私有方法（内部调用 `setAlarmClock()`，API 21+ 可用，try-catch 包裹，失败回退到 `setExactAndAllowWhileIdle`）
  2. **`AlarmClockInfo` showIntent**：之前传入的是 broadcast `PendingIntent`，现在改为 Activity `PendingIntent`（通过 `alarmShowPendingIntent()` 创建）
  3. **`schedule()` 缺 try-catch**：现在委托给 `scheduleAlarmClock()`（已有 try-catch + 降级逻辑）
  4. **先调度后存储**：现在先 `saveAlarm()` 持久化，再 `scheduleAlarmClock()` 注册系统闹钟。如果存储失败，不会留下孤儿闹钟
- 改动范围：`AlarmScheduler.kt` 一个文件，`schedule()` 方法
- 验证：
  - `testDebugUnitTest`: `BUILD SUCCESSFUL`
  - `assembleDebug`: `BUILD SUCCESSFUL`
- 下一步：Phase 1 核心调度链路加固
