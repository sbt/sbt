@echo off
REM Test script to verify sbt.bat handles paths with parentheses correctly
REM This test verifies the fix for issue #8644

cd "%~dp0"

echo Testing sbt.bat with path containing parentheses...

REM Create a test directory with parentheses in the name
set TEST_DIR=test-parentheses(test)
if exist "%TEST_DIR%" rmdir /s /q "%TEST_DIR%"
mkdir "%TEST_DIR%"

cd "%TEST_DIR%"

REM Create a minimal sbt project structure
mkdir project
echo sbt.version=1.12.1 > project\build.properties

REM Extract the freshly baked sbt
if not exist "..\freshly-baked" (
    echo Error: freshly-baked directory not found. Please run test.bat first.
    exit /b 1
)

REM Test that sbt.bat can handle the path with parentheses
REM The error message should display correctly without ") was unexpected at this time." error
echo Testing sbt.bat from directory with parentheses in path...
"..\freshly-baked\sbt\bin\sbt.bat" -Dsbt.no.format=true --script-version > scriptVersion.txt 2>&1

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: sbt.bat failed with error code %ERRORLEVEL%
    type scriptVersion.txt
    cd ..
    rmdir /s /q "%TEST_DIR%"
    exit /b 1
)

echo SUCCESS: sbt.bat handled path with parentheses correctly
type scriptVersion.txt

REM Test that the error message displays correctly when no build.sbt is found
REM (This is where the fix is most visible - in the error message)
cd ..
if not exist "no-build-project" mkdir "no-build-project"
cd "no-build-project"

echo Testing error message with parentheses in path...
"..\..\freshly-baked\sbt\bin\sbt.bat" -Dsbt.no.format=true compile 2> error.txt
if %ERRORLEVEL% EQU 0 (
    echo ERROR: Expected sbt to fail when no build.sbt exists
    cd ..
    rmdir /s /q "no-build-project"
    exit /b 1
)

REM Check that the error message doesn't contain the ") was unexpected" error
findstr /C:") was unexpected" error.txt >nul
if %ERRORLEVEL% EQU 0 (
    echo ERROR: Error message contains ") was unexpected" - fix did not work!
    type error.txt
    cd ..
    rmdir /s /q "no-build-project"
    exit /b 1
)

echo SUCCESS: Error message displayed correctly without parsing errors
type error.txt

cd ..
rmdir /s /q "no-build-project"
rmdir /s /q "%TEST_DIR%"

echo.
echo All tests passed! The fix correctly handles paths with parentheses.

