#!/bin/bash
set -u
BASE="${1:-http://localhost:8080}/api"; U=9008
# 登录测试账号并让 curl 自动带 token
. "$(dirname "$0")/auth_test.sh"

PASS=0; FAIL=0
ok(){ echo "✅ $*"; PASS=$((PASS+1)); }
bad(){ echo "❌ $*"; FAIL=$((FAIL+1)); }

code=$(curl -s -o /dev/null -w '%{http_code}' "${BASE%/api}/" 2>/dev/null)
[ "$code" = "200" ] || { echo "服务未就绪"; exit 2; }
curl -s -X POST "$BASE/admin/purge-all" >/dev/null

echo "=== 创建孕期目标，验证最近一天全部展示 + 两个独立折叠 ==="
RESP=$(curl -s -X POST "$BASE/goals" -H 'Content-Type: application/json' -d "{
  \"userId\":$U,\"scenarioType\":\"pregnancy\",\"title\":\"孕期\",\"goal\":\"刚怀孕\",
  \"collected\":{\"role\":\"准爸爸（男方）\",\"dueDate\":\"2027-03-12\"},\"focusAreas\":[]}")
SUB=$(echo "$RESP" | sed -n 's/.*"subSessionId":\([0-9]*\).*/\1/p' | head -1)
TASKS=$(curl -s "$BASE/sub-sessions/$SUB/tasks")
echo "$TASKS" | python3 -c '
import json,sys
d=json.load(sys.stdin)
for g in d["groups"]:
    tasks=g.get("tasks",[])
    if not tasks: continue
    dates={t["dueDate"] for t in tasks if t.get("dueDate")}
    assert len(dates)==1, ("直接展示的任务必须同一天", g["label"], dates)
    assert "upcomingCollapsed" in g, g
    assert "history" in g, g
    for t in g["history"]:
        assert t["status"]=="OVERDUE", t
' && ok "每个关注项只展示最近一天全部任务，历史/后续独立折叠" || bad "分组结构错误"

curl -s -X POST "$BASE/admin/purge-all" >/dev/null
echo "==================="; echo "通过 $PASS  失败 $FAIL"
[ "$FAIL" -eq 0 ]
