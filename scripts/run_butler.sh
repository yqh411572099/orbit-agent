#!/bin/bash
# Butler 服务启动包装（固定窗口标题：butler）
printf '\033]0;butler\007'

PORT=8080
echo "===== [$(date '+%H:%M:%S')] 启动 Butler ====="

# 1. 检查端口是否已被占用
EXISTING=$(lsof -nP -iTCP:$PORT -sTCP:LISTEN -t 2>/dev/null)
if [ -n "$EXISTING" ]; then
  echo "检测到端口 $PORT 已被 PID $EXISTING 占用，先停止旧进程..."
  kill -9 $EXISTING 2>/dev/null
  sleep 2
fi

# 2. 兜底清理残留 jar 进程
pkill -9 -f "butler-.*[.]jar" 2>/dev/null
sleep 1

# 3. 环境变量（API Key 等密钥放在 ~/.butler.env，不要提交到仓库）
export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.0.11+10/Contents/Home"
export PATH="$JAVA_HOME/bin:/usr/local/bin:$HOME/.homebrew/bin:$PATH"
[ -f "$HOME/.butler.env" ] && source "$HOME/.butler.env"

cd /Users/ma0000/project/butler
echo "等待 Milvus Lite (127.0.0.1:19531) 就绪..."
for i in $(seq 1 30); do
  if curl -s --max-time 2 http://127.0.0.1:19531/health >/dev/null 2>&1; then
    echo "Milvus Lite 已就绪。"
    break
  fi
  sleep 1
done

echo "启动 Butler：http://localhost:$PORT/"
exec java -jar "$(ls -t target/butler-*.jar | head -1)"
