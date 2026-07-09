#!/bin/bash
# safe_build.sh — 先扫描反模式，通过后才构建
# 用法: ./safe_build.sh

set -e
JAVA_HOME="E:/androidstudio/jbr"
GRADLE="C:/Users/ALIENWARE/.gradle/wrapper/dists/gradle-8.5-bin/5t9huq95ubn472n8rpzujfbqh/gradle-8.5/bin/gradle"
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "══════════════════════════════════════"
echo "  Step 1/3: 扫描已知反模式"
echo "══════════════════════════════════════"
bash "$PROJECT_DIR/pre-check.sh"
echo ""

echo "══════════════════════════════════════"
echo "  Step 2/3: 单元测试"
echo "══════════════════════════════════════"
export JAVA_HOME="$JAVA_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
"$GRADLE" -p "$PROJECT_DIR" :domain:test --console=plain 2>&1 | tail -5
echo ""

echo "══════════════════════════════════════"
echo "  Step 3/3: 构建 APK"
echo "══════════════════════════════════════"
"$GRADLE" -p "$PROJECT_DIR" assembleDebug --console=plain 2>&1 | tail -5
echo ""

echo "✅ 全部通过"
