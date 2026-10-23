@echo off
rem ============================================================
rem  build.bat - FYDL Manager (com.faa.faadlmod)
rem    build.bat          -> python build.py (cepat, tanpa internet)
rem    build.bat gradle   -> gradlew assembleRelease (SDK di C:\AndroidSDK)
rem  Hasil dua-duanya: build\manager.apk
rem ============================================================
setlocal
if /i "%~1"=="gradle" goto :gradle
python "%~dp0build.py"
if errorlevel 1 exit /b 1
exit /b 0

:gradle
if not defined ANDROID_HOME if exist "C:\AndroidSDK" set "ANDROID_HOME=C:\AndroidSDK"
call "%~dp0gradlew.bat" assembleRelease --console=plain
exit /b %errorlevel%
