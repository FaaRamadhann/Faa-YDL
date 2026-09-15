#!/system/bin/sh
# customize.sh - Faa YTDownloader (FYDL), arm64 only.
# Catatan: di sebagian installer (mis. Kitsune via CLI) script ini bisa
# dilewati — itu TIDAK masalah: file system/ di-mount otomatis oleh
# Magic Mount, dan manager.apk dipasang oleh service.sh tiap boot.

SKIPUNZIP=0

if [ -z "$MODPATH" ]; then
  MODPATH="/data/adb/modules/faadl"
fi

ui_print " "
ui_print "   =========================================="
MODVER="1.0.1"
if [ -f "$MODPATH/module.prop" ]; then
  MODVER=$(grep "^version=" "$MODPATH/module.prop" 2>/dev/null | cut -d= -f2)
  [ -z "$MODVER" ] && MODVER="1.0.1"
fi
ui_print "      Faa YTDownloader (FYDL) v$MODVER"
ui_print "      by Faa Ramadhan (arm64)"
ui_print "   =========================================="
ui_print " "

# Binary + lib langsung ikut Magic Mount dari system/ (tanpa copy manual).

# Install aplikasi manager langsung bila bisa (service.sh mengulang tiap boot).
if [ -f "$MODPATH/manager.apk" ]; then
  ui_print "- Memasang FYDL Manager..."
  if pm install -r "$MODPATH/manager.apk" >/dev/null 2>&1; then
    ui_print "- FYDL Manager terinstall."
  else
    ui_print "- Manager menyusul via service.sh (reboot dulu)."
  fi
fi

# Izin (bila installer menyediakan set_perm*).
if command -v set_perm_recursive >/dev/null 2>&1; then
  set_perm_recursive "$MODPATH/system" 0 0 0755 0644
  set_perm_recursive "$MODPATH/system/bin" 0 0 0755 0755
fi
for b in faadl-ffmpeg faadl-python3.14 faadl-ytdlp faadl-qjs faadl-python; do
  [ -f "$MODPATH/system/bin/$b" ] && chmod 0755 "$MODPATH/system/bin/$b" 2>/dev/null
done
chcon -R u:object_r:shell_exec:s0 "$MODPATH/system/bin" 2>/dev/null || true

ui_print " "
ui_print "- Selesai. Reboot, lalu buka FYDL."
ui_print " "
