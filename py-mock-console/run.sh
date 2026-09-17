#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "========================================================"
echo "🚀 正在启动 OrchestrAgent Mock Console & Data Provider..."
echo "========================================================"
echo "服务端地址: http://127.0.0.1:8000"
echo "Java 目标端: http://127.0.0.1:8080"
echo "========================================================"

python3 -m uvicorn app:app --host 0.0.0.0 --port 8000 --reload
