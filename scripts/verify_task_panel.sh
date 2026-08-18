#!/bin/bash
set -u
BASE="${1:-http://localhost:8080}/api"; U=9006
# 登录测试账号并让 curl 自动带 token
. "$(dirname "$0")/auth_test.sh"

PASS=0; FAIL=0
ok(){ echo "✅ $*"; PASS=$((PASS+1)); }
bad(){ echo "❌ $*"; FAIL=$((FAIL+1)); }

code=$(curl -s -o /dev/null -w '%{http_code}' "${BASE%/api}/" 2>/dev/null)
[ "$code" = "200" ] || { echo "❌ 服务未就绪 ($code)"; exit 2; }
curl -s -X POST "$BASE/admin/purge-all" >/dev/null

echo "=== 创建过期考试目标，验证任务面板契约 ==="
RESP=$(curl -s -X POST "$BASE/goals" -H 'Content-Type: application/json' -d "{
  \"userId\":$U,\"scenarioType\":\"cert_prep\",\"title\":\"过期证书\",\"goal\":\"备考过期证书\",
  \"collected\":{\"certName\":\"OLD\",\"examDate\":\"2026-01-01\"},\"focusAreas\":[]}")
SUB=$(echo "$RESP" | sed -n 's/.*"subSessionId":\([0-9]*\).*/\1/p' | head -1)
TASKS=$(curl -s "$BASE/sub-sessions/$SUB/tasks")
VISIBLE=$(echo "$TASKS" | sed -n 's/.*"tasks":\[\([^]]*\)\].*/\1/p')
echo "$TASKS" | grep -q '"overdueCount":5' && ok "5 个过期任务未默认完成并计数" || bad "过期计数错误"
echo "$TASKS" | grep -q '"history":\[' && ok "过期任务进入 history" || bad "缺少 history"
echo "$TASKS" | grep -q '"upcomingCollapsed":\[' && ok "后续任务进入 upcomingCollapsed" || bad "缺少 upcomingCollapsed"
echo "$VISIBLE" | grep -q '确认报名时间' && ok "最近一个待办直接展示" || bad "最近一个待办未展示"
echo "$TASKS" | grep -q '"completed":false' && ok "过期任务保持未完成" || bad "过期任务被默认完成"
echo "$TASKS" | grep -q '"dueDate":"2025-09-03"' && ok "跨年日期带年份并正确排序/推算" || bad "跨年日期错误"

curl -s -X POST "$BASE/admin/purge-all" >/dev/null
echo "==================="; echo "通过 $PASS  失败 $FAIL"
[ "$FAIL" -eq 0 ]
