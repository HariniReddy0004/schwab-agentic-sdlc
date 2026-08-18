#!/usr/bin/env bash
# Zero-dependency build: compiles main sources with javac only (no Maven Central access needed).
set -euo pipefail
cd "$(dirname "$0")"
../verify-java21.sh javac
rm -rf target/classes
mkdir -p target/classes
find src/main/java -name "*.java" > target/.sources-main.txt
javac -Xlint:all --release 21 -d target/classes @target/.sources-main.txt
echo "Build OK -> target/classes"
