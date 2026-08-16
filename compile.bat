@echo off
setlocal

where javac >nul 2>nul
if errorlevel 1 (
    echo Java compiler not found. Install JDK 17 or newer and add it to PATH.
    exit /b 1
)

if not exist "out" mkdir "out"

echo Compiling Minesweeper...
javac -encoding UTF-8 -d "out" -sourcepath "src" "src\minesweeper\Main.java"
if errorlevel 1 exit /b 1

echo Compilation successful! Run with: run.bat
