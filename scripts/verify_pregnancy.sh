#!/bin/bash
# 孕期时间轴验证：刚性模块（孕检/建档/福利）+ 可选模块（待产包/月嫂/选病房）+ 提前量 + 知识详情。

BASE="${1:-http://localhost:8080}/api"; U=9003
# 登录测试账号并让 curl 自动带 token
. "$(dirname "$0")/auth_test.sh"

PASS=0; FAIL=0
ok(){ echo "✅ $*"; PASS=$((PASS+1)); }
bad(){ echo "❌ $*"; FAIL=$((FAIL+1)); }
check(){ echo "$1" | grep -q "$2" && ok "$3" || bad "$3"; }
sub_id_of(){ echo "$1" | sed -n 's/.*"subSessionId":\([0-9]*\).*/\1/p' | head -1; }

code=$(curl -s -o /dev/null -w '%{http_code}' "${BASE%/api}/" 2>/dev/null)
[ "$code" = "200" ] || { echo "❌ 服务未就绪 ($code)"; exit 2; }

echo "=== 0. 清脏数据 ==="; curl -s -X POST "$BASE/admin/purge-all" >/dev/null

echo "=== 1. 创建孕期目标（预产期 2027-03-12，准爸爸，勾选待产包/月嫂/选病房）==="
RESP=$(curl -s -X POST "$BASE/goals" -H 'Content-Type: application/json' -d "{
  \"userId\":$U,\"scenarioType\":\"pregnancy\",\"title\":\"孕期管家\",
  \"goal\":\"第一次怀孕，7月26日孕7周+1d\",
  \"collected\":{\"role\":\"准爸爸（男方）\",\"dueDate\":\"2027-03-12\",\"currentWeek\":\"7\"},
  \"focusAreas\":[\"birth_prep\",\"maternity_arrange\",\"hospital_choice\"]}")
SUB=$(sub_id_of "$RESP"); echo "subId=$SUB"; [ -n "$SUB" ] && ok "子对话已创建" || bad "子对话未创建"

TASKS=$(curl -s "$BASE/sub-sessions/$SUB/tasks")
echo "$TASKS" | head -c 1500; echo

echo "=== 2. 刚性模块必须存在（不依赖勾选）==="
check "$TASKS" "NT检查" "孕检：NT检查在时间轴上"
PRE_TOTAL=$(echo "$TASKS" | grep -o '"key":"prenatal_checkup","label":"孕检相关","total":[0-9]*' | grep -o '[0-9]*$')
echo "孕检组未完成任务数=$PRE_TOTAL（里程碑已合并为单任务双时间，不再拆准备/正式两条）"
[ "${PRE_TOTAL:-0}" -ge 7 ] && ok "孕检组生成了全部里程碑任务" || bad "孕检任务数不足($PRE_TOTAL)"
check "$TASKS" "完成建档" "建档任务在时间轴上"
check "$TASKS" "生育登记" "生育福利/报销任务在时间轴上"

echo "=== 3. 可选模块按勾选出现 ==="
check "$TASKS" "待产包" "勾选后出现待产包任务"
check "$TASKS" "月嫂" "勾选后出现月嫂/月子中心任务"
check "$TASKS" "生产医院与病房" "勾选后出现选病房任务"
NOT_FOCUS=$(echo "$TASKS" | grep -c "增重目标" || true)
[ "$NOT_FOCUS" = "0" ] && ok "未勾选的体重模块未生成任务" || bad "未勾选的体重模块不应生成"

echo "=== 4. 提前量：任务带 remindDate（提醒时间）早于 dueDate（执行时间）==="
check "$TASKS" '"remindDate":"' "任务返回 remindDate 提醒时间字段"

echo "=== 5. 任务带知识详情（detail）==="
check "$TASKS" '"detail":"' "任务返回 detail 字段"
check "$TASKS" "11~13" "NT详情含孕周窗口说明"
check "$TASKS" "空腹" "糖耐详情含空腹注意事项"
check "$TASKS" "医保" "生育福利详情提示以当地政策为准"

echo "=== 6. 日期锚定：NT任务 remindDate(10周) 应早于 dueDate(12周) ==="
read NT_REMIND NT_MAIN < <(echo "$TASKS" | python3 -c 'import sys,json
d=json.load(sys.stdin)
for g in d["groups"]:
  for t in (g.get("tasks",[])+g.get("upcomingCollapsed",[])+g.get("history",[])):
    if t["content"].startswith("NT检查"):
      print(t.get("remindDate") or "", t.get("dueDate") or ""); break' 2>/dev/null)
echo "NT提醒=$NT_REMIND  NT执行=$NT_MAIN"
[ -n "$NT_REMIND" ] && [ -n "$NT_MAIN" ] && [ "$NT_REMIND" \< "$NT_MAIN" ] && ok "提醒日期早于执行日期" || bad "双时间日期顺序错误"

echo "=== 7. 任务完成推进下一节点 ==="
TID=$(curl -s "$BASE/sub-sessions/$SUB/tasks" | python3 -c 'import sys,json
d=json.load(sys.stdin)
for g in d["groups"]:
  for t in (g.get("tasks", []) + g.get("upcomingCollapsed", []) + g.get("history", [])):
    if t.get("nextHint"):
      print(t["id"]); sys.exit(0)' 2>/dev/null)
echo "完成任务 id=$TID"
curl -s -X POST "$BASE/tasks/$TID/complete" >/dev/null
HIST=$(curl -s "$BASE/chat/history?userId=$U&sessionType=sub&subSessionId=$SUB")
echo "$HIST" | grep -q "下一步" && ok "完成后写入了下一步提示消息" || bad "未写入下一步提示"
echo "$HIST" | grep -q "已完成" && ok "完成消息含已完成任务标记" || bad "缺已完成标记"

echo "=== 8. 关注项依赖：勾选月子照护应自动带上新生儿护理、产后恢复 ==="
RESP2=$(curl -s -X POST "$BASE/goals" -H 'Content-Type: application/json' -d "{
  \"userId\":$U,\"scenarioType\":\"pregnancy\",\"title\":\"依赖测试\",\"goal\":\"刚怀孕\",
  \"collected\":{\"role\":\"准爸爸（男方）\",\"dueDate\":\"2027-03-12\"},
  \"focusAreas\":[\"maternity_arrange\"]}")
SUB2=$(echo "$RESP2" | sed -n 's/.*"subSessionId":\([0-9]*\).*/\1/p' | head -1)
TASKS2=$(curl -s "$BASE/sub-sessions/$SUB2/tasks")
echo "$TASKS2" | grep -q "月嫂" && ok "勾选月子照护生成了月嫂任务" || bad "缺月嫂任务"
echo "$TASKS2" | grep -q '"content":"[^"]*待产包' && bad "未勾选的待产包不应生成（依赖未指向它）" || ok "未勾选的待产包未生成"

echo ""; echo "==================="; echo "通过 $PASS  失败 $FAIL"
curl -s -X POST "$BASE/admin/purge-all" >/dev/null
[ "$FAIL" -eq 0 ]
