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
powershell -NoProfile -ExecutionPolicy Bypass -Command "$d='%INSTDIR%'; $p=[Environment]::GetEnvironmentVariable('Path','Machine'); $parts=$p -split ';' | Where-Object { $_ -ne '' -and $_ -ne $d }; [Environment]::SetEnvironmentVariable('Path', ($parts -join ';'), 'Machine'); Write-Host 'PATH system dibersihkan.'; Add-Type -Namespace Win32 -Name Env -MemberDefinition '[DllImport(`"user32.dll`")] public static extern IntPtr SendMessageTimeout(IntPtr hWnd,uint Msg,UIntPtr wParam,string lParam,uint fuFlags,uint uTimeout,out UIntPtr lpdwResult);'; $r=[UIntPtr]::Zero; [Win32.Env]::SendMessageTimeout([IntPtr]0xffff,0x1a,[UIntPtr]::Zero,'Environment',0x2,5000,[ref]$r) | Out-Null"
if errorlevel 1 (
  echo Gagal ubah PATH.
  pause
  exit /b 1
)
echo.
echo Selesai. Perintah fydl sudah tidak aktif di cmd baru.
pause
