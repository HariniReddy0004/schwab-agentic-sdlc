#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./build.sh
echo "Starting url-shortener on port ${PORT:-8081}..."
java -cp target/classes com.schwab.urlshortener.UrlShortenerApp
