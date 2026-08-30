#!/bin/bash
# 在标题为 "butler" 的固定 Terminal 窗口中（重）启动服务：
# 先停旧进程，再按窗口标题定位到 butler 窗口（绝不复用 milvus/其他窗口），无则新建。
TERM_SCRIPT="/Users/ma0000/project/butler/scripts/butler_in_term.sh"
TITLE="butler"

# 1) 从外部停止旧进程，让前台运行 java 的标签回到 shell 提示符
pkill -9 -f "butler-.*[.]jar" 2>/dev/null
for i in $(seq 1 10); do
  pgrep -f "butler-.*[.]jar" >/dev/null || break
  sleep 0.5
done

# 2) 按标题定位 butler 窗口：找到就在该窗口开 tab，找不到才新建窗口
osascript <<APPLESCRIPT
tell application "Terminal"
    activate
    set matches to {}
    repeat with w in windows
      if (name of w) contains "$TITLE" then
        set end of matches to w
      end if
    end repeat
    if (count of matches) = 0 then
      set theTab to do script "$TERM_SCRIPT"
      set custom title of theTab to "$TITLE"
    else
      set targetWin to item 1 of matches
      set theTab to do script "$TERM_SCRIPT" in targetWin
      set custom title of theTab to "$TITLE"
      -- 关闭复用窗口里残留的空闲 tab
      delay 1
      set n to count of tabs of targetWin
      repeat with i from n to 1 by -1
        set tb to tab i of targetWin
        if tb is not theTab and (busy of tb) is false then
          try
            close tb
          end try
        end if
      end repeat
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
