#!/usr/bin/env bash
set -euo pipefail

tool="${1:-java}"
if ! command -v "$tool" >/dev/null 2>&1; then
  echo "ERROR: $tool is not installed. Install a full JDK 21 or newer (not only a JRE)." >&2
  exit 1
fi

version_line="$($tool -version 2>&1 | head -n 1)"
major="$(printf '%s' "$version_line" | sed -E 's/^[^0-9]*([0-9]+).*/\1/')"
if ! [[ "$major" =~ ^[0-9]+$ ]] || (( major < 21 )); then
  echo "ERROR: JDK 21 or newer is required, but $tool reports: $version_line" >&2
  echo "Set JAVA_HOME to a JDK 21+ installation and place \$JAVA_HOME/bin first on PATH." >&2
  exit 1
fi
