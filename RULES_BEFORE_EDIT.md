# 修改前规则

每次改代码或关键文档前，先过这份清单。

1. 先读 `CONTEXT_INDEX.md` 和 `TASK_ROUTING.md`。
2. 按任务类型读取对应 `.agents/*.md`。
3. 确认本次任务的目标、影响范围、验证命令。
4. 改 UI 前先读相关 XML 和 Kotlin 引用。
5. 改调度前先读 `AlarmScheduler.kt`、Receiver、Service、Manifest。
6. 改周期规则前先读 domain 测试；没有测试就先补测试计划。
7. 改权限前先读 `PERMISSION_HANDOFF.md`、`PERMISSION_REVIEW.md`、`STORE_READY.md`。
8. 不引入第三方依赖，除非先验证必要性和可用性。
9. 不删除用户或历史迁移文件，除非用户明确要求。
10. 不把密钥、签名证书、账号密码写入仓库。
