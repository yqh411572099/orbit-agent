#!/bin/bash
# 一键启动 Butler 及其依赖（Milvus Lite 向量库）。
# 在两个固定标题的 Terminal 窗口中启动：milvus-lite / butler
exec "$(cd "$(dirname "$0")" && pwd)/scripts/start_all.sh"
