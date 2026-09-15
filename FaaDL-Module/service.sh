#!/system/bin/sh
# service.sh - FYDL: pastikan manager terinstall (tiap boot).
# Backstop bila customize.sh dilewati installer: pasang manager.apk
# dari dalam modul. Idempoten via checksum marker.

MODDIR=${0%/*}
PKG=com.faa.faadlmod
APK=$MODDIR/manager.apk
MARK=$MODDIR/.mgr_sum

[ -f "$APK" ] || exit 0

SUM=$(sha256sum "$APK" 2>/dev/null | cut -d' ' -f1)

have_pkg() {
  pm list packages 2>/dev/null | grep -q "package:$PKG"
}

marked_ok() {
  [ -n "$SUM" ] && [ -f "$MARK" ] && [ "$(cat "$MARK" 2>/dev/null)" = "$SUM" ]
}

if have_pkg && marked_ok; then
  exit 0
fi

i=0
while [ $i -lt 12 ]; do
  pm install -r "$APK" >/dev/null 2>&1
  sleep 10
  if have_pkg; then
    [ -n "$SUM" ] && echo "$SUM" > "$MARK" 2>/dev/null
    exit 0
  fi
  i=$((i + 1))
done

exit 0
