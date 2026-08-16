#!/bin/bash
# ─────────────────────────────────────────────
#  Runs the Minesweeper game
#  Usage:  ./run.sh
# ─────────────────────────────────────────────

OUT_DIR="out"

if [ ! -d "$OUT_DIR" ]; then
  echo "⚠  No compiled classes found. Run ./compile.sh first."
  exit 1
fi

java -cp "$OUT_DIR" minesweeper.Main "$@"
