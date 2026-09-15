@echo off
rem Faa YTDownloader (Windows) - jalankan server Web UI.
cd /d "%~dp0"
where node >nul 2>nul
if errorlevel 1 (
  echo Node.js belum kepasang. Install LTS dari https://nodejs.org lalu ulangi.
  pause
  exit /b 1
)
if not exist node_modules (
  echo Install dependencies dulu...
  call npm install
  if errorlevel 1 exit /b 1
)
echo Buka http://localhost:2080 di browser (Ctrl+C untuk berhenti)
start "" http://localhost:2080
node server.js
