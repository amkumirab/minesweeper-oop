#!/bin/bash
# ─────────────────────────────────────────────
#  Compiles all Minesweeper source files
#  Usage:  ./compile.sh
# ─────────────────────────────────────────────

set -e

SRC_DIR="src"
OUT_DIR="out"

echo "🔨 Compiling Minesweeper..."
mkdir -p "$OUT_DIR"

# Find all .java files and compile them together
find "$SRC_DIR" -name "*.java" | xargs javac -encoding UTF-8 -d "$OUT_DIR" -sourcepath "$SRC_DIR"

echo "✅ Compilation successful! Run with:  ./run.sh"
