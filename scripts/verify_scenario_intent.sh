#!/bin/bash
set -u
BASE="${1:-http://localhost:8080}/api"; U=9005
# 登录测试账号并让 curl 自动带 token
. "$(dirname "$0")/auth_test.sh"

PASS=0; FAIL=0
ok(){ echo "✅ $*"; PASS=$((PASS+1)); }
bad(){ echo "❌ $*"; FAIL=$((FAIL+1)); }

code=$(curl -s -o /dev/null -w '%{http_code}' "${BASE%/api}/" 2>/dev/null)
[ "$code" = "200" ] || { echo "❌ 服务未就绪 ($code)"; exit 2; }
curl -s -X POST "$BASE/admin/purge-all" >/dev/null

echo "=== 1. 创建考证目标（考试日 2027-03-01）==="
RESP=$(curl -s -X POST "$BASE/goals" -H 'Content-Type: application/json' -d "{
  \"userId\":$U,\"scenarioType\":\"cert_prep\",\"title\":\"PMP备考\",\"goal\":\"备考PMP\",
  \"collected\":{\"certName\":\"PMP\",\"examDate\":\"2027-03-01\"},\"focusAreas\":[]}")
SUB=$(echo "$RESP" | sed -n 's/.*"subSessionId":\([0-9]*\).*/\1/p' | head -1)
echo "SUB=$SUB"
[ -n "$SUB" ] && ok "考证子对话已创建" || bad "考证子对话未创建"

TASKS=$(curl -s "$BASE/sub-sessions/$SUB/tasks")
echo "$TASKS" | grep -q "参加证书考试" && ok "考证确定性时间轴已生成" || bad "缺考证时间轴任务"
echo "$TASKS" | tr '}' '\n' | grep "参加证书考试" | grep -q '"dueDate":"2027-03-01"' && ok "考试日主任务日期正确" || bad "考试日主任务日期错误"
echo "$TASKS" | tr '}' '\n' | grep "确认报名时间、考试大纲与资料" | grep -q '"dueDate":"2026-11-01"' && ok "倒排任务日期正确" || bad "倒排任务日期错误"

echo "=== 2. 对话变更考试日：2027-04-01 ==="
curl -s -X POST "$BASE/messages" -H 'Content-Type: application/json' \
  -d "{\"userId\":$U,\"sessionType\":\"sub\",\"subSessionId\":$SUB,\"content\":\"PMP考试时间确认改到2027年4月1日，请调整后面的复习和模考安排\"}" >/dev/null
sleep 8

TASKS2=$(curl -s "$BASE/sub-sessions/$SUB/tasks")
echo "$TASKS2" | tr '}' '\n' | grep "参加证书考试" | grep -q '"dueDate":"2027-04-01"' && ok "对话后主考试日期已重排" || bad "主考试日期未重排"
echo "$TASKS2" | tr '}' '\n' | grep "确认报名时间、考试大纲与资料" | grep -q '"dueDate":"2026-12-02"' && ok "倒排任务随考试日重排" || bad "倒排任务未重排"

MEM=$(curl -s "$BASE/memories?userId=$U&sessionType=sub&subSessionId=$SUB")
echo "$MEM" | grep -q '"type":"cert.info"' && ok "存在 cert.info 结构化记忆" || bad "缺 cert.info 记忆"
echo "$MEM" | grep -q '"examDate":"2027-04-01"' && ok "结构化记忆已更新考试日" || bad "结构化记忆未更新考试日"

echo "=== 3. 创建考研目标（考试日 2026-12-20）也走同一套时间轴抽象 ==="
RESP2=$(curl -s -X POST "$BASE/goals" -H 'Content-Type: application/json' -d "{
  \"userId\":$U,\"scenarioType\":\"exam_prep\",\"title\":\"考研计划\",\"goal\":\"备考计算机研究生\",
  \"collected\":{\"targetSchool\":\"清华大学 计算机\",\"examDate\":\"2026-12-20\"},\"focusAreas\":[]}")
SUB2=$(echo "$RESP2" | sed -n 's/.*"subSessionId":\([0-9]*\).*/\1/p' | head -1)
TASKS3=$(curl -s "$BASE/sub-sessions/$SUB2/tasks")
echo "$TASKS3" | grep -q "参加研究生考试" && ok "考研复用通用时间轴" || bad "考研未生成时间轴"
echo "$TASKS3" | tr '}' '\n' | grep "参加研究生考试" | grep -q '"dueDate":"2026-12-20"' && ok "考研考试日期正确" || bad "考研考试日期错误"

echo ""; echo "==================="; echo "通过 $PASS  失败 $FAIL"
curl -s -X POST "$BASE/admin/purge-all" >/dev/null
[ "$FAIL" -eq 0 ]
