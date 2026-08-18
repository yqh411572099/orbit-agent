#!/bin/bash
# 测试脚本统一登录测试账号 test，并让后续 curl 自动带上会话 token。
# 用法：在设置 BASE（形如 .../api）后 source 本文件。
_BASE_NOAPI="${BASE%/api}"
_AUTH_RESP=$(command curl -s -X POST "$_BASE_NOAPI/api/users/login" \
  -H 'Content-Type: application/json' -d '{"username":"test","password":"test123"}')
export TEST_TOKEN=$(printf '%s' "$_AUTH_RESP" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
export U=$(printf '%s' "$_AUTH_RESP" | sed -n 's/.*"id":\([0-9]*\).*/\1/p' | head -1)
if [ -z "$TEST_TOKEN" ] || [ -z "$U" ]; then
  echo "❌ 测试账号登录失败: $_AUTH_RESP" >&2
  return 1 2>/dev/null || exit 1
fi
curl() { command curl -H "Authorization: Bearer $TEST_TOKEN" "$@"; }
export -f curl
