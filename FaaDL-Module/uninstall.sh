#!/system/bin/sh
# uninstall.sh - dijalankan saat modul dihapus (Magisk/KernelSU/APatch).
# Hapus aplikasi manager agar tidak yatim.

if pm list packages 2>/dev/null | grep -q "package:com.faa.faadlmod"; then
  pm uninstall com.faa.faadlmod >/dev/null 2>&1
fi

# Bersihkan sisa file kerja aplikasi (opsional, history download TIDAK dihapus).
rm -rf /data/data/com.faa.faadlmod 2>/dev/null
rm -rf /data/user/*/com.faa.faadlmod 2>/dev/null

exit 0
