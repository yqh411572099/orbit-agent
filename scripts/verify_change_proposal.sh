#!/bin/bash
# 验证：子对话消息产生“变更预览”(change_proposal SSE事件)，确认前不落库，确认后应用到头部字段/待办。
set -u
BASE="${1:-http://localhost:8080}/api"; U=9030
# 登录测试账号并让 curl 自动带 token
. "$(dirname "$0")/auth_test.sh"

PASS=0; FAIL=0
ok(){ echo "✅ $*"; PASS=$((PASS+1)); }
bad(){ echo "❌ $*"; FAIL=$((FAIL+1)); }

code=$(curl -s -o /dev/null -w '%{http_code}' "${BASE%/api}/" 2>/dev/null)
[ "$code" = "200" ] || { echo "服务未就绪($code)"; exit 2; }

RESP=$(curl -s -X POST "$BASE/goals" -H 'Content-Type: application/json' -d "{
  \"userId\":$U,\"scenarioType\":\"pregnancy\",\"title\":\"孕期\",\"goal\":\"刚怀孕\",
  \"collected\":{\"role\":\"准爸爸（男方）\",\"dueDate\":\"2027-03-13\"},\"focusAreas\":[]}")
SUB=$(echo "$RESP" | sed -n 's/.*"subSessionId":\([0-9]*\).*/\1/p' | head -1)
echo "SUB=$SUB"; [ -n "$SUB" ] || { echo "创建失败: $RESP"; exit 1; }

dueOf(){ curl -s "$BASE/sub-sessions/$SUB/tasks" | /usr/bin/python3 -c '
import sys,json
d=json.load(sys.stdin); t=sys.argv[1]
for g in d.get("groups",[]):
  for x in g.get("tasks",[])+g.get("upcomingCollapsed",[])+g.get("history",[]):
    if x.get("content")==t: print(x.get("dueDate","")); sys.exit(0)' "$1"; }

[ "$(dueOf 'NT检查（颈项透明层）')" = "2026-08-29" ] && ok "初始 NT=2026-08-29" || bad "初始 NT 异常: $(dueOf 'NT检查（颈项透明层）')"

# 捕获 SSE change_proposal 事件
EVENTS=$(mktemp)
curl -N -s -X POST "$BASE/chat/stream" -H 'Content-Type: application/json' \
  -d "{\"userId\":$U,\"sessionType\":\"sub\",\"subSessionId\":$SUB,\"message\":\"NT检查时间确定了，预约到8月20号\"}" > "$EVENTS" &
CURL_PID=$!
for i in $(seq 1 40); do
  PID=$(grep -A1 '^event:change_proposal' "$EVENTS" 2>/dev/null | grep '^data:' | head -1)
  [ -n "$PID" ] && break
  sleep 1
done
wait $CURL_PID 2>/dev/null
DATA=$(grep -A1 '^event:change_proposal' "$EVENTS" | grep '^data:' | head -1 | sed 's/^data://')
echo "$DATA" | head -c 400; echo
[ -n "$DATA" ] && ok "收到 change_proposal 事件" || bad "未收到 change_proposal"

# 确认前：NT 仍为旧日期
[ "$(dueOf 'NT检查（颈项透明层）')" = "2026-08-29" ] && ok "确认前未落库(NT 仍为 08-29)" || bad "确认前就落库了: $(dueOf 'NT检查（颈项透明层）')"

PROPOSAL_ID=$(echo "$DATA" | /usr/bin/python3 -c 'import sys,json; print(json.load(sys.stdin).get("proposalId",""))' 2>/dev/null)
echo "PROPOSAL_ID=$PROPOSAL_ID"
echo "$DATA" | grep -q "milestone_nt_date" && ok "预览包含 NT 预约字段变更" || bad "预览缺字段"
echo "$DATA" | grep -q "2026-08-20" && ok "预览包含新日期 2026-08-20" || bad "预览缺新日期"

# 确认应用
curl -s -X POST "$BASE/sub-sessions/$SUB/proposals/$PROPOSAL_ID/apply" >/dev/null
[ "$(dueOf 'NT检查（颈项透明层）')" = "2026-08-20" ] && ok "确认后 NT 重排到 2026-08-20" || bad "确认后未生效: $(dueOf 'NT检查（颈项透明层）')"
[ "$(dueOf '大排畸（系统超声/三维四维）')" = "2026-11-07" ] && ok "大排畸保持 2026-11-07" || bad "大排畸被影响: $(dueOf '大排畸（系统超声/三维四维）')"

rm -f "$EVENTS"
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
