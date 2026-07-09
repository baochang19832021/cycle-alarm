# 错误库 — Cycle Alarm

> 每次修完一个 bug，把根因和方法记录在此。新会话开始前必须读过。

---

## 1. render* 重建 → ScrollView 跳顶

**出现次数**: 4+
**严重度**: 🔴 高

**根因**: 局部修改（开关/勾选/对话框返回）调了 `renderEdit()` 或 `renderAlarmList()`，重建整个页面 → 新 ScrollView 滚动位置 = 0 → 跳回顶部。

**正确做法**:
- 编辑页：用 `rebuildRow()` 只重建单行
- 列表页：用 `update*()` 方法只更新被改的 View
- 只有页面级跳转（切 Tab / 保存后回列表）才调 `render*`

**详细**: [[avoid-full-render-on-small-changes]]

---

## 2. 对话框直改实例 → 取消失效

**出现次数**: 3
**严重度**: 🔴 高

**根因**: `showTitleDialog`、`showDateDialog`、`showMedicineNameDialog` 的"确定"按钮直写 `alarmInstances`。用户后续点取消无法回滚。

**正确做法**: 对话框只写编辑缓冲变量（`editTitle`、`editDateMs`、`editMedicineName`），只有点"保存"时才提交到实例。进入编辑页时清缓冲，取消/保存后清缓冲。

**详细**: [[dialog-must-use-edit-buffer]]

---

## 3. Kotlin `?:` 空字符串陷阱

**出现次数**: 2
**严重度**: 🟡 中

**根因**: `map["key"] ?: fallback` — 当 `map["key"]` 是 `""`（空字符串）时，`?:` 不触发。`""` 不是 `null`。

**症状**: `planRegular` 收到空的 `selections` → 返回空列表 → 显示"待计算"。

**正确做法**: `map["key"]?.takeIf { it.isNotBlank() } ?: fallback`

**详细**: [[kotlin-elvis-empty-string-trap]]

---

## 4. 重渲染覆盖编辑缓冲

**出现次数**: 2+
**严重度**: 🟡 中

**根因**: `renderEdit()` 每次调都从实例重新加载 config，覆盖掉用户刚刚在对话框里改的值。

**正确做法**: `if (!isSameInstance)` 才加载 config。同实例重渲染保留编辑缓冲。

**详细**: [[dont-reload-instance-on-rerender]]

---

## 5. 行内 View 更新时取错层级

**出现次数**: 1（刚修）
**严重度**: 🟡 中

**根因**: `updateCardCheckbox` 里 `card.getChildAt(0)` 直接当 `LinearLayout` 用。但实际结构是 `card → FrameLayout → top LinearLayout → checkbox`，中间多了一层 FrameLayout。cast 失败静默返回，功能失效无报错。

**正确做法**: 更新行内 View 前，先确认实际的 View 层级结构，多层嵌套就多层 `getChildAt`。
- **不要假设** "第一个 child 就是 target"
- **始终用 `as?` 安全转换 + `?: return`**，避免 crash 但也要保证取到了正确的层
- 如果功能静默失效，第一件事就检查 View 层级

---

## 6. `settingsRow` label 被长 value 挤没

**出现次数**: 1
**严重度**: 🟡 中

**根因**: label 用 `layout_width=0, weight=1`，value 用 `wrap_content`。长文本时 value 无限撑开，label 被挤到 0 宽度。

**正确做法**: value 也要加 `layout_width=0, weight=1` + `maxLines=1` + `ellipsize=end`，让两边平分空间，超长截断。

---

## 7. 第 1 天覆盖全部轮班的意外同步

**出现次数**: 1
**严重度**: 🟢 低

**根因**: `applyShiftDaySummaryChange` 里 `changedIndex == 0` 时，覆盖所有非"休"天。用户改第 1 天，结果 5 天全变了。

**正确做法**: 去掉特殊逻辑，每天独立。

---

## 预防机制

**为什么反复出现相同错误？** 因为 `AlarmListActivity.kt` 2700+ 行，靠人记不可靠。

**三道防线：**

| 防线 | 工具 | 触发时机 |
|------|------|---------|
| 🔴 自动扫描 | `./pre-check.sh` | 每次改代码后、构建前 |
| 🟡 安全构建 | `./safe_build.sh` | 一步完成：扫描 → 测试 → 构建 |
| 🟢 错误库 | 本文件 | 新会话开始必读 |

**每次改 `AlarmListActivity.kt` 后必须跑：**
```bash
./pre-check.sh    # 只扫描，不构建
# 或
./safe_build.sh   # 扫描 + 测试 + 构建 一条龙
```

---

## 8. 对话框 "完成" → renderEdit() → 缓冲读不到

**出现次数**: 3+（名称为0→已有）
**严重度**: 🔴 高

**根因**: 对话框"完成"按钮 → 写编辑缓冲 → 调 renderEdit() 重建页面 → 页面里读 `instance.xxx` 而非 `instanceXxx()` 辅助方法 → 显示旧值。

**症状**: 改完标题/时长/日期，点"完成"后页面不更新。但点"保存"后能显示——因为保存时用辅助方法正确读了缓冲。

**这是一个双重 Bug**:
1. 对话框回调调了 renderEdit()（违反 ERROR #1）
2. renderEdit() 里的 settingsRow 直接从 instance 读字段（违反 ERROR #4）

**正确做法**: 
- 对话框只写缓冲 + 行内更新对应 View（tag + findViewWithTag）
- 永远走 `instanceTitle()` / `instanceDateMs()` 读值，不直接读 `instance.xxx`

**自动检测**: `./pre-check.sh` ERROR #1 + ERROR #6 联合覆盖

---

## 9. 排序后 index 错位 — 用 sorted 的 idx 取 unsorted 的原列表

**出现次数**: 1
**严重度**: 🔴 高

**根因**: 
```kotlin
val sorted = list.sortedBy { ... }
sorted.forEachIndexed { idx, item ->
    originalList[idx]  // ← idx 对应的是 sorted 的位次，不是 original 的！
}
```
排序后的第 N 个元素，在原始列表里不是第 N 个。用 sorted 的 index 去 original 取数据，取到的不是同一个元素。

**症状**: 列表显示数据错位、开关点了不起作用、页面乱跳。

**正确做法**: 排序时把 ID 和数据绑在一起：
```kotlin
val cards = list.sortedBy { ... }.map { it.id to DemoAlarm(...) }
cards.forEach { (id, item) -> alarmCard(id, item) }
```

**自动检测**: `./pre-check.sh` ERROR #9 检查 sortedBy/sortedWith + forEachIndexed 组合

---

## 10. 特殊重复规则被调度层忽略 — summary 有值但 selections 为空

**出现次数**: 1
**严重度**: 🔴 高

**根因**: 对话框选择 "每天"/"每月"/"每年" 时，设置 `regularRepeatSummary = "每天"` 但 `regularRepeatSelections.clear()`。保存后 `repeatSelections` 为空。调度层 `schedulePlansForInstance` 只用 `repeatSelections` 构建 `selectedRules` → 空集 → `planRegular` 返回空 → 无闹钟注册。

**症状**: 卡片显示 "每天"/"每月"/"每年"（从 repeatSummary 读取），但下次响铃显示 "待计算"，实际不响铃。

**正确做法**: 调度层必须把 `repeatSummary` 中的特殊规则（每天/每月/每年）合并到 `selections` 里，不能只读 `repeatSelections`。

**自动检测**: 人工 review。grep 难以检测这种逻辑漏洞。

---

## 11. 时间滚轮两个轮子互相覆盖 — hour/minute 参数捕获

**出现次数**: 1
**严重度**: 🔴 高（影响所有编辑页）

**根因**: `timeWheels(hour, minute, onChange)` 中：
```kotlin
hourWheel.onIndexChanged = { onChange(it, minute) }   // minute 是函数参数val，永不变！
minuteWheel.onIndexChanged = { onChange(hour, it) }   // hour 是函数参数val，永不变！
```
滚完小时再滚分钟 → 小时被重置为初始值。两个轮子互相覆盖。

**正确做法**: 用 `var` 存当前值：
```kotlin
var hour = initialHour; var minute = initialMinute
hourWheel.onIndexChanged = { hour = it; onChange(hour, minute) }
minuteWheel.onIndexChanged = { minute = it; onChange(hour, minute) }
```

---

## 更新日志

| 日期 | 条目 | 触发 bug |
|------|:--:|------|
| 2026-07-09 | #11 时间滚轮互相覆盖 | 滚完小时滚分钟，值被重置 |
| 2026-07-09 | #10 特殊重复规则被调度忽略 | 每天/每月/每年选后不响 |
| 2026-07-09 | #9 排序 index 错位 | 闹钟开关失灵、页面乱跳 |
| 2026-07-09 | #8 对话框完成→renderEdit→缓冲读不到 | 标题/时长/日期改完不显示 |
| 2026-07-08 | #5 行内 View 层级 | 长按多选打钩失效 |
| 2026-07-08 | #6 settingsRow 挤压 | 闹钟名称太长页面变形 |
| 2026-07-08 | #3 空字符串陷阱 | 常规闹钟显示"待计算" |
| 2026-07-08 | #2 对话框直改实例 | 点取消名已保存 |
| 2026-07-08 | #7 第1天覆盖 | 排班设置意外同步 |
| 之前 | #1 render* 跳顶 | 多次 |
| 之前 | #4 重渲染覆盖缓冲 | 多次 |
