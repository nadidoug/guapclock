@echo off
setlocal

cd /d "%~dp0"

set "JAVA_EXE=java"

if exist "..\..\..\java-workflow\tools\jdk\bin\java.exe" (
    set "JAVA_EXE=..\..\..\java-workflow\tools\jdk\bin\java.exe"
)

"%JAVA_EXE%" -version >nul 2>&1
if errorlevel 1 (
    echo Booth Time Clock could not find Java.
    echo.
    echo Install Java 17 or newer, then run this file again:
    echo https://adoptium.net/temurin/releases/
    echo.
    pause
    exit /b 1
)

"%JAVA_EXE%" -cp "." BoothTimeClock

if errorlevel 1 (
    echo.
    echo Booth Time Clock closed with an error.
    pause
)

endlocal

