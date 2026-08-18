#!/bin/bash
# 验证：创建后增删关注项 + 预产期变更重排时间轴
set -u
BASE="${1:-http://localhost:8080}/api"; U=9004
# 登录测试账号并让 curl 自动带 token
. "$(dirname "$0")/auth_test.sh"

PASS=0; FAIL=0
ok(){ echo "✅ $*"; PASS=$((PASS+1)); }
bad(){ echo "❌ $*"; FAIL=$((FAIL+1)); }

code=$(curl -s -o /dev/null -w '%{http_code}' "${BASE%/api}/" 2>/dev/null)
[ "$code" = "200" ] || { echo "服务未就绪($code)"; exit 2; }
curl -s -X POST "$BASE/admin/purge-all" >/dev/null

echo "=== 1. 创建孕期目标（仅默认关注项，预产期2027-03-12）==="
RESP=$(curl -s -X POST "$BASE/goals" -H 'Content-Type: application/json' -d "{
  \"userId\":$U,\"scenarioType\":\"pregnancy\",\"title\":\"孕期\",\"goal\":\"刚怀孕\",
  \"collected\":{\"role\":\"准爸爸（男方）\",\"dueDate\":\"2027-03-12\"},\"focusAreas\":[]}")
SUB=$(echo "$RESP" | sed -n 's/.*"subSessionId":\([0-9]*\).*/\1/p' | head -1)
echo "SUB=$SUB"
NT_BEFORE=$(curl -s "$BASE/sub-sessions/$SUB/tasks" | tr '}' '\n' | grep '"content":"NT检查（颈项透明层）"' | grep -o '"dueDate":"[0-9-]*"' | head -1)
echo "变更前 NT 日期: $NT_BEFORE"
echo "$NT_BEFORE" | grep -q "2026-08-28" && ok "NT 初始日期正确(12周)" || bad "NT 初始日期异常"

echo "=== 2. 通过接口新增关注项：体重管理、月嫂照护 ==="
curl -s -X POST "$BASE/sub-sessions/$SUB/focus" -H 'Content-Type: application/json' \
  -d '{"focusAreas":["prenatal_checkup","filing","accompany_checkup","weight","maternity_arrange"]}' >/dev/null
TASKS=$(curl -s "$BASE/sub-sessions/$SUB/tasks")
echo "$TASKS" | grep -q "增重目标" && ok "新增体重模块生成了任务" || bad "体重模块任务未生成"
echo "$TASKS" | grep -q "月嫂" && ok "新增月嫂模块生成了任务" || bad "月嫂模块任务未生成"

echo "=== 3. 关闭关注项：移除体重 ==="
curl -s -X POST "$BASE/sub-sessions/$SUB/focus" -H 'Content-Type: application/json' \
  -d '{"focusAreas":["prenatal_checkup","filing","accompany_checkup","maternity_arrange"]}' >/dev/null
TASKS2=$(curl -s "$BASE/sub-sessions/$SUB/tasks")
echo "$TASKS2" | grep -q "增重目标" && bad "体重任务应被移除" || ok "关闭后体重任务已移除"
echo "$TASKS2" | grep -q "月嫂" && ok "月嫂任务仍保留" || bad "月嫂任务被误删"

echo "=== 4. 预产期变更（对话触发重排）：改为2027-05-01 ==="
curl -s -X POST "$BASE/messages" -H 'Content-Type: application/json' \
  -d "{\"userId\":$U,\"sessionType\":\"sub\",\"subSessionId\":$SUB,\"content\":\"医生说预产期改到2027年5月1日，今天是8月5日，帮我重排一下产检时间\"}" >/dev/null
sleep 8
FIRST_AFTER=$(curl -s "$BASE/sub-sessions/$SUB/tasks" | python3 -c 'import sys,json
d=json.load(sys.stdin)
for g in d["groups"]:
  for t in (g.get("tasks",[])+g.get("upcomingCollapsed",[])+g.get("history",[])):
    if t["content"].startswith("早孕B超"):
      print(t.get("remindDate") or t.get("dueDate") or ""); break' 2>/dev/null)
echo "变更后早孕B超提醒日期: $FIRST_AFTER"
HIST=$(curl -s "$BASE/chat/history?userId=$U&sessionType=sub&subSessionId=$SUB")
echo "$FIRST_AFTER" | grep -q "2026-09-05" && ok "时间轴提醒日期随预产期重排到新日期" || bad "时间轴日期未重排($FIRST_AFTER)"
echo "$HIST" | grep -q "重排\|新增\|移除\|变更" && ok "对话中给出了重排反馈" || bad "缺少重排反馈消息"

echo "=== 5. 预产期变更同步到长期记忆 pregnancy.profile ==="
MEM=$(curl -s "$BASE/memories?userId=$U&sessionType=sub&subSessionId=$SUB")
echo "$MEM" | grep -q '"type":"pregnancy.profile"' && ok "存在 pregnancy.profile 记忆" || bad "缺 profile 记忆"
echo "$MEM" | grep -q '"dueDate":"2027-05-01"' && ok "记忆 dueDate 已更新为新预产期" || bad "记忆 dueDate 未更新"


echo ""; echo "==================="; echo "通过 $PASS  失败 $FAIL"
curl -s -X POST "$BASE/admin/purge-all" >/dev/null
[ "$FAIL" -eq 0 ]
