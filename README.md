# 挂号闹钟 (Hospital Alarm) for Android

一个参考小米自带时钟风格、简洁大方的 Android 闹钟应用。用于提醒每 N 天去医院挂号/开药。

## 功能

- 周期可设置 **1 ~ 365 天**
- 闹铃支持 **本地音乐 / 录音文件** 或系统默认闹铃
- 使用 Android `AlarmManager` + `BroadcastReceiver` 实现定时提醒
- 开机自动重新注册闹钟
- 通知栏高优先级提醒
- 小米手机 MIUI 兼容提示（自启动、省电策略）

## 项目结构

```
hospital-alarm/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/hospital/alarm/
│       │   ├── MainActivity.kt      # 主界面
│       │   ├── AlarmScheduler.kt    # 闹钟调度逻辑
│       │   ├── AlarmReceiver.kt     # 闹钟触发响应
│       │   └── BootReceiver.kt      # 开机重新注册闹钟
│       └── res/                     # 布局、样式、图标资源
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── local.properties                 # 需要替换为你的 Android SDK 路径
```

## 如何编译/安装

### 方式 1：用 Android Studio（推荐）

1. 安装 [Android Studio](https://developer.android.com/studio)
2. 打开本文件夹 `hospital-alarm`
3. 在 `local.properties` 中确认 `sdk.dir` 指向你的 Android SDK（Android Studio 通常会自动生成）
4. 点击 **Build → Build Bundle(s) / APK(s) → Build APK(s)**
5. 编译完成后在 `app/build/outputs/apk/debug/app-debug.apk` 找到 APK，发到小米手机安装

### 方式 2：命令行（需要配置好 Android SDK 和 Gradle）

```bash
# 在 Windows PowerShell 中
./gradlew assembleDebug
```

## 小米手机安装后设置

为了让闹钟在 MIUI 上稳定响起，请完成以下设置：

1. **自启动**
   - 设置 → 应用设置 → 应用管理 → 挂号闹钟 → 自启动 → 允许
2. **省电策略**
   - 设置 → 应用设置 → 应用管理 → 挂号闹钟 → 省电策略 → 无限制
3. **通知权限**
   - 首次打开时允许通知
4. **精确闹钟权限**
   - 首次打开时按提示前往系统设置，授权“闹钟和提醒”

## 使用说明

1. 打开 App，选择提醒时间（如 08:00）
2. 拖动滑块设置周期（默认 40 天）
3. 点击“选择”挑选本地音乐或录音作为闹铃
4. 打开开关，点击“设置闹钟”
5. 页面会显示“下次提醒”时间

## 已知限制

- 闹铃响铃时当前实现使用 `MediaPlayer` 在 `BroadcastReceiver` 中播放，可能会被系统限制较短时间。若需要长时间响铃直至用户手动关闭，建议后续升级为 ForegroundService + 全屏提醒 Activity。
- Android 12+ 需要用户手动授予“精确闹钟”权限。

## 作者

由 QClaw 生成，用户可自由修改和分发。
