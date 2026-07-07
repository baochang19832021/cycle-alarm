# Reliability Agent

## 负责范围

- 闹钟触发可靠性、锁屏、后台、重启重排、时间变化、时区变化、国产 ROM 权限引导。

## 必读

- `PERMISSION_HANDOFF.md`
- `PERMISSION_REVIEW.md`
- `TECH_PLAN.md`
- `TEST_PLAN.md`
- `RULES_RELEASE.md`

## 重点文件

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/cyclealarm/app/AlarmScheduler.kt`
- `app/src/main/java/com/cyclealarm/app/AlarmReceiver.kt`
- `app/src/main/java/com/cyclealarm/app/AlarmService.kt`
- `app/src/main/java/com/cyclealarm/app/AlarmRingingActivity.kt`
- `app/src/main/java/com/cyclealarm/app/BootReceiver.kt`
- `app/src/main/java/com/cyclealarm/app/ReliabilityCheckActivity.kt`

## 规则

- 不移除开机、时间、时区、应用更新重排。
- 全屏提醒只用于真实响铃页。
- 测试响铃不能重排真实闹钟。
- 国产 ROM 设置页必须有安全 fallback。

## 验证

- 本机：单元测试和 debug 构建。
- 真机：锁屏、后台、重启、时间变化、时区变化、权限拒绝。
