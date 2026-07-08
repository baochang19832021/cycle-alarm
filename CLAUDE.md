# CLAUDE.md

## 项目概述

手机周期闹钟（Cycle Alarm）— Android 原生离线周期提醒工具。面向中国安卓市场，优先兼容 Android 5.0+ 旧手机和国产 ROM。

**技术栈**: Android 原生 / Kotlin / Gradle / minSdk 21 / targetSdk 34
**包名**: `com.cyclealarm.app`
**当前主线**: 每 N 天闹钟 完整闭环
**项目路径**: `D:\firstcc`

## 新会话必读顺序

1. `CLAUDE.md`（本文件）
2. `ERRORS.md` — 🔴 错误库，每次写代码前必读，避免重复犯错
3. `CURRENT_STATUS.md` — 最新开发状态、版本号、验证结果
3. `TASK_ROUTING.md` — 按任务类型路由到对应文档和 agent
4. 相关 `.agents/*.md` — 按任务类型读取
5. `RULES_BEFORE_EDIT.md` — 修改前检查清单
6. `RULES_AFTER_EDIT.md` — 修改后检查清单
7. `BENCHMARK_ANALYSIS.md` — 开源竞品对比（涉及架构/调度/功能规划时必读）
8. 计划文件（路径见 `CURRENT_STATUS.md` 顶部 "▶ 当前任务" 区块）— 当前进行中的 Phase 和具体下一步

## 快捷触发词

以下词语在新会话中具有特殊含义，Claude 应自动执行对应操作，无需用户展开说明：

- **"继续"** / **"继续上次的"** / **"resume"** / **"rewind"** →
  1. 读取 `CURRENT_STATUS.md`（顶部 "▶ 当前任务" 区块获取当前 Phase 和计划文件路径）
  2. 读取 `CURRENT_STATUS.md` 顶部指向的计划文件
  3. 读取 `BENCHMARK_ANALYSIS.md`
  4. 从 CURRENT_STATUS.md 顶部标注的当前 Phase 和下一步操作开始工作
  默认：延续上次未完成的开发 Phase。用户只需说一个字"继续"。
- **"回退"** / **"回滚"** / **"rollback"** → 执行 `git tag -l 'checkpoint-*' --format='%(refname:short) | %(subject)'` 列出所有检查点及其描述，用 AskUserQuestion 让用户选择回退到哪个点（选项显示格式：`checkpoint-01 | 多闹钟实例改造前，v1.0.14，打勾删除+FAB`），然后执行 `git reset --hard <选中的tag> && git clean -fd`。每完成一个重要改动后创建新的检查点标签。
- **"修 P0"** / **"修那个 bug"** → 修复 `AlarmScheduler.kt:211` 的 `setAlarmClock` 版本判断错误。
- **"P1 改进"** → 读取 `BENCHMARK_ANALYSIS.md` 第五节的 P1 改进建议列表，逐一讨论或实现。

## 当前工程状态

- Android 原生单模块工程
- 版本: `versionCode = 13`, `versionName = "1.0.12"`
- 主线功能: 每 N 天闹钟（已完成闭环）
- Pixso 风格新 UI 原型已集成到 `AlarmListActivity.kt`
- 四个新闹钟类型已连接到 `AlarmScheduler`
- 周期计算在 `domain/` 层，有 JUnit 单元测试
- 测试通过: `testDebugUnitTest` ✅, `assembleDebug` ✅

## 核心架构

```
app/src/main/java/com/cyclealarm/app/
├── AlarmListActivity.kt    — 主页（Pixso 新 UI，四 Tab）
├── MainActivity.kt          — 旧编辑页（可靠性链路保留）
├── AlarmScheduler.kt        — 系统闹钟注册/取消/重排
├── AlarmReceiver.kt         — 闹钟触发广播接收器
├── AlarmService.kt          — 响铃前台服务
├── AlarmRingingActivity.kt  — 全屏响铃页
├── BootReceiver.kt          — 开机/时间变化重排
├── ReliabilityCheckActivity.kt — 必要权限检查页
├── WheelView.kt             — 自绘滚轮控件
├── IntervalPickerDialog.kt  — 间隔选择弹窗
└── domain/
    ├── AlarmTimeCalculator.kt      — 周期时间计算（天/分/月）
    └── UiAlarmSchedulePlanner.kt   — UI 调度计划生成
```

## 教科书原则 🔴

**Pixso 之前的成功老版本是「教科书」**。每一次改动都必须遵守：

- 新版 UI **不能破坏**可靠性链路（调度 → 通知 → 响铃页 → 前台服务 → 重启重排）
- 铃声预览、长按删除、轮班排班、日历展示 — 沿用已验证过的模式，不重新发明
- 新功能接入调度器时，参考已有教科书实现（如每 N 天闹钟的调度-重排闭环）
- 遇到不确定的实现时，优先看 `MainActivity.kt`（旧编辑页）和 `AlarmScheduler.kt` 作为参考

## 禁止事项

- ❌ 不提前实现其他 5 个周期功能（每 N 小时、每月 X 号、农历、法定假日、地点触发）
- ❌ 不引入广告 SDK、账号系统、云同步、后端服务
- ❌ 不申请通讯录/短信/相机/麦克风/全部文件访问权限
- ❌ 不移除现有可靠性链路：调度、通知、响铃页、前台服务、重启重排、权限页
- ❌ 不删除用户文件或历史备份
- ❌ 不把密钥、签名证书写入仓库

## 开发工作流

1. **修改前**：读 `RULES_BEFORE_EDIT.md`，确认影响范围
2. **修改中**：遵循 `PROJECT_RULES.md` 的设计规范
3. **修改后**：按 `RULES_AFTER_EDIT.md` 自查，运行构建验证
4. **阶段完成**：更新 `CURRENT_STATUS.md`

## 强制验证流程

**每次接手任务，先验证再动手：**

```
1. 读文档 → 2. 跑测试 → 3. 跑构建 → 4. 改代码
```

- 第一步：读 `CURRENT_STATUS.md`、`CONTEXT_INDEX.md`、`TASK_ROUTING.md`、`AGENTS.md`
- 第二步：`./gradlew testDebugUnitTest` — 确认测试通过
- 第三步：`./gradlew assembleDebug` — 确认构建通过
- 第四步：以上全部通过后，才能开始改代码
- 绝不跳过验证直接修改

## 构建命令

```bash
# 构建 debug APK
./gradlew assembleDebug

# 运行单元测试
./gradlew testDebugUnitTest

# 或使用项目自带的脚本（指定 JDK 和 Gradle 路径）
./build_test.bat
```

Debug APK 输出: `app/build/outputs/apk/debug/app-debug.apk`

## 设计规范

- 主色: `#FF7043` 橘橙，关闭状态 `#BBBBBB` 灰，开启状态 `#1A1A1A` 黑
- 字体: 标题 30sp 粗体，时间 36sp 粗体，正文 16sp
- 圆角: 卡片 24dp，按钮 22dp
- 滚轮: 选中 38sp 粗体 #222222，未选 28sp #999999，itemHeight=52dp，visibleCount=3

## 6 项功能规划

| # | 功能 | 状态 |
|---|------|------|
| 1 | 每 N 天闹钟 | ✅ 主线闭环 |
| 2 | 每 N 小时闹钟 | ⏸️ 后置 |
| 3 | 每月 X 号闹钟 | ⏸️ 后置 |
| 4 | 农历日期闹钟 | ⏸️ 后置 |
| 5 | 法定假日调休闹钟 | ⏸️ 后置 |
| 6 | 地点触发闹钟 | ⏸️ 后置 |

## 关键风险

- 国产 ROM（小米/OPPO/vivo/华为）后台和锁屏限制需真机验证
- `SYSTEM_ALERT_WINDOW`、全屏提醒、精准闹钟是应用商店审核敏感点
- 周期计算必须由单元测试保护
- 大 UI 改动容易破坏 XML ID 与 Kotlin 引用
- 项目路径含中文字符，Gradle 测试有时需要特殊处理（见 `CURRENT_STATUS.md` 实现说明）
