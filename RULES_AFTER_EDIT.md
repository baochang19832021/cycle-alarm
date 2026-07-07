# 修改后规则

每次改完后，按影响范围完成检查。

1. 删除或重命名 XML ID 后，全局搜索旧 ID。
2. 新增 XML ID 后，确认 Kotlin 绑定或引用正确。
3. 改 Activity、Receiver、Service 后，核对 Manifest。
4. 改周期计算后，运行或更新单元测试。
5. 改调度、权限、响铃后，运行 debug 构建。
6. 改文案或权限说明后，核对 `STORE_READY.md` 和 `PERMISSION_REVIEW.md`。
7. 大 UI 改动后，确认必要权限入口、响铃页、列表入口没有丢失。
8. 阶段完成后更新 `CURRENT_STATUS.md`。
9. 发现新错误写入 `_MISTAKES.md`。
10. 可复发的问题升级到规则文件或 `TASK_ROUTING.md`。
