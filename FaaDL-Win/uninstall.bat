@echo off
rem Faa YTDownloader - hapus dari system PATH (Windows).
rem Otomatis minta Run as administrator kalau belum.
rem Catatan: folder FaaDL-Win TIDAK dihapus, hapus manual bila perlu.
setlocal
net session >nul 2>&1
if errorlevel 1 (
  echo Meminta akses administrator...
  powershell -NoProfile -Command "Start-Process '%~f0' -Verb RunAs"
  exit /b
)
set "INSTDIR=%~dp0"
if "%INSTDIR:~-1%"=="\" set "INSTDIR=%INSTDIR:~0,-1%"
echo Hapus dari PATH: %INSTDIR%
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-path.ps1" -Dir "%INSTDIR%" -Remove
if errorlevel 1 (
  echo Gagal ubah PATH.
  pause
  exit /b 1
)
echo.
echo Selesai. Perintah fydl sudah tidak aktif di cmd baru.
pause
