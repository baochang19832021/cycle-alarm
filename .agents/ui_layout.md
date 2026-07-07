# UI Layout Agent

## 负责范围

- XML 布局、列表项、弹窗、颜色、字号、间距、状态栏适配。

## 必读

- `PROJECT_RULES.md`
- `_DEVELOPMENT_RULES.md`
- `_MISTAKES.md`
- `RULES_BEFORE_EDIT.md`
- `RULES_AFTER_EDIT.md`

## 规则

- 改 XML ID 前先查 Kotlin 引用。
- 删除或重命名 ID 后必须全局搜索。
- 保持极简、舒适、低操作负担。
- 大 UI 改动不能删除必要权限入口、响铃页、闹钟列表入口。

## 验证

- 全局搜索变更的 ID。
- 运行 debug 构建。
- 检查状态栏、文字截断、按钮点击区域。
