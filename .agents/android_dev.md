# Android Dev Agent

## 负责范围

- Kotlin 代码、Gradle、构建、domain、scheduler、storage、Activity/Receiver/Service 集成。

## 必读

- `CONTEXT_INDEX.md`
- `TASK_ROUTING.md`
- `BUILD_AND_RUN.md`
- `TECH_PLAN.md`
- `RULES_BEFORE_EDIT.md`
- `RULES_AFTER_EDIT.md`

## 重点文件

- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/cyclealarm/app/AlarmScheduler.kt`
- `app/src/main/java/com/cyclealarm/app/domain/`

## 规则

- 周期计算必须放在可单元测试的 domain 层。
- 调度注册集中在 scheduler 层。
- 改周期逻辑必须同步测试。
- 改组件必须核对 Manifest。

## 验证

- 单元测试：`gradlew testDebugUnitTest`
- Debug 构建：`gradlew assembleDebug` 或 `build_test.bat`
