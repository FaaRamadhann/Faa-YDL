# Faa YTDownloader - tambah/hapus folder dari system PATH + broadcast perubahan.
# Dipanggil install.bat / uninstall.bat (sudah elevated). Jangan dijalankan manual.
param([string]$Dir, [switch]$Remove)
$p = [Environment]::GetEnvironmentVariable('Path', 'Machine')
$parts = @($p -split ';' | Where-Object { $_ -ne '' })
if ($Remove) {
  $parts = @($parts | Where-Object { $_ -ne $Dir })
  Write-Host 'PATH system dibersihkan.'
} elseif ($parts -notcontains $Dir) {
  $parts += $Dir
  Write-Host 'PATH system ditambah.'
} else {
  Write-Host 'PATH system sudah ada.'
}
[Environment]::SetEnvironmentVariable('Path', ($parts -join ';'), 'Machine')
Add-Type -Namespace Win32 -Name Env -MemberDefinition '[DllImport("user32.dll")] public static extern IntPtr SendMessageTimeout(IntPtr hWnd,uint Msg,UIntPtr wParam,string lParam,uint fuFlags,uint uTimeout,out UIntPtr lpdwResult);'
$r = [UIntPtr]::Zero
[Win32.Env]::SendMessageTimeout([IntPtr]0xffff, 0x1a, [UIntPtr]::Zero, 'Environment', 0x2, 5000, [ref]$r) | Out-Null
Write-Host 'Selesai.'
