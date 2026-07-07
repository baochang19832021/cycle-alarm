# 任务路由

每个任务先判断类型，再按对应路线读取文档、修改文件和验证。不要直接从聊天历史猜上下文。

## UI / 布局任务

- 适用：页面重排、颜色、字号、间距、卡片、按钮、列表、弹窗。
- 负责 agent：`.agents/ui_layout.md`
- 必读：`PROJECT_RULES.md`、`_MISTAKES.md`、相关 XML、相关 Activity/Adapter。
- 修改前：列出会触碰的 XML ID 和 Kotlin 引用。
- 修改后：全局搜索删除或重命名的 ID，运行 debug 构建。
- 禁止：为了视觉改动删除闹钟可靠性入口或破坏响铃链路。

## 闹钟调度 / 响铃可靠性任务

- 适用：AlarmManager、Receiver、Service、BootReceiver、响铃页、贪睡、关闭、重排。
- 负责 agent：`.agents/reliability.md` 和 `.agents/android_dev.md`
- 必读：`PERMISSION_HANDOFF.md`、`TECH_PLAN.md`、`TEST_PLAN.md`、Manifest、`AlarmScheduler.kt`、`AlarmReceiver.kt`、`AlarmService.kt`。
- 修改后：运行单元测试和 debug 构建；更新 `CURRENT_STATUS.md`。
- 禁止：移除重启、时间变化、时区变化、应用更新后的重排。

## 周期规则 / 时间计算任务

- 适用：每 N 天计算、起始日期、时间已过顺延、一次性闹钟。
- 负责 agent：`.agents/android_dev.md`
- 必读：`TECH_PLAN.md` 第 7 节、`TEST_PLAN.md` 周期计算用例、domain 测试。
- 必须：修改 domain 逻辑时同步新增或更新单元测试。
- 验证：运行 `testDebugUnitTest` 或等价单元测试任务。

## 权限 / 合规任务

- 适用：Manifest 权限、权限页、系统设置跳转、审核说明、隐私政策。
- 负责 agent：`.agents/release_compliance.md` 和 `.agents/reliability.md`
- 必读：`PERMISSION_HANDOFF.md`、`PERMISSION_REVIEW.md`、`RULES_RELEASE.md`、`STORE_READY.md`、Manifest。
- 修改后：核对权限用途和商店文案一致。
- 禁止：为后置功能提前申请定位、后台定位或无关敏感权限。

## 构建失败 / 依赖任务

- 适用：Gradle、SDK、依赖、编译错误、资源错误。
- 负责 agent：`.agents/android_dev.md`
- 必读：`BUILD_AND_RUN.md`、`app/build.gradle.kts`、报错文件。
- 修改后：运行失败任务的最小复现命令，再运行 debug 构建。
- 禁止：未经验证引入第三方库。

## 上架 / 发布任务

- 适用：截图、审核备注、隐私政策、权限说明、版本号、阶段包。
- 负责 agent：`.agents/release_compliance.md`
- 必读：`RELEASE_CHECKLIST.md`、`PERMISSION_REVIEW.md`、`STORE_READY.md`、`RULES_RELEASE.md`。
- 必须：明确区分本机已完成和外部待办。
- 禁止：宣传未上线功能或伪造真机/市场审核结果。

## 研究 / 调研任务

- 适用：竞品分析、技术选型、开源项目参考、最佳实践调研。
- 负责 agent：`.agents/product.md`
- 必读：`BENCHMARK_ANALYSIS.md`（已有的竞品对比）、`CONTEXT_INDEX.md`。
- 必须：调研结论写入项目文档并接入索引链路，避免下次重复调研。

## 文档 / 交接任务

- 适用：状态更新、阶段总结、错误复盘、规则沉淀、说明书维护。
- 负责 agent：`.agents/product.md`
- 必读：`CONTEXT_INDEX.md`、`CURRENT_STATUS.md`、`_MISTAKES.md`。
- 必须：文档反映当前真实代码状态。
- 阶段完成：更新 `CURRENT_STATUS.md`。
