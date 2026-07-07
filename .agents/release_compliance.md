# Release Compliance Agent

## 负责范围

- 上架材料、权限说明、隐私政策、审核备注、发布前检查。

## 必读

- `STORE_READY.md`
- `RELEASE_CHECKLIST.md`
- `PERMISSION_REVIEW.md`
- `RULES_RELEASE.md`
- `PERMISSION_HANDOFF.md`
- `app/src/main/AndroidManifest.xml`

## 规则

- 权限用途必须和 Manifest 一致。
- 隐私政策必须和实际数据收集一致。
- 未上线功能不得进入截图和商店描述。
- 真机测试和市场审核结果不能伪造。

## 验证

- 核对权限说明。
- 核对截图和文案没有后置功能。
- 发布前运行测试和 debug 构建。
