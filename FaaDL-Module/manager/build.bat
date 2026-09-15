@echo off
rem ============================================================
rem  build.bat - FYDL Manager (com.faa.faadlmod) TANPA Gradle
rem  Tahapan seperti ex-build (Example-Build):
rem    javac -^> jar -^> d8 -^> aapt -^> keystore -^> zipalign -^> apksigner
rem  Implementasi di build.py. Hasil: build\manager.apk
rem ============================================================
setlocal
python build.py
if errorlevel 1 exit /b 1
endlocal
exit /b 0
