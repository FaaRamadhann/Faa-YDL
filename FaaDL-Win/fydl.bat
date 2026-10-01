@echo off
rem fydl - CLI Faa YTDownloader (Windows).
rem   fydl                -> jalankan server Web UI (buka browser otomatis)
rem   fydl mp3 URL        -> download MP3 ke Downloads\FaaDL
rem   fydl mp4 URL        -> download MP4 ke Downloads\FaaDL
rem   fydl --test [URL]   -> cek node, yt-dlp, ffmpeg (+ judul video bila URL diisi)
rem   fydl help           -> bantuan ini
setlocal
set "APPDIR=%~dp0"
set "OUTDIR=%USERPROFILE%\Downloads\FaaDL"
if "%~1"=="" goto :server
if /i "%~1"=="help" goto :usage
if /i "%~1"=="-h" goto :usage
if /i "%~1"=="--help" goto :usage
if /i "%~1"=="mp3" goto :mp3
if /i "%~1"=="mp4" goto :mp4
if /i "%~1"=="--test" goto :test
if /i "%~1"=="test" goto :test
echo Perintah tidak dikenal: %~1
echo.
goto :usage

:usage
echo Pakai: fydl ^|--test [URL] ^| mp3 URL ^| mp4 URL ^| help
echo Contoh: fydl mp3 https://www.youtube.com/watch?v=Oreek8z0yxk
echo Tanpa argumen: jalankan server Web UI di http://localhost:2080
exit /b 0

:server
call "%APPDIR%start.bat"
exit /b %errorlevel%

:need-ytdlp
where yt-dlp >nul 2>nul
if errorlevel 1 (
  echo yt-dlp tidak ketemu di PATH. Install dulu, misal: winget install yt-dlp.yt-dlp
  echo Lalu tutup dan buka ulang cmd ini.
  exit /b 1
)
exit /b 0

:mp3
call :need-ytdlp
if errorlevel 1 exit /b 1
if "%~2"=="" (
  echo URL-nya mana? Contoh: fydl mp3 https://www.youtube.com/watch?v=Oreek8z0yxk
  exit /b 1
)
if not exist "%OUTDIR%" mkdir "%OUTDIR%"
yt-dlp --no-playlist --ignore-errors --extractor-args "youtube:player_client=android" -x --audio-format mp3 --audio-quality 192K -o "%OUTDIR%\%%(title)s.%%(ext)s" "%~2"
exit /b %errorlevel%

:mp4
call :need-ytdlp
if errorlevel 1 exit /b 1
if "%~2"=="" (
  echo URL-nya mana? Contoh: fydl mp4 https://www.youtube.com/watch?v=Oreek8z0yxk
  exit /b 1
)
if not exist "%OUTDIR%" mkdir "%OUTDIR%"
yt-dlp --no-playlist --ignore-errors --extractor-args "youtube:player_client=android" -f best -o "%OUTDIR%\%%(title)s.%%(ext)s" "%~2"
exit /b %errorlevel%

:test
echo == FYDL self-test (Windows) ==
node --version 2>nul
if errorlevel 1 (
  echo [X] node tidak ketemu. Install Node.js LTS dari https://nodejs.org
) else (
  echo [OK] node ada
)
call :need-ytdlp >nul 2>nul
if errorlevel 1 (
  echo [X] yt-dlp tidak ketemu. Contoh: winget install yt-dlp.yt-dlp
) else (
  for /f "delims=" %%v in ('yt-dlp --version 2^>nul') do echo [OK] yt-dlp %%v
)
ffmpeg -version 2>nul | findstr /c:"ffmpeg version" >nul
if errorlevel 1 (
  echo [X] ffmpeg tidak ketemu. Contoh: winget install Gyan.FFmpeg
) else (
  for /f "tokens=3" %%v in ('ffmpeg -version 2^>nul ^| findstr /c:"ffmpeg version"') do echo [OK] ffmpeg %%v
)
if not "%~2"=="" (
  echo -- tes judul video --
  yt-dlp --skip-download --no-playlist --print title "%~2"
)
echo == selesai ==
exit /b 0
