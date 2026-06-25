# 手机周期闹钟 App 技术方案

## 1. 技术目标

第一版以 Android 为唯一实现平台，最低支持 Android 5.0 / API 21。技术方案优先保证旧机、低端机、国产 ROM 下的可用性，而不是追求复杂视觉效果或云端能力。

当前实现路线：

- Android 原生优先。
- 轻量原生 UI 优先。
- 本地数据优先。
- 每次只注册下一次真实需要触发的系统闹钟。
- 触发后重新计算并注册下一次。
- 第一轮只实现“每 N 天闹钟”的完整闭环。
- 其他 5 个功能暂不实现，只保留规划或入口。

如果 Qclaw 原型不是 Android 原生，接管后先评估是否继续沿用；不能为了保留原型而牺牲闹钟可靠性。

## 2. Android 版本策略

- `minSdkVersion`: 21
- `targetSdkVersion`: 跟随目标应用商店当期要求，不在文档中写死。
- `compileSdkVersion`: 使用当前稳定 Android SDK。
- Java/Kotlin 版本：接管 Qclaw 后按原工程决定；新建工程优先 Kotlin。

版本重点：

- Android 5.0-5.1：使用 `setExact`，重点测锁屏、重启恢复与前台响铃。
- Android 6-7：重点处理 Doze、锁屏、重启恢复。
- Android 8-11：重点处理通知渠道、后台限制、国产 ROM 省电策略。
- Android 12+：重点处理精准闹钟权限。
- Android 13+：重点处理通知运行时权限。
- Android 14+：重点处理全屏提醒权限。

官方参考：

- Android alarm scheduling: https://developer.android.com/develop/background-work/services/alarms
- Google Play sensitive permissions: https://support.google.com/googleplay/android-developer/answer/16558241
- Google Play target API requirements: https://support.google.com/googleplay/android-developer/answer/11926878

## 3. 架构模块

推荐模块：

- `ui`: 页面、表单、列表、响铃页。
- `domain`: 周期规则、下次触发计算、校验。
- `storage`: 本地数据库、节假日数据、偏好设置。
- `scheduler`: AlarmManager 注册、取消、重排。
- `receiver`: 闹钟触发、开机、时间变化、时区变化、应用更新。
- `notification`: 通知渠道、高优先级提醒、全屏提醒。
- `permission`: 通知、精准闹钟、全屏提醒、定位权限引导。
- `geofence`: 地点触发闹钟，后置实现。

## 4. 数据模型

### 4.1 AlarmRule

字段：

- `id`: 本地唯一 ID
- `name`: 闹钟名称
- `enabled`: 是否启用
- `type`: 周期类型
- `timeOfDay`: 一天内触发时间
- `ringtoneUri`: 铃声
- `vibrate`: 是否震动
- `snoozeMinutes`: 贪睡分钟数
- `note`: 备注
- `createdAt`
- `updatedAt`

### 4.2 RecurrenceConfig

按类型存储：

- `every_n_days`: `startDate`, `intervalDays`
- `every_n_hours`: `startDateTime`, `intervalHours`, `activeStartTime`, `activeEndTime`
- `monthly_day`: `dayOfMonth`
- `lunar_date`: `lunarMonth`, `lunarDay`, `leapMonthMode`
- `holiday_adjusted`: `holidayRule`, `holidayDataYear`
- `geofence`: `latitude`, `longitude`, `radiusMeters`, `transitionType`, `activeStartTime`, `activeEndTime`

### 4.3 ScheduleInstance

字段：

- `ruleId`
- `nextTriggerAt`
- `requestCode`
- `scheduleMode`: exact, exact_allow_while_idle, alarm_clock, inexact
- `lastScheduledAt`
- `lastFailureReason`

## 5. 闹钟调度策略

原则：

- 不使用长期循环计时器。
- 不依赖常驻后台服务维持周期。
- 每条启用规则只注册下一次触发。
- 触发后立即计算并注册下一次。
- 开机、时间变化、时区变化、应用更新后重排所有启用闹钟。

建议 API：

- 用户明确需要准点响铃的闹钟：优先 `AlarmManager.setAlarmClock` 或 exact alarm。
- Android 5.0-5.1 使用 `setExact`；Android 6+ 需要低电耗模式下触发时使用 `setExactAndAllowWhileIdle`。
- 不要求准点的辅助任务：使用 inexact alarm 或 WorkManager。

权限策略：

- Android 12+：使用精准闹钟前检查是否具备 Alarms & reminders 能力。
- Android 13+：如果声明 `USE_EXACT_ALARM`，必须确保 App 核心功能就是闹钟，并准备商店声明。
- Android 13+：通知权限在首次需要提醒前申请。
- Android 14+：全屏提醒只用于真正响铃页面，非核心提示不得使用。

Google 文档明确指出，闹钟或日历这类核心功能依赖精准时间时可以使用 exact alarm；Google Play 对 `USE_EXACT_ALARM` 和全屏提醒均有核心用途限制，所以上架材料必须与实际功能一致。

## 6. 重排触发点

必须监听并处理：

- 设备开机完成
- 应用更新完成
- 系统时间变化
- 时区变化
- 精准闹钟权限状态变化
- 用户启用/禁用闹钟
- 用户编辑/删除闹钟
- 闹钟响铃后关闭或贪睡

处理方式：

- 读取所有 `enabled = true` 的 `AlarmRule`。
- 重新计算 `nextTriggerAt`。
- 取消旧 requestCode。
- 注册新的下一次闹钟。
- 写入 `ScheduleInstance`。

## 7. 周期计算规则

每 N 天：

- 基于起始日期和触发时间计算。
- 若起始触发点早于当前时间，按 intervalDays 向未来推进。

每 N 小时：

- 基于起始日期时间计算。
- 如果设置有效时间段，触发点必须落在有效时间段内。

每月 X 号：

- 当月不存在 X 号时跳过该月。
- 不自动改为月末。

农历日期：

- 使用离线农历数据或本地换算库。
- 默认不包含闰月同日。

法定假日调休：

- 使用内置 JSON。
- 数据按年份管理。
- 不联网自动拉取。

地点触发：

- 后置实现。
- 仅在用户创建地点闹钟时申请定位。
- 优先使用系统 geofence。

## 8. 本地存储

推荐：

- Room 或 SQLite 保存闹钟规则。
- JSON assets 保存节假日数据。
- SharedPreferences/DataStore 保存轻量设置。

限制：

- 不保存不必要的个人信息。
- 不上传闹钟内容。
- 不上传位置数据。
- 备份导入导出作为 Pro 候选功能，第一版不默认开启。

## 9. UI 和性能

低端旧机要求：

- 首页首屏只加载闹钟列表和必要状态。
- 避免开屏广告、启动页延迟、重动画。
- 表单分步清晰，控件使用系统组件优先。
- 空列表直接显示创建入口。
- 列表项高度稳定，不因文本变化造成跳动。
- 不在首页执行重型农历/节假日批量计算；只展示已缓存的下一次触发时间。

## 10. Qclaw 接管步骤

拿到 Qclaw 项目后按顺序执行：

1. 复制或迁移项目到当前仓库。
2. 记录目录结构、技术栈、构建命令。
3. 运行只读检查：依赖树、manifest 权限、入口 Activity、闹钟相关类。
4. 尝试构建 debug 包。
5. 在模拟器或真机运行。
6. 检查“每 N 天闹钟”是否真实注册系统闹钟，还是只做了 UI。
7. 写 `QCLAW_AUDIT.md`，列出可保留、需修复、需重写的部分。
8. 再开始功能续做。

## 11. 后续开发顺序

1. 接管 Qclaw 原型并确保可构建。
2. 稳定本地数据模型。
3. 完成每 N 天闹钟闭环。
4. 完成权限和重启重排。
5. 做旧机兼容测试。
6. 第一功能稳定后，再完成每月 X 号。
7. 再完成每 N 小时。
8. 再完成农历日期。
9. 再完成法定假日调休。
10. 最后评估地点触发。

## 12. 每 N 天闹钟首轮验收

接管源码后的第一轮代码目标：

- App 能构建 debug 包。
- 首页能进入每 N 天闹钟创建页。
- 用户能设置名称、起始日期、触发时间和间隔天数。
- 保存后本地持久化。
- 列表展示规则摘要和下次响铃时间。
- 关闭开关后取消系统闹钟。
- 删除后取消系统闹钟并删除本地数据。
- 到点后展示响铃页或高优先级提醒。
- 用户关闭后自动注册下一次。
- 用户贪睡后注册贪睡提醒，贪睡结束后恢复原周期。
- 重启手机后重排已启用闹钟。
- 系统时间或时区变化后重排已启用闹钟。
