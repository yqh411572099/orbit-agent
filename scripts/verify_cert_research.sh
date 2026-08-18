#!/bin/bash
# 验证考证建目标前的联网调研+确认流程：
# 主对话发“想考公共营养师”应收到 goal_proposal；确认后创建子对话；
# 确认卡应含考期临近提示；子对话顶部应返回真实资料链接；待办日期不得早于今天。
set -u
BASE="http://localhost:8080"
TOKEN=$(curl -s -X POST "$BASE/api/users/login" -H 'Content-Type: application/json' \
  -d '{"username":"test","password":"test123"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
if [ -z "$TOKEN" ]; then echo "LOGIN FAIL"; exit 1; fi
echo "login ok"

echo "=== 发主对话：我想考公共营养师 ==="
RESP=$(curl -s -N -X POST "$BASE/api/chat/stream" -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"userId":1,"sessionType":"main","message":"我想考公共营养师"}' --max-time 200)
PID=$(echo "$RESP" | awk '/event:goal_proposal/{f=1} f&&/"proposalId"/{match($0,/"proposalId":"[^"]+"/); print substr($0,RSTART+14,RLENGTH-15); exit}')
if [ -z "$PID" ]; then echo "FAIL: no goal_proposal"; echo "$RESP" | tail -20; exit 1; fi
echo "proposalId=$PID"

# 考期临近应主动提示用户确认是否赶本考期，而不是默默跳过
echo "$RESP" | grep -q "是否赶这次" && echo "PASS: 考期临近提示" || echo "WARN: 未出现考期临近提示（若考期>60天可忽略）"

echo "=== 确认创建 ==="
CONF=$(curl -s -X POST "$BASE/api/goal-proposals/$PID/confirm" -H "Authorization: Bearer $TOKEN")
echo "$CONF" | grep -q '"subSessionId"' && echo "PASS: 子对话已创建" || { echo "FAIL: confirm"; exit 1; }
SID=$(echo "$CONF" | sed -n 's/.*"subSessionId":\([0-9]*\).*/\1/p')

echo "=== 资料链接 ==="
MAT=$(curl -s "$BASE/api/sub-sessions/$SID/materials" -H "Authorization: Bearer $TOKEN")
echo "$MAT" | grep -q '"url"' && echo "PASS: 有资料链接" || { echo "FAIL: 无资料链接 $MAT"; exit 1; }

echo "=== 待办日期不得早于今天 ==="
curl -s "$BASE/api/sub-sessions/$SID/tasks" -H "Authorization: Bearer $TOKEN" \
  | python3 -c '
import sys,json,datetime
d=json.load(sys.stdin)
today=datetime.date.today()
bad=[]
for g in d.get("groups",[]):
    for t in g.get("tasks",[])+g.get("upcomingCollapsed",[])+g.get("history",[]):
        du=t.get("dueDate")
        if du:
            try:
                if datetime.date.fromisoformat(du)<today: bad.append((du,t.get("content")))
            except: pass
if bad:
    print("FAIL 过期待办:",bad); sys.exit(1)
print("PASS: 无过期待办")
'
