#!/bin/bash
# 验证：用户说“NT检查时间确定了，预约到8月20号”后，NT里程碑待办日期被重排，
# 而其他里程碑日期（大排畸/早孕B超）保持不变（通用里程碑覆盖机制）。
set -u
BASE="${1:-http://localhost:8080}/api"; U=9023
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
data=json.load(sys.stdin); target=sys.argv[1]
for g in data.get("groups",[]):
    for t in g.get("tasks",[])+g.get("upcomingCollapsed",[])+g.get("history",[]):
        if t.get("content")==target:
            print(t.get("dueDate","")); sys.exit(0)
' "$1"; }

[ "$(dueOf 'NT检查（颈项透明层）')" = "2026-08-29" ] && ok "NT 初始=2026-08-29(12周)" || bad "NT 初始异常: $(dueOf 'NT检查（颈项透明层）')"

curl -s -X POST "$BASE/messages" -H 'Content-Type: application/json' \
  -d "{\"userId\":$U,\"sessionType\":\"sub\",\"subSessionId\":$SUB,\"content\":\"NT检查时间确定了，预约到8月20号\"}" >/dev/null
sleep 12

[ "$(dueOf 'NT检查（颈项透明层）')" = "2026-08-20" ] && ok "NT 已重排到 2026-08-20" || bad "NT 未重排: $(dueOf 'NT检查（颈项透明层）')"
[ "$(dueOf '大排畸（系统超声/三维四维）')" = "2026-11-07" ] && ok "大排畸保持 2026-11-07" || bad "大排畸被影响: $(dueOf '大排畸（系统超声/三维四维）')"
[ "$(dueOf '早孕B超：确认宫内孕、胎心胎芽')" = "2026-08-01" ] && ok "早孕B超保持 2026-08-01" || bad "早孕B超被影响: $(dueOf '早孕B超：确认宫内孕、胎心胎芽')"

echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
