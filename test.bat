@echo off
setlocal
cd /d "%~dp0"

if not exist "out\tests" mkdir "out\tests"
javac -Xlint:all -encoding UTF-8 -d "out\tests" -sourcepath "src;tests" "src\minesweeper\Main.java" "tests\minesweeper\RevealTests.java"
if errorlevel 1 exit /b 1

java -cp "out\tests" minesweeper.RevealTests
exit /b %errorlevel%
