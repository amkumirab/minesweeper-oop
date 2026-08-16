@echo off
setlocal

if not exist "out\minesweeper\Main.class" (
    echo No compiled classes found. Run compile.bat first.
    exit /b 1
)

chcp 65001 >nul
java -Dfile.encoding=UTF-8 -cp "out" minesweeper.Main %*
