@echo off
rem ============================================================
rem  build.bat - Faa DL (com.faa.faadl) TANPA Gradle
rem  Tahapan sama seperti ex-build (Example-Build):
rem    javac -^> jar -^> d8 -^> aapt -^> keystore -^> zipalign -^> apksigner
rem  (jar dipakai agar argumen d8 tidak kepanjangan)
rem
rem  Menghasilkan 3 APK di folder build\:
rem    1. faa-dl_v{ver}_arm64.apk      (64 bit / arm64-v8a)
rem    2. faa-dl_v{ver}_arm32.apk      (32 bit / armeabi-v7a)
rem    3. faa-dl_v{ver}_universal.apk  (universal, dua ffmpeg + pilih runtime)
rem
rem  Pakai:  build.bat [arm64^|arm32^|universal^|all]
rem  Default: all
rem
rem  Implementasi build ada di build.py (versi Python dari ex-build.py,
rem  lebih stabil untuk multi-varian). File ini hanya penerus.
rem ============================================================
setlocal

if "%~1"=="" (
  python build.py all
) else if "%~1"=="64" (
  python build.py arm64
) else if "%~1"=="32" (
  python build.py arm32
) else (
  python build.py %~1
)
if errorlevel 1 exit /b 1

endlocal
exit /b 0
