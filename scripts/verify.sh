#!/bin/bash
# 属性化记忆端到端验证：三场景结构化属性 + 未知 type 透传 + 有效期。
# 依赖：服务已启动（默认 http://localhost:8080）。会调用 purge-all 清空业务数据。
# 用法：scripts/verify.sh [BASE_URL]
set -u
BASE="${1:-http://localhost:8080}/api"; U=9002
# 登录测试账号并让 curl 自动带 token
. "$(dirname "$0")/auth_test.sh"

PASS=0; FAIL=0
ok(){ echo "✅ $*"; PASS=$((PASS+1)); }
bad(){ echo "❌ $*"; FAIL=$((FAIL+1)); }
check(){ echo "$1" | grep -q "$2" && ok "$3" || bad "$3"; }
sub_id_of(){ echo "$1" | sed -n 's/.*"subSessionId":\([0-9]*\).*/\1/p' | head -1; }

code=$(curl -s -o /dev/null -w '%{http_code}' "${BASE%/api}/" 2>/dev/null)
if [ "$code" != "200" ]; then echo "❌ 服务未就绪（${BASE%/api}/ 返回 $code），请先启动服务"; exit 2; fi

echo "=== 0. 清脏数据 ==="; curl -s -X POST "$BASE/admin/purge-all" >/dev/null

create_goal(){
  curl -s -X POST "$BASE/goals" -H 'Content-Type: application/json' \
    -d "{\"userId\":$U,\"scenarioType\":\"$1\",\"title\":\"$2\",\"goal\":\"$3\",\"collected\":$4,\"focusAreas\":$5}"
}
sub_mem(){ curl -s "$BASE/memories?userId=$U&sessionType=sub&subSessionId=$1"; }

echo "=== 1. 孕期：pregnancy.profile + subjectProfile ==="
PREG=$(create_goal pregnancy "孕期管家" "第一次怀孕，7月26日孕7周+1d" \
  '{"role":"准爸爸（男方）","dueDate":"2027-03-12","currentWeek":"7","hospital":"北京妇产医院"}' \
  '["prenatal_checkup","weight_management"]')
check "$PREG" '"scenarioType":"pregnancy"' "孕期子对话已创建"
SUB_P=$(sub_id_of "$PREG"); echo "subId=$SUB_P"
MEM_P=$(sub_mem "$SUB_P")
check "$MEM_P" '"type":"pregnancy.profile"' "含 pregnancy.profile 类型标识"
# 收集信息里同时给了 dueDate 与孕周锚点（7/26 孕7周+1d），按孕周确定性换算得到 2027-03-13，优先于手填日期。
check "$MEM_P" '"dueDate":"2027-03-13"' "预产期按孕周锚点换算正确"
check "$MEM_P" '"gestationalWeek":7' "孕周正确"
check "$MEM_P" '"hospital":"北京妇产医院"' "医院正确"
check "$MEM_P" '\\"role\\":\\"准爸爸（男方）\\"' "主体画像角色"
check "$MEM_P" '\\"relatedParty\\":\\"孕妇\\"' "relatedParty"

echo "=== 2. 考研：exam.target + exam.subject ==="
EXAM=$(create_goal exam_prep "考研计划" "备考计算机研究生" \
  '{"targetSchool":"清华大学 计算机科学与技术","examDate":"2026-12-20","subjects":"数学、英语、数据结构","currentLevel":"英语四级水平"}' '[]')
check "$EXAM" '"scenarioType":"exam_prep"' "考研子对话已创建"
MEM_E=$(sub_mem "$(sub_id_of "$EXAM")")
check "$MEM_E" '"type":"exam.target"' "含 exam.target 类型标识"
check "$MEM_E" '"school":"清华大学 计算机科学与技术"' "院校专业正确"
check "$MEM_E" '"examDate":"2026-12-20"' "考试日期正确"
check "$MEM_E" '"type":"exam.subject"' "含 exam.subject 类型标识"
check "$MEM_E" '"level":"英语四级水平"' "科目基础正确"

echo "=== 3. 考证：cert.info ==="
CERT=$(create_goal cert_prep "PMP备考" "备考PMP" \
  '{"certName":"PMP","examDate":"2026-11-30"}' '[]')
check "$CERT" '"scenarioType":"cert_prep"' "考证子对话已创建"
MEM_C=$(sub_mem "$(sub_id_of "$CERT")")
check "$MEM_C" '"type":"cert.info"' "含 cert.info 类型标识"
check "$MEM_C" '"name":"PMP"' "证书名正确"
check "$MEM_C" '"examDate":"2026-11-30"' "考试日期正确"

echo "=== 4. 对话提炼：未知 type 透传 + 有效期 ==="
curl -s -X POST "$BASE/messages" -H 'Content-Type: application/json' \
  -d "{\"userId\":$U,\"sessionType\":\"main\",\"content\":\"我对青霉素过敏，最近两周在备考冲刺，每天学习8小时\"}" >/dev/null
N=$(curl -s -X POST "$BASE/admin/extract/$U"); echo "提炼条数=$N"
MEM_MAIN=$(curl -s "$BASE/memories?userId=$U&sessionType=main")
check "$MEM_MAIN" '"type":"allergy"' "未知属性类型 allergy 原样透传"
check "$MEM_MAIN" '"validTo":"' "临时上下文含有效期 validTo"

echo ""; echo "==================="; echo "通过 $PASS  失败 $FAIL"
[ "$FAIL" -eq 0 ]
