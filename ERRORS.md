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

## 更新日志

| 日期 | 条目 | 触发 bug |
|------|:--:|------|
| 2026-07-08 | #5 行内 View 层级 | 长按多选打钩失效 |
| 2026-07-08 | #6 settingsRow 挤压 | 闹钟名称太长页面变形 |
| 2026-07-08 | #3 空字符串陷阱 | 常规闹钟显示"待计算" |
| 2026-07-08 | #2 对话框直改实例 | 点取消名已保存 |
| 2026-07-08 | #7 第1天覆盖 | 排班设置意外同步 |
| 之前 | #1 render* 跳顶 | 多次 |
| 之前 | #4 重渲染覆盖缓冲 | 多次 |
