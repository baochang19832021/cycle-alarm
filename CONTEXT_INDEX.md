# 项目上下文入口

## 当前目标

把手机周期闹钟项目稳定推进到“每 N 天闹钟”可测试、可构建、可上架准备的主线状态。当前不扩展其他周期功能，不做账号、云同步、广告 SDK 或后端服务。

## 当前工程状态

- Android 原生单模块工程，包名 `com.cyclealarm.app`。
- `minSdk = 21`，`targetSdk = 34`。
- 主线功能：每 N 天闹钟。
- 已有列表页、编辑页、响铃页、必要权限页。
- 已有 AlarmManager 调度、BootReceiver 重排、AlarmReceiver、AlarmService、全屏响铃 Activity。
- 权限与响铃可靠性迁移内容见 `PERMISSION_HANDOFF.md`。
- 每 N 小时闹钟仍后置，不进入当前主线。

## 新会话必读顺序

1. `AGENTS.md`
2. `CURRENT_STATUS.md`
3. `TASK_ROUTING.md`
4. 与任务匹配的 `.agents/*.md`
5. `RULES_BEFORE_EDIT.md`
6. 修改后按 `RULES_AFTER_EDIT.md`
7. **涉及架构/调度/功能规划时额外读 `BENCHMARK_ANALYSIS.md`** — 开源竞品对比和已知差距

发布和权限任务额外读：

- `PERMISSION_HANDOFF.md`
- `PERMISSION_REVIEW.md`
- `RULES_RELEASE.md`
- `STORE_READY.md`

## 当前禁止事项

- 不提前实现其他 5 个周期功能。
- 不引入广告 SDK。
- 不引入账号、云同步、后端服务。
- 不申请通讯录、短信、通话记录、相机、麦克风、全部文件访问。
- 不移除现有可靠性链路：通知、精确闹钟、前台服务、全屏响铃、重启重排、权限页。
- 不伪造真机测试或应用市场审核结果。

## 当前主要风险

- 国产 ROM 后台和锁屏限制仍需真机验证。
- `SYSTEM_ALERT_WINDOW`、全屏提醒、精准闹钟属于审核敏感点。
- 周期计算必须由单元测试保护。
- 大 UI 改动容易破坏 XML ID 与 Kotlin 引用。
- README 和部分历史文档可能仍含旧包名或旧实现描述，修改发布材料前需核对当前代码。

## 阶段完成标准

- 代码能通过相关测试或 debug 构建。
- `CURRENT_STATUS.md` 记录本阶段完成内容、验证结果、下一步。
- 如果出现新错误，写入 `_MISTAKES.md`。
- 如果错误可能复发，升级到 `RULES_BEFORE_EDIT.md`、`RULES_AFTER_EDIT.md` 或任务路由。
