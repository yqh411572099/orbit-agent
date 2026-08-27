#!/bin/bash
# Start milvus-lite and butler in fixed-title Terminal windows.
# Reuses existing windows on restart (stops old process, closes stale idle tabs); creates if missing.
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

osascript <<APPLESCRIPT
on runInWindow(cmdPath, titleText, killPattern)
  tell application "Terminal"
    do shell script "pkill -9 -f '" & killPattern & "' 2>/dev/null || true"
    delay 2
    set matches to {}
    repeat with w in windows
      if (name of w) contains titleText then
        set end of matches to w
      end if
    end repeat
    if (count of matches) = 0 then
      set theTab to do script cmdPath
      set custom title of theTab to titleText
    else
      -- first close other same-title leftover windows (idle), from back to front
      if (count of matches) > 1 then
        repeat with i from (count of matches) to 2 by -1
          try
            close item i of matches
          end try
        end repeat
        delay 1
      end if
      set targetWin to item 1 of matches
      set theTab to do script cmdPath in targetWin
      set custom title of theTab to titleText
      -- close stale idle tabs left in the reused window
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
end runInWindow

runInWindow("$SCRIPT_DIR/run_milvus_lite.sh", "milvus-lite", "milvus/lite_server.py")
delay 1
runInWindow("$SCRIPT_DIR/run_butler.sh", "butler", "butler-.*[.]jar")
APPLESCRIPT

echo "Starting services in fixed windows, waiting..."
for i in $(seq 1 60); do
  M=$(curl -s --max-time 2 http://127.0.0.1:19531/health 2>/dev/null || true)
  B=$(curl -s --max-time 2 -o /dev/null -w '%{http_code}' http://localhost:8080/ 2>/dev/null || true)
  if echo "$M" | grep -qE '"ok"[[:space:]]*:[[:space:]]*true' && [ "$B" = "200" ]; then
    echo "Milvus Lite ready (127.0.0.1:19531)"
    echo "Butler ready (http://localhost:8080/)"
    exit 0
  fi
  sleep 1
done
echo "Timeout; check window logs."
exit 1
