#!/usr/bin/env bash
# Build + test with nothing but a JDK (17+). No Maven/Gradle needed.
set -euo pipefail
cd "$(dirname "$0")/.."
rm -rf out && mkdir -p out build
javac -d out src/main/java/com/frauddetect/*.java src/test/java/com/frauddetect/*.java
java -cp out com.frauddetect.TestRunner
rm -rf out/com/frauddetect/TestRunner*.class
printf 'Main-Class: com.frauddetect.Main\n' > build/manifest.txt
jar cfm build/fraud-ring-investigator.jar build/manifest.txt -C out .
echo "Built build/fraud-ring-investigator.jar"
