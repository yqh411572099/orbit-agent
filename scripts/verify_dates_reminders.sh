#!/bin/bash
set -u
BASE="${1:-http://localhost:8080}/api"; U=9007
# 登录测试账号并让 curl 自动带 token
. "$(dirname "$0")/auth_test.sh"

PASS=0; FAIL=0
ok(){ echo "✅ $*"; PASS=$((PASS+1)); }
bad(){ echo "❌ $*"; FAIL=$((FAIL+1)); }

code=$(curl -s -o /dev/null -w '%{http_code}' "${BASE%/api}/" 2>/dev/null)
[ "$code" = "200" ] || { echo "服务未就绪"; exit 2; }
curl -s -X POST "$BASE/admin/purge-all" >/dev/null

echo "=== 孕周日期换算预产期 ==="
RESP=$(curl -s -X POST "$BASE/goals" -H 'Content-Type: application/json' -d "{
  \"userId\":$U,\"scenarioType\":\"pregnancy\",\"title\":\"孕期\",\"goal\":\"7月26日是孕7周+1d，下次复查B超时间是8月7日\",
  \"collected\":{\"role\":\"准爸爸（男方）\"},\"focusAreas\":[]}")
SUB=$(echo "$RESP" | sed -n 's/.*"subSessionId":\([0-9]*\).*/\1/p' | head -1)
MEM=$(curl -s "$BASE/memories?userId=$U&sessionType=sub&subSessionId=$SUB")
echo "$MEM" | grep -q '"dueDate":"2027-03-13"' && ok "按孕周确定性换算预产期为 2027-03-13" || bad "预产期换算错误"

echo "=== 过期任务不会在刷新/触发提醒时刷进聊天 ==="
RESP2=$(curl -s -X POST "$BASE/goals" -H 'Content-Type: application/json' -d "{
  \"userId\":$U,\"scenarioType\":\"cert_prep\",\"title\":\"旧考试\",\"goal\":\"旧考试\",
  \"collected\":{\"certName\":\"OLD\",\"examDate\":\"2026-01-01\"},\"focusAreas\":[]}")
SUB2=$(echo "$RESP2" | sed -n 's/.*"subSessionId":\([0-9]*\).*/\1/p' | head -1)
BEFORE=$(curl -s "$BASE/chat/history?userId=$U&sessionType=sub&subSessionId=$SUB2" | grep -c "待办提醒" || true)
curl -s -X POST "$BASE/admin/reminders/run" >/dev/null
AFTER=$(curl -s "$BASE/chat/history?userId=$U&sessionType=sub&subSessionId=$SUB2" | grep -c "待办提醒" || true)
[ "$BEFORE" = "$AFTER" ] && ok "过期任务不会自动推送聊天提醒" || bad "过期任务被推送进聊天($BEFORE->$AFTER)"

TASKS=$(curl -s "$BASE/sub-sessions/$SUB2/tasks")
echo "$TASKS" | grep -q '"status":"OVERDUE"' && ok "右侧过期任务状态为 OVERDUE/待确认" || bad "缺 OVERDUE 状态"
echo "$TASKS" | grep -q '"completed":false' && ok "过期任务未默认完成" || bad "过期任务被默认完成"

curl -s -X POST "$BASE/admin/purge-all" >/dev/null
echo "==================="; echo "通过 $PASS  失败 $FAIL"
[ "$FAIL" -eq 0 ]
