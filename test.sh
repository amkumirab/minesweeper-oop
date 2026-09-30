#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"

mkdir -p out/tests
find src tests -name '*.java' -print0 | xargs -0 javac -Xlint:all -encoding UTF-8 -d out/tests
java -cp out/tests minesweeper.RevealTests
