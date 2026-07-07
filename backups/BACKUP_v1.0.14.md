# 阶段备份 — v1.0.14 可靠性全面加固版

> 备份时间：2026-07-06
> 分支：`codex/experiment-custom-ringtone-page`
> 版本：versionCode=15, versionName="1.0.14"
> SDK：compileSdk=36, minSdk=21, targetSdk=35

## 备份文件

| 文件 | 说明 |
|------|------|
| `backups/apk/cycle-alarm-v1.0.14-*.apk` | debug APK 安装包 |
| `stage_packages/latest/cycle-alarm-v1.0.14-latest-debug.apk` | 最新阶段包 |

## 本版本包含的全部改动

### 可靠性改进（7 Phase 完成）

| Phase | 内容 | 涉及文件 |
|:---:|------|------|
| 0 | P0 Bug：setAlarmClock 全版本 + showIntent 修正 + 先存后调 | AlarmScheduler.kt |
| 1 | goAsync + 备份闹钟 + canScheduleExactAlarms + stopWithTask 移除 + 调度后验证 | AlarmReceiver.kt, AlarmScheduler.kt, AndroidManifest.xml |
| 2 | OPPO/vivo/华为自启动 + 电池弹窗 + DnD 穿透 + 持久通知 + 首次引导 | ReliabilityCheckActivity.kt, AlarmReceiver.kt, AlarmService.kt, AlarmScheduler.kt, AlarmListActivity.kt, AndroidManifest.xml |
| 3 | 服药场景（药品名输入 + TTS 播报 + 服药确认）+ 长按防误触 | AlarmScheduler.kt, AlarmReceiver.kt, AlarmService.kt, AlarmRingingActivity.kt, AlarmListActivity.kt, UiAlarmSchedulePlanner.kt |
| 5 | 音频焦点 + 来电监听 + 渐进音量 + 10分钟自动超时 + 可配置 Snooze | AlarmService.kt, AlarmRingingActivity.kt |
| 6 | USE_EXACT_ALARM + 触发后验证 + ReliabilityLogger + 30分钟健康检查 + 用户提醒 | AlarmScheduler.kt, BootReceiver.kt, AlarmListActivity.kt, AndroidManifest.xml, ReliabilityLogger.kt(新) |
| 7 | :domain 模块拆分（纯 JVM / 零 Android 依赖 / 测试通过） | domain/(新), build.gradle.kts, settings.gradle.kts |

### Android 版本兼容

- compileSdk 34 → 36（Android 16 Beta）
- targetSdk 34 → 35（Android 15 行为基准）
- 边到边适配（WindowInsetsCompat）
- minSdk 保持 21

### 产品功能

- 服药场景：特殊周期闹钟支持药品名称 → TTS 语音播报 → 服药确认
- 长按防误触：关闭按钮需长按 2 秒

## 恢复方法

### 方法 1：直接使用 APK
安装 `stage_packages/latest/cycle-alarm-v1.0.14-latest-debug.apk` 即可。

### 方法 2：用 Git 恢复（如果在另一台电脑上）
```bash
git clone <repo-url>
git checkout codex/experiment-custom-ringtone-page
# 然后 ./gradlew assembleDebug 构建
```

### 方法 3：从当前工作目录继续
在 Claude Code 中打开此项目，输入「继续」或「resume」即可自动恢复到当前进度。
