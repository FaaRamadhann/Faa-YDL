@echo off
rem Faa YTDownloader - install ke system PATH (Windows).
rem Otomatis minta Run as administrator kalau belum. Sesudah ini,
rem buka cmd baru lalu ketik: fydl
setlocal
net session >nul 2>&1
if errorlevel 1 (
  echo Meminta akses administrator...
  powershell -NoProfile -Command "Start-Process '%~f0' -Verb RunAs"
  exit /b
)
set "INSTDIR=%~dp0"
if "%INSTDIR:~-1%"=="\" set "INSTDIR=%INSTDIR:~0,-1%"
echo Install dir: %INSTDIR%
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-path.ps1" -Dir "%INSTDIR%"
if errorlevel 1 (
  echo Gagal ubah PATH.
  pause
  exit /b 1
)
where node >nul 2>nul
if errorlevel 1 (
  echo Perhatian: node belum kepasang. Install Node.js LTS dari https://nodejs.org
) else (
  cd /d "%INSTDIR%"
  if not exist node_modules (
    echo Install dependencies...
    call npm install
  )
)
echo.
echo Berhasil. Tutup cmd ini, buka cmd BARU, lalu ketik: fydl
pause
