#!/bin/bash
# 在单一 Terminal 窗口中（重）启动服务：先停旧进程，等窗口回到提示符，再在同一窗口启动。
TERM_SCRIPT="/Users/ma0000/project/butler/scripts/butler_in_term.sh"

# 1) 从外部停止旧进程，让前台运行 java 的标签回到 shell 提示符
pkill -9 -f "butler-.*[.]jar" 2>/dev/null
for i in $(seq 1 10); do
  pgrep -f "butler-.*[.]jar" >/dev/null || break
  sleep 0.5
done

# 2) 在同一窗口（无窗口则新建）执行启动脚本
osascript <<APPLESCRIPT
tell application "Terminal"
    activate
    if (count of windows) = 0 then
        do script "$TERM_SCRIPT"
    else
        do script "$TERM_SCRIPT" in front window
    end if
end tell
APPLESCRIPT

# 3) 等待新进程就绪
for i in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/ 2>/dev/null || true)
  if [ "$code" = "200" ]; then echo "UP after ${i}s"; exit 0; fi
  sleep 1
done
echo "timeout last=$code"
