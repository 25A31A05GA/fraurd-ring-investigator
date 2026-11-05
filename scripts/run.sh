#!/usr/bin/env bash
# One command: compile, run all tests, then run a demo.   ./scripts/run.sh [demo|bench|farm|serve] [--options]
set -euo pipefail
cd "$(dirname "$0")/.."
./scripts/build.sh
java -jar build/fraud-ring-investigator.jar "${@:-demo}"
