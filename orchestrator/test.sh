#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
../verify-java21.sh javac
rm -rf target/classes target/test-classes
mkdir -p target/classes target/test-classes
find src/main/java -name "*.java" > target/.sources-main.txt
find src/test/java -name "*.java" > target/.sources-test.txt
javac -Xlint:all --release 21 -d target/classes @target/.sources-main.txt
javac -Xlint:all --release 21 -cp target/classes -d target/test-classes @target/.sources-test.txt
java -cp target/classes:target/test-classes com.schwab.orchestrator.testing.TestRunnerMain
