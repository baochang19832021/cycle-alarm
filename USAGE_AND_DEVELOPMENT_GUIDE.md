# 使用与开发说明书

这份说明用于以后自己或新 agent 接手项目时快速进入正确流程。

## 1. 新会话接手流程

每次新会话先读：

1. `CONTEXT_INDEX.md`
2. `AGENTS.md`
3. `CURRENT_STATUS.md`
4. `TASK_ROUTING.md`
5. 与任务匹配的 `.agents/*.md`
6. `RULES_BEFORE_EDIT.md`

不要依赖旧聊天记录。上下文以仓库文档为准。

## 2. 普通开发任务怎么走

1. 判断任务类型。
2. 按 `TASK_ROUTING.md` 找到路由。
3. 读取对应 agent 文档。
4. 明确要改哪些文件、影响哪些功能、如何验证。
5. 修改前过 `RULES_BEFORE_EDIT.md`。
6. 修改后过 `RULES_AFTER_EDIT.md`。
7. 运行对应测试或构建。
8. 阶段完成后更新 `CURRENT_STATUS.md`。

## 3. UI 修改注意事项

- UI 改动先读 `.agents/ui_layout.md`、`PROJECT_RULES.md`、`_MISTAKES.md`。
- 删除或重命名任何 XML ID 后，立刻全局搜索旧 ID。
- 改 XML 时同步看 Kotlin `findViewById`、Adapter、点击事件。
- 大 UI 改动不能丢掉：
  - 每 N 天闹钟列表入口
  - 必要权限入口
  - 响铃页
  - 启停、删除、编辑流程
- 默认风格：极简、舒适、低操作负担。

## 4. 闹钟调度修改注意事项

- 调度相关任务先读 `.agents/reliability.md`。
- `AlarmScheduler.kt` 负责系统闹钟注册、取消、重排和持久化。
- 周期计算在 `app/src/main/java/com/cyclealarm/app/domain/AlarmTimeCalculator.kt`。
- 不移除 BootReceiver 中的开机、时间变化、时区变化、应用更新重排。
- 测试响铃必须保持和真实闹钟隔离，不能触发真实闹钟重排。
- 改调度后至少跑单元测试和 debug 构建。

## 5. 周期规则修改注意事项

- 改周期规则必须同步更新 `AlarmTimeCalculatorTest.kt`。
- 必测场景：
  - `N = 0`
  - `N = 1`
  - `N = 40`
  - 当前时间已过
  - 起始日期为空
  - 非法 N 值
  - 秒和毫秒归零
- 不要把 Calendar 计算散落回 Activity 或 Scheduler。

## 6. 权限修改注意事项

- 权限相关任务先读 `.agents/release_compliance.md` 和 `.agents/reliability.md`。
- 新增、删除或调整权限后，必须同步检查：
  - `app/src/main/AndroidManifest.xml`
  - `PERMISSION_REVIEW.md`
  - `PERMISSION_HANDOFF.md`
  - `STORE_READY.md`
  - `RULES_RELEASE.md`
- 不为后置功能提前申请权限。
- 不宣传“绝对准时”或“绕过系统限制”。

## 7. 构建和测试命令

PowerShell 项目根目录：

```powershell
.\gradlew testDebugUnitTest
.\gradlew assembleDebug
```

也可以运行：

```powershell
.\build_test.bat
```

如果本机 JDK/Gradle 环境异常，先读 `BUILD_AND_RUN.md`。

注意：当前项目路径包含中文字符。为保证 Android JVM 单元测试在本机稳定运行，`app/build.gradle.kts` 会在 `testDebugUnitTest` 前把 Kotlin main/test class 同步到 `C:\Users\ALIENWARE\.gradle\cyclealarm-test-classes\HospitalAlarm\debugUnitTest` 这类 ASCII 路径，并在测试执行前加入 classpath。不要随意删除这段配置，除非项目迁移到纯 ASCII 路径并确认单元测试仍通过。

## 8. 阶段完成后必须更新

- `CURRENT_STATUS.md`：当前状态、完成内容、验证结果、下一步。
- `_MISTAKES.md`：如果出现新错误。
- `RULES_BEFORE_EDIT.md` / `RULES_AFTER_EDIT.md` / `TASK_ROUTING.md`：如果错误可能复发。
- `STORE_READY.md` 或 `PERMISSION_REVIEW.md`：如果涉及发布或权限。

## 9. 外部验证说明

以下不能由本机命令替代：

- 小米/Redmi、OPPO/vivo、华为/荣耀真机锁屏和后台验证。
- Android 14+ 全屏提醒权限验证。
- 应用市场后台审核。
- 隐私政策 URL、用户协议 URL 托管。
- 正式签名证书备份。

这些完成前，只能标记为“待验证”，不能写成已通过。
