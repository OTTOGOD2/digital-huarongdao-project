@echo off
setlocal
cd /d "%~dp0"

echo [1/4] Compiling...
if not exist out mkdir out
javac -encoding UTF-8 -d out src\*.java
if errorlevel 1 exit /b 1

echo [2/4] Running tests...
java -cp out PuzzleBoardTest
if errorlevel 1 exit /b 1
java -cp out RecordStoreTest
if errorlevel 1 exit /b 1
java -cp out PuzzleSolverTest
if errorlevel 1 exit /b 1

echo [3/4] Packaging jar (test classes excluded)...
if exist build rmdir /s /q build
mkdir build\classes
javac -encoding UTF-8 -d build\classes src\DigitalHuarongdao.java src\PuzzleBoard.java src\PuzzleGameFrame.java src\PuzzleSolver.java src\RecordStore.java
if errorlevel 1 exit /b 1
jar cfe build\DigitalHuarongdao.jar DigitalHuarongdao -C build\classes .
if errorlevel 1 exit /b 1

echo [4/4] Creating standalone executable with jpackage...
if exist dist rmdir /s /q dist
jpackage --type app-image --name DigitalHuarongdao --input build --main-jar DigitalHuarongdao.jar --main-class DigitalHuarongdao --dest dist
if errorlevel 1 exit /b 1

echo.
echo Done: dist\DigitalHuarongdao\DigitalHuarongdao.exe (bundled runtime, double-click to run)
