#!/bin/bash
# 该脚本在 Terminal 窗口内前台运行服务（由 start_butler_window.sh 调用）
export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.0.11+10/Contents/Home"
export PATH="$JAVA_HOME/bin:/usr/local/bin:$PATH"
[ -f "$HOME/.butler.env" ] && source "$HOME/.butler.env"
printf '\033]0;butler\007'
cd /Users/ma0000/project/butler
java -jar target/butler-0.0.1-SNAPSHOT.jar 2>&1 | tee /tmp/butler.log
