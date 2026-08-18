#!/bin/bash
# Milvus Lite 桥接服务启动包装（固定窗口标题：milvus-lite）
printf '\033]0;milvus-lite\007'

PORT=19531
echo "===== [$(date '+%H:%M:%S')] 启动 Milvus Lite 桥接 ====="

# 1. 检查端口是否已被占用，若有则视为已在运行
EXISTING=$(lsof -nP -iTCP:$PORT -sTCP:LISTEN -t 2>/dev/null)
if [ -n "$EXISTING" ]; then
  echo "检测到端口 $PORT 已被 PID $EXISTING 占用，先停止旧进程..."
  kill -9 $EXISTING 2>/dev/null
  sleep 2
fi

# 2. 兜底清理可能残留的同名进程
pkill -9 -f "milvus/lite_server.py" 2>/dev/null
sleep 1

# 3. 启动
source ~/milvus/venv/bin/activate
export EMBEDDING_DIM=2048
echo "启动 HTTP 桥接：http://127.0.0.1:$PORT （数据 ~/milvus/data/butler_knowledge.db）"
exec python ~/milvus/lite_server.py
