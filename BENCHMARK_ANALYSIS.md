# 开源闹钟 App 对比分析

> 生成日期: 2026-07-06 | 下次 review: 每次重大架构改动前

## 目的

本文档记录 Cycle Alarm 与 GitHub 上最具代表性的开源闹钟 App 的对比分析，用于：
- 识别架构差距和改进机会
- 为技术选型提供参考
- 避免重复造轮子或重复踩坑

新会话中，如果涉及闹钟调度、架构设计、功能规划，应先读本文档了解竞品参考。

---

## 一、选出的 5 个代表性项目

| 项目 | Stars | 语言 | 许可证 | 定位 |
|------|-------|------|--------|------|
| [Fossify Clock](https://github.com/FossifyOrg/Clock) | 441★ | Kotlin | GPL-3.0 | 全天候时钟套件（闹钟+计时器+秒表+桌面小部件），SimpleMobileTools 继任者 |
| [BlackyHawky Clock](https://github.com/BlackyHawky/Clock) | 活跃 | Kotlin | FOSS | AOSP DeskClock 增强版，翻/摇关闭，隐私优先，F-Droid 可获取 |
| [Chronos](https://github.com/meenbeese/Chronos) | 活跃 | Kotlin | Apache 2.0 | 极简美学闹钟，Jetpack Compose + Material 3，自定义铃声/背景/主题 |
| [Chrono](https://github.com/vicolo-dev/chrono) | 活跃 | Kotlin | FOSS | 功能最强，闹钟任务（数学题/打字关闭），Material You |
| [Shake Alarm Clock](https://github.com/WrichikBasu/ShakeAlarmClock) | 成熟 | Java/Kotlin | AGPL-3.0 | 摇一摇关闭，Room 数据库，UI-Service 严格解耦，Google Play 可获取 |

另有一个必读参考：**AOSP DeskClock**（Google 官方闹钟实现，`platform/packages/apps/DeskClock`），所有严肃闹钟 App 的教科书。

---

## 二、架构维度对比

### 2.1 闹钟调度 API（最关键的差异）🔴

| 项目 | scheduling API | 覆盖范围 |
|------|---------------|---------|
| AOSP DeskClock | `setAlarmClock()` | API 21+ |
| BlackyHawky Clock | `setAlarmClock()` | API 21+ |
| Fossify Clock | `setAlarmClock()` | API 21+ |
| Chronos | `setExactAndAllowWhileIdle()` | API 23+ |
| **Cycle Alarm（当前）** | `setAlarmClock()` 仅 API 31+，否则 `setExactAndAllowWhileIdle()` | ⚠️ |

> **🔴 在修 Bug：`setAlarmClock()` 从 API 21 (Lollipop) 就可以用了，但 `AlarmScheduler.kt:211` 把它限制在 API 31+（Android 12 以上）。**

对 minSdk 21 的用户群意味着 90% 以上的设备在用次等的 `setExactAndAllowWhileIdle()`，Doze 模式下可能延迟长达 15 分钟。

**修复**：把 `Build.VERSION_CODES.S` 改成 `Build.VERSION_CODES.LOLLIPOP`。

### `setAlarmClock()` vs `setExactAndAllowWhileIdle()` 深度对比

| 维度 | `setAlarmClock()` | `setExactAndAllowWhileIdle()` |
|------|-------------------|-------------------------------|
| Doze 行为 | **退出 Doze 后再触发** | Doze 中延迟可达 15 分钟 |
| 系统待遇 | 临时电源豁免（约 10 秒） | 无特殊待遇 |
| 状态栏 | 显示闹钟图标 | 无 |
| 可靠性 | ⭐⭐⭐⭐⭐ | ⭐⭐ |
| 电池影响 | 较高（完全唤醒设备） | 较低 |
| 适用场景 | 用户明确期望精确触发的闹钟 | 可容忍延迟的后台任务 |
| 引入版本 | API 21 | API 23 |

**结论**：对于闹钟 App，`setAlarmClock()` 是唯一正确选择。

### 2.2 调度链路可靠性

这是 AOSP 历史上最著名的一个闹钟 bug（**AOSP Bug 25846551**）：

```
❌ 旧架构（Cycle Alarm 当前实现）:
   AlarmManager → BroadcastReceiver.onReceive() → Service.onStartCommand()
   
   问题：lowmemorykiller 可能在 onReceive() 结束和 onStartCommand() 开始之间杀掉进程。
   在这个窗口期内，闹钟已经触发但 Service 还没启动，闹钟静默丢失。

✅ AOSP 修复后:
   AlarmManager → PendingIntent 直接发给 Service.onStartCommand()
   
   好处：单个 Intent 处理全过程，没有中间断裂点。
```

Cycle Alarm 的 `AlarmReceiver` → `AlarmService.start()` 链路使用了旧架构。大多数开源项目（Chronos、ShakeAlarmClock）也保留了 `BroadcastReceiver` 中间层，所以这不是紧急问题。

**缓解措施（低风险）**：在 `AlarmReceiver.onReceive()` 中调用 `goAsync()` 延长 BroadcastReceiver 生命周期，防止系统在 Service 启动前杀掉进程。

### 2.3 数据持久化

| 项目 | 方案 | 优点 |
|------|------|------|
| ShakeAlarmClock | Room (SQLite) | 类型安全、迁移支持、DAO 查询 |
| Chronos | Room (SQLite) | 同上，支持 import/export |
| Fossify Clock | ContentProvider + SQLite | 多进程安全 |
| **Cycle Alarm** | **JSON 文件** | 简单直观，零依赖，当前规模完全够用 |

JSON 文件方案对当前规模（少量闹钟）完全够用。如果未来需要列表查询排序、导入导出、跨版本迁移，Room 是更好的选择。**不是紧急问题。**

### 2.4 开机/时间变化重排

| 项目 | 监听的广播 |
|------|-----------|
| Chronos | `BOOT_COMPLETED` |
| Fossify Clock | `BOOT_COMPLETED` + `TIME_SET` + `TIMEZONE_CHANGED` |
| **Cycle Alarm** 🏆 | `BOOT_COMPLETED` + `QUICKBOOT_POWERON` + **`MIUI_QUICKBOOT_POWERON`** + `TIME_CHANGED` + `TIMEZONE_CHANGED` + `DATE_CHANGED` + `MY_PACKAGE_REPLACED` |

**Cycle Alarm 在这一项是最全面的。** MIUI 快启广播和 `MY_PACKAGE_REPLACED`（App 更新后重排）是差异化优势，充分考虑了国产 ROM 的特殊行为。

---

## 三、功能维度对比

| 功能 | Cycle Alarm | Fossify | BlackyHawky | Chronos | Chrono |
|------|:-----------:|:-------:|:-----------:|:-------:|:------:|
| 基础闹钟 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 周期性闹钟 (N天/时分/月) | ✅ 🏆 | ❌ | ❌ | ❌ | ❌ |
| 自定义铃声 | ✅ 回退链 | ✅ | ✅ | ✅ 内置+自定义 | ✅ |
| 振动 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 贪睡 (Snooze) | ✅ 硬编码 5 分钟 | ✅ 可配置 | ✅ 可配置 | ✅ 可配置 | ✅ 可配置 |
| 渐进音量 (Rising Volume) | ❌ | ✅ | ✅ | ✅ | ✅ |
| 闹钟超时自动关闭 | ❌ (wakelock 5min) | ✅ | ✅ (10min) | ✅ | ✅ |
| 防误触关闭 | ❌ | ❌ | ✅ 翻/摇 | ❌ | ✅ 数学题/打字 |
| 锁屏显示 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 桌面小部件 | ❌ | ✅ | ✅ | ❌ | ❌ |
| 导入/导出 | ❌ | ❌ | ❌ | ✅ | ❌ |
| 暗色/AMOLED 主题 | ❌ | ✅ | ✅ | ✅ | ✅ |
| 自定义背景 | ❌ | ❌ | ❌ | ✅ | ❌ |
| Tablet/横屏布局 | ❌ | ❌ | ❌ | ✅ | ❌ |
| 关机闹钟 | ❌ | ❌ | ✅ (骁龙) | ❌ | ❌ |

### Cycle Alarm 的独特优势 🏆

五个参考项目**没有一个**支持"每 N 天/时分/月"周期闹钟。这是 Cycle Alarm 的核心差异化功能，也是它存在的原因。`AlarmTimeCalculator` 和 `UiAlarmSchedulePlanner` 的周期计算能力在开源竞品中独一无二。

---

## 四、Cycle Alarm 已经做得很好的地方

| 强项 | 说明 |
|------|------|
| 🏆 **周期闹钟算法** | `AlarmTimeCalculator` / `UiAlarmSchedulePlanner` — 五个参考项目都没有这个能力 |
| 🏆 **国产 ROM 适配** | MIUI 快启广播、`MY_PACKAGE_REPLACED` 重排、三个时间变化广播全面覆盖 |
| 🏆 **铃声回退链** | 用户铃声 → 闹钟默认 → 通知默认 → 来电默认，四层保护（参考了 AOSP AlarmKlaxon 的 fallback 模式） |
| 🏆 **锁屏全屏 Activity** | `FLAG_SHOW_WHEN_LOCKED` + `FLAG_TURN_SCREEN_ON` + `requestDismissKeyguard` 组合拳 |
| 🏆 **双 WakeLock** | `PARTIAL_WAKE_LOCK` 保 CPU + `SCREEN_BRIGHT_WAKE_LOCK` 亮屏 |
| 🏆 **FullScreenIntent 通知** | 在某些 ROM 上比直接启动 Activity 更可靠，是 AOSP DeskClock 的标准做法 |
| 🏆 **BroadcastReceiver 通知** | `AlarmReceiver` 在启动 Service 前先发全屏通知，比直接启动 Activity 在严格 ROM 上更可靠 |

---

## 五、改进建议（按优先级排列）

### 🔴 P0 — 立即修复

**1. `setAlarmClock` 版本判断错误**
- 文件: `AlarmScheduler.kt:211`
- 修复: `Build.VERSION_CODES.S` → `Build.VERSION_CODES.LOLLIPOP`（或直接移除判断，因为 minSdk 21）
- 影响: 所有 Android 5.0–11 用户的闹钟准时性将大幅提升

**2. Snooze 硬编码 5 分钟**
- 文件: `AlarmRingingActivity.kt:43`
- 修复: 参考 ShakeAlarmClock，每个闹钟独立存储 snooze 时长
- 最低方案: 改为可配置的 1–30 分钟

### 🟡 P1 — 近期改进

**3. 渐进音量（Rising Volume）**
- 参考: AOSP AlarmKlaxon
- 实现: `AlarmService` 中用 Handler + Runnable 每 3 秒调高 `MediaPlayer.setVolume()`
- 用户体验提升显著，实现成本低

**4. 闹钟自动超时关闭**
- 参考: AOSP 10 分钟超时
- 实现: Handler.postDelayed() 在 startAlarm 时设置，到期自动停止铃声但保留通知
- 既省电又防止响一整天

**5. 防误触关闭**
- 参考: Chrono（数学题/打字）、ShakeAlarmClock（摇一摇）、BlackyHawky（翻转手机）
- 最低实现: 关闭按钮需要长按 2 秒

### 🟢 P2 — 长期规划

**6. Room 数据库** — 当闹钟数量增长时替换 JSON 文件
**7. 导入/导出闹钟** — 用户换手机时不丢失设置
**8. 暗色主题** — 配合蓝色 UI 改造
**9. 自定义 Snooze 时长** — 每个闹钟独立配置
**10. 桌面小部件** — 显示下一个闹钟倒计时

---

## 六、参考项目的架构设计启示

### 6.1 AOSP DeskClock — 闹钟响铃的黄金标准

- **AlarmService** + **AlarmKlaxon** 分离：Service 管调度状态，Klaxon 管音频播放
- Klaxon 使用 `START_STICKY`：被 kill 后自动重启
- 独立的 `PhoneStateListener`：来电时自动静音闹钟
- `setAlarmClock()` + `AlarmClockInfo`：系统级闹钟图标
- 铃声播放失败时 fallback 到内置资源

### 6.2 ShakeAlarmClock — UI-Service 解耦的典范

- Room 数据库存储每个闹钟的所有设置
- Service 几乎不依赖 UI 层
- 每个闹钟独立管理自己的设置（音量、铃声、snooze），设置之间不互相污染

### 6.3 Chronos — 现代 Android 开发范本

- 纯 Jetpack Compose UI
- Material 3 / Material You 动态配色
- Catppuccin 主题系统（亮色/暗色/AMOLED）
- 内置默认铃声资源（无需网络）
- 请求 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 确保后台可靠性

### 6.4 BlackyHawky Clock — 硬件交互创新

- 翻转手机静音/贪睡
- 摇一摇关闭
- 音量键/电源键关闭
- 骁龙关机闹钟支持（设备相关）

---

## 七、与文档体系的关联

- 本文档是 Cycle Alarm 的**外部参考**，不是实现规范
- 修改闹钟调度链路时，先读 `TECH_PLAN.md` 和本文档
- 本文档的改进建议（P0/P1/P2）是有意为之的技术债，不应在无关改动中顺手实现
- 每完成一个改进建议，更新本文档对应条目并标注完成日期

---

## 八、下次调研方向

- 需要在真实国产 ROM（MIUI/ColorOS/OriginOS/HarmonyOS）上验证各竞品 App 的后台可靠性
- 需要对比各竞品的 Google Play / 应用商店审核通过情况
- 需要分析各竞品在 Android 14+ 上的适配策略（`SCHEDULE_EXACT_ALARM` 权限等）
