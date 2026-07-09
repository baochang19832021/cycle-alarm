---
name: android-dev-patterns
description: Cycle Alarm 项目开发经验库。包含 5 条铁律、可复用代码模式、反模式速查表。修改 AlarmListActivity.kt 或 AlarmService.kt 前必读。
tools: Read, Edit, Write, Bash, Grep, Glob
model: inherit
---

# Android 闹钟 App 开发经验库

> 从 0 到 1 开发 Cycle Alarm 项目累计的 15+ 次踩坑经验。
> 每次新会话自动加载。修改 UI 或调度代码前先过一遍。

---

## 🔴 铁律：写代码前先想三秒

### 1. 对话框改值 → 必须更新行

```kotlin
// ❌ 错：对话框点了"完成"，值改了但页面不显示
showDialog(...) { newValue ->
    variable = newValue
    saveUiState()
    // 忘了更新行！
}

// ✅ 对：改值 + 更新行
showDialog(...) { newValue ->
    variable = newValue
    saveUiState()
    // 找到那行，更新文字
    (findViewWithTag(tag) as? LinearLayout)?.let { row ->
        (row.getChildAt(1) as? TextView)?.text = displayText
    }
}
```

### 2. 读编辑缓冲 → 不直接读 instance

```kotlin
// ❌ 错：instance.title 是旧值，编辑缓冲改了但没保存
val title = instance.title

// ✅ 对：走辅助方法，优先读缓冲
val title = instanceTitle(instanceId) ?: instance.title
```

### 3. 局部修改 → 不重建整个页面

```kotlin
// ❌ 错：改一行的事，整个页面重建 → 滚动跳顶
renderEdit(instanceId)

// ✅ 对：只更新那一行
(row.getChildAt(1) as? TextView)?.text = newValue
```

### 4. 排序列表 → ID 和数据绑一起

```kotlin
// ❌ 错：用 sorted 的 index 去 unsorted 取数据 → 错位
sortedList.forEachIndexed { idx, item ->
    originalList[idx]  // idx 不对应！
}

// ✅ 对：Pair 绑定
list.sortedBy{...}.map{ it.id to DemoAlarm(...) }
    .forEach { (id, item) -> alarmCard(id, item) }
```

### 5. 时间滚轮 → 两个轮子要同步

```kotlin
// ❌ 错：函数参数捕获 → 滚完小时滚分钟，小时重置
timeWheels(hour, minute) { nh, nm -> ... }
  hourWheel.onIndexChanged = { onChange(it, minute) }  // minute 是 val！

// ✅ 对：用 var
var h = hour; var m = minute
hourWheel.onIndexChanged = { h = it; onChange(h, m) }
minuteWheel.onIndexChanged = { m = it; onChange(h, m) }
```

---

## 🏗️ 可复用模式

### 可编辑行（对话框 + 自动更新）

```kotlin
// 一行代码 = 创建行 + 弹对话框 + 改值 + 更新文字
column.addView(medicineDetailRow("药品名称", value, "tag", "提示") { newVal ->
    variable = newVal; saveUiState()
})
```

### 行内更新（不用 tag 的话用 rebuildRow）

```kotlin
val row = LinearLayout(...)
fun rebuildRow() {
    row.removeAllViews()
    // 重建这一行的子View
}
// 对话框回调里调 rebuildRow()
```

---

## 🧪 修改后必须做的事

```bash
./pre-check.sh    # 1秒扫描反模式
./safe_build.sh   # 扫描 + 测试 + 构建
```

---

## 📋 常见反模式速查

| 模式 | 检测 | 修复 |
|------|------|------|
| renderEdit 在对话框回调 | pre-check #1 | 行内更新 |
| instance.xxx 直接读 | pre-check #6 | 辅助方法 |
| getChildAt as 硬转型 | pre-check #5 | as? + ?: return |
| Map["k"] ?: fallback | pre-check #3 | takeIf + isNotBlank |
| sortedBy + forEachIndexed | pre-check #9 | Pair 绑定 |

---

## 🎯 每次修 Bug 后

```
1. 修掉 Bug
2. 提取模式 → 加入 ERRORS.md
3. 能 grep 检测 → 加入 pre-check.sh
4. 跑一次 pre-check.sh 验证新规则有效
```

## 💀 绝不能做的事

- ❌ 对话框直写 alarmInstances（应写编辑缓冲）
- ❌ 局部修改调 renderEdit
- ❌ 创建 View 不存引用（GC 回收 → 回调不触发）
- ❌ TTS/MediaPlayer 用完不释放（资源泄漏 → 循环停止）

---

## 📡 资源管理铁律

### 6. TTS / MediaPlayer 用完必须释放

```kotlin
// ❌ 错：每次循环 new TextToSpeech()，旧的没关 → 3次后资源耗尽
tts = TextToSpeech(this) { ... }

// ✅ 对：先关旧的再建新的
try { tts?.shutdown() } catch (_: Exception) {}
tts = TextToSpeech(this) { ... }
```

### 7. MediaPlayer 必须存成员引用

```kotlin
// ❌ 错：局部变量 → GC 回收 → onCompletion 不触发 → 循环停止
MediaPlayer().apply { ...; start() }

// ✅ 对：成员变量保活
medicineVoicePlayer = MediaPlayer().apply { ...; start() }
// cleanup() 里: medicineVoicePlayer?.release()
```

---

## 🔗 配置流转链路

```
编辑页 → 对话框 → 编辑缓冲 → 保存 → config → 调度 → 卡片显示
                                                      ↘ 下次响铃
```
**每次修改 REGULAR/SPECIAL/MEDICINE 的逻辑，必须走完整条链路验证。**

常见断点：
- `buildConfigForType` 存了但 `schedulePlansForInstance` 没读
- `planRegular` 依赖 `selections` 但特殊规则只存在 `summary` 里（ERROR #10）
- 对话框改了 `summary` 但 `selections` 被清空（每天/每月/每年/法定工作日）

---

## 🎙️ 高质量语音生成

```bash
pip install edge-tts
edge-tts --voice zh-CN-XiaoxiaoNeural --text "该吃药了！×3" --write-media res/raw/med_morning.mp3
```

微软晓晓神经网络语音，免费，比系统 TTS 自然 100 倍。

---

## 📦 多时段数据模式

```kotlin
// 存储: 用 | 分隔
"08:00|20:00"  →  split("|")  →  ["08:00", "20:00"]
"早饭後|晚饭後" →  split("|")  →  ["早饭後", "晚饭後"]

// 调度: 每个时段一个独立 SchedulePlan
times.mapIndexed { idx, timeStr ->
    SchedulePlan(id = "alarm_ui_medicine_$idx", hour = h, minute = m, ...)
}
```

---

## 🐛 Kotlin 陷阱速查

| 陷阱 | 现象 | 修复 |
|------|------|------|
| `map["k"] ?: fallback` | 空字符串 "" 不被 ?: 拦截 | `?.takeIf{it.isNotBlank()} ?: fallback` |
| `val x = Foo()` → λ 里用 x | 自引用，编译错误 | `var x: Foo? = null; x = Foo()` |
| `fun f(a: Int, b: Int)` → λ `{ f(it, b) }` | a 被 b 的 λ 用旧值覆盖 | 用 `var` 存可变副本 |
