@echo off
setlocal
cd /d "%~dp0"

if not exist "out\gui-tests" mkdir "out\gui-tests"
javac -Xlint:all -encoding UTF-8 -d "out\gui-tests" -sourcepath "src;tests" "src\minesweeper\Main.java" "tests\minesweeper\engine\VictoryDialogTests.java"
if errorlevel 1 exit /b 1

java -cp "out\gui-tests" minesweeper.engine.VictoryDialogTests
exit /b %errorlevel%
