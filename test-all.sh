#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "$0")" && pwd)"

echo "[1/2] Testing URL shortener"
(cd "$root_dir/url-shortener" && ./test.sh)

echo
echo "[2/2] Testing agentic orchestrator"
(cd "$root_dir/orchestrator" && ./test.sh)

echo
echo "All 48 tests passed. The Java 21 submission is ready for review."
