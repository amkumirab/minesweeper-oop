#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"

mkdir -p out/tests
find src tests -name '*.java' -print0 | xargs -0 javac -Xlint:all -encoding UTF-8 -d out/tests
java -cp out/tests minesweeper.engine.RevealTests
java -cp out/tests minesweeper.PlayerTests
java -cp out/tests minesweeper.engine.TimeLimitTests
java -cp out/tests minesweeper.EncapsulationTests
java -cp out/tests minesweeper.ConsoleInputTests
java -cp out/tests minesweeper.engine.EngineLifecycleTests
