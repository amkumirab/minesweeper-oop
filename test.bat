@echo off
setlocal
cd /d "%~dp0"

if not exist "out\tests" mkdir "out\tests"
javac -Xlint:all -encoding UTF-8 -d "out\tests" -sourcepath "src;tests" "src\minesweeper\Main.java" "tests\minesweeper\engine\RevealTests.java" "tests\minesweeper\PlayerTests.java" "tests\minesweeper\engine\TimeLimitTests.java" "tests\minesweeper\EncapsulationTests.java" "tests\minesweeper\ConsoleInputTests.java" "tests\minesweeper\engine\EngineLifecycleTests.java"
if errorlevel 1 exit /b 1

java -cp "out\tests" minesweeper.engine.RevealTests
if errorlevel 1 exit /b 1

java -cp "out\tests" minesweeper.PlayerTests
if errorlevel 1 exit /b 1

java -cp "out\tests" minesweeper.engine.TimeLimitTests
if errorlevel 1 exit /b 1

java -cp "out\tests" minesweeper.EncapsulationTests
if errorlevel 1 exit /b 1

java -cp "out\tests" minesweeper.ConsoleInputTests
if errorlevel 1 exit /b 1

java -cp "out\tests" minesweeper.engine.EngineLifecycleTests
exit /b %errorlevel%
