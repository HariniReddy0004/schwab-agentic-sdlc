#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./build.sh
echo "Starting orchestrator on port ${PORT:-8080}..."
java -cp target/classes com.schwab.orchestrator.OrchestratorApp
