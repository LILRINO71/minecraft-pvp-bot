@echo off
setlocal EnableDelayedExpansion
title MC BOT - Setup and Build (MC 26.1.2)
call :main > "%~dp0setup-log.txt" 2>&1
type "%~dp0setup-log.txt"
echo.
echo  ----------------------------------------------------
echo  A copy of this output was saved to setup-log.txt
echo  ----------------------------------------------------
pause
exit /b

:main
echo =====================================
echo   MC BOT - Setup and Build (MC 26.1.2)
echo =====================================
echo.
set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

echo [1/3] Checking Java (Minecraft 26.1.2 needs Java 25+)...
java -version >nul 2>&1
if errorlevel 1 goto no_java
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JAVA_VER=%%v"
set "JAVA_VER=!JAVA_VER:"=!"
for /f "tokens=1 delims=." %%m in ("!JAVA_VER!") do set "JAVA_MAJOR=%%m"
echo      Found Java !JAVA_VER!
if !JAVA_MAJOR! LSS 25 goto old_java
echo      Java OK (using system Java for the build).

echo.
echo [2/3] Setting up Gradle 9.4.1 wrapper...
if exist "gradle\wrapper\gradle-wrapper.jar" goto have_wrapper
echo      Downloading Gradle 9.4.1 (one time)...
set "GRADLE_HOME=%SCRIPT_DIR%gradle-dist\gradle-9.4.1"
powershell -NoProfile -Command "$ErrorActionPreference='Stop'; $z=Join-Path $env:TEMP 'mcbot-gradle-9.4.1.zip'; if(-not (Test-Path $z)){ Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-9.4.1-bin.zip' -OutFile $z -UseBasicParsing }; Remove-Item 'gradle-dist' -Recurse -Force -ErrorAction SilentlyContinue; Expand-Archive $z 'gradle-dist' -Force"
if not exist "%GRADLE_HOME%\bin\gradle.bat" goto wrapper_fail
call "%GRADLE_HOME%\bin\gradle.bat" wrapper --gradle-version 9.4.1 --distribution-type bin --no-daemon
if not exist "gradle\wrapper\gradle-wrapper.jar" goto wrapper_fail
echo      Wrapper ready.
:have_wrapper

echo.
echo [3/3] Building MC BOT (first run downloads Minecraft 26.1.2, several minutes)...
echo       Do not close this window.
call gradlew.bat build --stacktrace --no-daemon
if errorlevel 1 goto build_fail

echo.
set "BUILT_JAR="
for /f %%f in ('dir /b /s build\libs\mcbot-*.jar 2^>nul') do set "BUILT_JAR=%%f"
echo =====================================
echo   BUILD COMPLETE
echo =====================================
if defined BUILT_JAR echo  Your mod jar: %BUILT_JAR%
if not defined BUILT_JAR echo  Build said OK but no jar found - tell Claude.
goto :eof

:no_java
echo  [ERROR] Java not found on PATH. Install Temurin JDK 25 from https://adoptium.net/ then re-run.
goto :eof

:old_java
echo  [ERROR] Java !JAVA_VER! is too old. Minecraft 26.1.2 needs Java 25+.
echo          Install Temurin JDK 25 from https://adoptium.net/ then re-run.
goto :eof

:wrapper_fail
echo  [ERROR] Gradle wrapper setup failed - see messages above.
goto :eof

:build_fail
echo.
echo  =====================================
echo   BUILD FAILED - check errors above
echo  =====================================
echo  (Expected for now: the source code still uses old yarn names and
echo   needs porting to 26.1 official mappings. Send setup-log.txt to Claude.)
goto :eof
