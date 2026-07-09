#!/bin/bash
# pre-check.sh — 每次改代码后自动扫描已知反模式
# 用法: ./pre-check.sh
# 所有模式对应 ERRORS.md 中的条目

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_DIR="$PROJECT_DIR/app/src/main/java/com/cyclealarm/app"
MAIN_FILE="$APP_DIR/AlarmListActivity.kt"
WARNINGS=0
ALL_FILES="$APP_DIR/*.kt"

echo "🔍 扫描已知反模式..."
echo ""

# ═══════════════════════════════════════════════════════════
# ERROR #1: renderEdit() 整页重建 → ScrollView 跳顶
# 反模式：对话框/行内事件的回调里调用 renderEdit()
# 正确做法：用 rebuildRow() 或 findViewById 行内更新
# 合理场景：闹钟列表卡片点击跳转编辑页、保存后返回列表
# 记忆: avoid-full-render-on-small-changes
# ═══════════════════════════════════════════════════════════
echo "── ERROR #1: renderEdit() 整页重建 ──"
# 只检测明确的反模式：renderEdit 出现在 showXxxDialog / settingsRow / setOnClickListener 回调
# 排除合理场景：闹钟卡片点击跳转（列表→编辑页的导航）
DIALOG_RENDER=$(grep -nP '(showDialog|settingsRow|show\w+Dialog|setPositiveButton|setNeutralButton).*renderEdit|commitAndClose.*renderEdit' "$MAIN_FILE" | grep -v '^\s*//')
if [ -n "$DIALOG_RENDER" ]; then
    echo "  🔴 对话框/行内回调里直接调 renderEdit():"
    echo "$DIALOG_RENDER" | while IFS= read -r l; do echo "     $l"; done
    FOUND=1
else
    echo "  ✅ 通过"
    FOUND=0
fi

if [ $FOUND -gt 0 ]; then
    echo "  → 应该用 rebuildRow() 或 findViewById 只更新单个 View"
    echo "  → 参考 memory: avoid-full-render-on-small-changes"
    WARNINGS=$((WARNINGS + 1))
fi
echo ""

# ═══════════════════════════════════════════════════════════
# ERROR #2: 对话框直改实例 → 取消失效
# 反模式：AlertDialog 的 setPositiveButton 里直接写 alarmInstances[]
# 正确做法：对话框只写编辑缓冲，点保存时才提交实例
# 记忆: dialog-must-use-edit-buffer
# ═══════════════════════════════════════════════════════════
echo "── ERROR #2: 对话框直改实例 ──"
# 搜索 alarmInstances 的赋值操作（排除明确在保存逻辑里的）
DIRECT_WRITE=$(grep -n 'alarmInstances\[' "$MAIN_FILE" | grep -v '//' | grep -v 'copy' | grep -v 'save\|保存\|idx\|indexOf')
if [ -n "$DIRECT_WRITE" ]; then
    echo "  🔴 alarmInstances 直接赋值:"
    echo "$DIRECT_WRITE" | while IFS= read -r l; do echo "     $l"; done
    echo "  → 对话框点\"确定\"只能写编辑缓冲 (editXxx = ...)"
    echo "  → 参考 memory: dialog-must-use-edit-buffer"
    WARNINGS=$((WARNINGS + 1))
fi
# 更精确的检查：AlertDialog + setPositiveButton + alarmInstances 出现在相近行
DIALOG_BLOCK_START=""
DIRECT_IN_DIALOG=0
while IFS= read -r line; do
    lineno=$(echo "$line" | cut -d: -f1)
    if echo "$line" | grep -q 'AlertDialog\.Builder\|setPositiveButton\|setNeutralButton'; then
        DIALOG_BLOCK_START=$lineno
    fi
    if [ -n "$DIALOG_BLOCK_START" ] && echo "$line" | grep -qP 'alarmInstances\['; then
        # 检查是否在对话框回调内（行号接近）
        diff=$((lineno - DIALOG_BLOCK_START))
        if [ $diff -le 30 ] && [ $diff -ge 0 ]; then
            echo "  🔴 对话框内直改实例（行 ~$lineno）"
            DIRECT_IN_DIALOG=$((DIRECT_IN_DIALOG + 1))
        fi
    fi
done < <(grep -n 'AlertDialog\|alarmInstances\[' "$MAIN_FILE" | grep -v '//')
if [ $DIRECT_IN_DIALOG -gt 0 ]; then
    WARNINGS=$((WARNINGS + 1))
fi
if [ -z "$DIRECT_WRITE" ] && [ $DIRECT_IN_DIALOG -eq 0 ]; then
    echo "  ✅ 通过"
fi
echo ""

# ═══════════════════════════════════════════════════════════
# ERROR #3: Kotlin ?: 空字符串陷阱
# 反模式: map["key"] ?: fallback  →  "" 不会被 ?: 拦截
# 正确做法: map["key"]?.takeIf { it.isNotBlank() } ?: fallback
# 记忆: kotlin-elvis-empty-string-trap
# ═══════════════════════════════════════════════════════════
echo "── ERROR #3: ?: 空字符串陷阱 ──"
# 检查从 Map/SharedPreferences 取值没有 takeIf 保护的模式
# 针对我们代码库中已知危险模式
ELVIS_UNSAFE=$(grep -nP '(prefs|sp|configMap|savedMap|selections)\[.*\]\s*\?\s*:' "$MAIN_FILE" | grep -v 'takeIf\|isNotBlank\|isNotEmpty\|//\|toString()')
if [ -n "$ELVIS_UNSAFE" ]; then
    echo "  🟡 Map 取值 ?: 缺少 takeIf 保护:"
    echo "$ELVIS_UNSAFE" | while IFS= read -r l; do echo "     $l"; done
    echo "  → 空字符串 \"\" 不会被 ?: 拦截"
    echo "  → 加上 ?.takeIf { it.isNotBlank() } ?: fallback"
    echo "  → 参考 memory: kotlin-elvis-empty-string-trap"
    WARNINGS=$((WARNINGS + 1))
else
    echo "  ✅ 通过"
fi
echo ""

# ═══════════════════════════════════════════════════════════
# ERROR #4: 重渲染覆盖编辑缓冲
# 反模式: renderEdit() 每次都从 instance 重新加载 → 覆盖 editXxx
# 正确做法: if (!isSameInstance) 才加载
# 记忆: dont-reload-instance-on-rerender
# ═══════════════════════════════════════════════════════════
echo "── ERROR #4: 重渲染覆盖编辑缓冲 ──"
# 检查 renderEdit 内部是否缺少 isSameInstance 保护
if grep -q 'private fun renderEdit' "$MAIN_FILE"; then
    if ! grep -A30 'private fun renderEdit' "$MAIN_FILE" | grep -q 'isSameInstance'; then
        echo "  🔴 renderEdit() 缺少 isSameInstance 保护！"
        echo "  → 每次重渲染都会覆盖编辑缓冲"
        echo "  → 加上 if (!isSameInstance) { editXxx = null; loadConfig() }"
        WARNINGS=$((WARNINGS + 1))
    else
        echo "  ✅ isSameInstance 保护存在"
    fi
fi
echo ""

# ═══════════════════════════════════════════════════════════
# ERROR #5: getChildAt() 硬转型 → 静默失败
# 反模式: card.getChildAt(0) as LinearLayout → cast 失败 crash 或静默返回
# 正确做法: card.getChildAt(0) as? LinearLayout ?: return
# 记忆: 无独立文件，在 ERRORS.md #5 中
# ═══════════════════════════════════════════════════════════
echo "── ERROR #5: getChildAt() 硬转型 ──"
HARD_CAST=$(grep -nP 'getChildAt\(\d+\)\s+as\s+[^?]' "$MAIN_FILE" | grep -v '//')
if [ -n "$HARD_CAST" ]; then
    echo "  🟡 getChildAt() 后直接 as 转型（没用 as?）:"
    echo "$HARD_CAST" | while IFS= read -r l; do echo "     $l"; done
    echo "  → 层级变化时 crash 或静默失效"
    echo "  → 改用 as? TargetType ?: return"
    WARNINGS=$((WARNINGS + 1))
else
    echo "  ✅ 通过"
fi
echo ""

# ═══════════════════════════════════════════════════════════
# ERROR #6: instance.title / instance.xxx 直接读取
# 反模式: val t = instance.title → 不读编辑缓冲
# 正确做法: instanceTitle(instanceId) / instanceDateMs(instanceId)
# 记忆: dont-reload-instance-on-rerender
# ═══════════════════════════════════════════════════════════
echo "── ERROR #6: instance 字段跳过编辑缓冲 ──"
# 在 renderEdit 函数体内找 instance.xxx 直接读取
# 用 awk 提取 renderEdit 函数体范围
RENDER_START=$(grep -n 'private fun renderEdit' "$MAIN_FILE" | head -1 | cut -d: -f1)
if [ -n "$RENDER_START" ]; then
    # 找下一个 private fun（约等于函数结束）
    RENDER_END=$(grep -n 'private fun' "$MAIN_FILE" | awk -F: -v start="$RENDER_START" '$1 > start {print $1; exit}')
    if [ -n "$RENDER_END" ]; then
        # 在 renderEdit 函数体内找 instance.title / instance.dateMs / instance.hour / instance.minute
        BODY_READS=$(sed -n "${RENDER_START},${RENDER_END}p" "$MAIN_FILE" | grep -n 'instance\.\(title\|dateMs\|hour\|minute\)' | grep -v 'instanceTitle\|instanceDateMs\|//\|editHour\|editMinute\|alarmInstances\|\.copy(')
        if [ -n "$BODY_READS" ]; then
            echo "  🔴 renderEdit() 内部直接读 instance 字段:"
            echo "$BODY_READS" | while IFS= read -r l; do echo "     (相对行) $l"; done
            echo "  → 编辑页应该走 instanceTitle()/instanceDateMs() 读编辑缓冲"
            WARNINGS=$((WARNINGS + 1))
        else
            echo "  ✅ 通过"
        fi
    else
        echo "  ⚠️  无法确定 renderEdit() 范围，跳过"
    fi
else
    echo "  ⚠️  未找到 renderEdit()，跳过"
fi
echo ""

# ═══════════════════════════════════════════════════════════
# ERROR #9: 排序后 index 错位
# 反模式: sortedBy/sortedWith 后 forEachIndexed 用 idx 取另一个列表
# 正确做法: 排序时把 ID 和数据绑成 Pair，不靠 index
# 记忆: 在 ERRORS.md #9
# ═══════════════════════════════════════════════════════════
echo "── ERROR #9: 排序 index 错位 ──"
# 检测 sortedBy/sortedWith 后紧跟着 forEachIndexed 内部用 [idx]
SORT_MISMATCH=$(grep -n 'sortedBy\|sortedWith' "$MAIN_FILE" | grep -v '//' | grep -v 'private fun')
FOUND9=0
if [ -n "$SORT_MISMATCH" ]; then
    # 检查 forEachIndexed 是否在同一函数内用了另一个列表的 [idx]
    while IFS= read -r sortLine; do
        sortLineno=$(echo "$sortLine" | cut -d: -f1)
        # 检查从 sort 行开始往下 30 行内是否有 forEachIndexed
        BLOCK=$(sed -n "${sortLineno},$((sortLineno + 30))p" "$MAIN_FILE")
        if echo "$BLOCK" | grep -q 'forEachIndexed' && echo "$BLOCK" | grep -qP '\w+\[\w*idx\w*\]'; then
            FOUND9=$((FOUND9 + 1))
        fi
    done <<< "$SORT_MISMATCH"
fi
if [ $FOUND9 -gt 0 ]; then
    echo "  🔴 发现 sortedBy/sortedWith + forEachIndexed[x] 组合，可能 index 错位"
    echo "  → 排序后的 index 不等于原列表的 index"
    echo "  → 用 Pair<ID, Data> 绑定，别靠 index"
    echo "  → 参考 ERRORS.md #9"
    WARNINGS=$((WARNINGS + 1))
else
    echo "  ✅ 通过"
fi
echo ""

# ═══════════════════════════════════════════════════════════
# 额外检查: 重复代码模式（同一逻辑写了两遍以上）
# 维护性风险，容易一处修了另一处忘
# ═══════════════════════════════════════════════════════════
echo "── 代码健康: 重复模式 ──"
DUP_TITLE=$(grep -c 'settingsRow("闹钟名称"' "$MAIN_FILE")
if [ "$DUP_TITLE" -gt 1 ]; then
    echo "  🟡 \"闹钟名称\" settingsRow 出现 $DUP_TITLE 次"
    echo "  → 建议提取为 titleSettingsRow() 方法，只有一处逻辑"
fi
DUP_DATE=$(grep -c 'settingsRow("开始日期"\|settingsRow("响铃日期"' "$MAIN_FILE")
if [ "$DUP_DATE" -gt 1 ]; then
    echo "  🟡 日期 settingsRow 出现 $DUP_DATE 次 → 建议提取方法"
fi
# 检查 renderEdit 行数（超过 200 行建议拆分）
RENDER_LINES=$(sed -n "${RENDER_START},${RENDER_END}p" "$MAIN_FILE" | wc -l)
if [ "$RENDER_LINES" -gt 500 ]; then
    echo "  🟡 renderEdit() 约 $RENDER_LINES 行（超大，建议拆分）"
fi
echo "  ℹ️  文件总行数: $(wc -l < "$MAIN_FILE")"
echo ""

# ═══════════════════════════════════════════════════════════
echo "════════════════════════════════════════════════════════"
if [ $WARNINGS -eq 0 ]; then
    echo "  ✅ 全部检查通过"
    exit 0
else
    echo "  🔴 发现 $WARNINGS 类可疑模式，请在提交前检查"
    echo "  详细参考: ERRORS.md"
    echo "════════════════════════════════════════════════════════"
    exit 1
fi
