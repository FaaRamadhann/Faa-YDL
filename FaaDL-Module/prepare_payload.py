"""Susun payload/ Faa-DL-Module dari bins/ milik rebuild-ytdl.
- payload/arm64/bin  : faadl-ffmpeg, faadl-python3.14, faadl-python, faadl-ytdlp, faadl-qjs
- payload/arm64 -> system/lib64/faadl = isi bins/arm64/faapy/lib64
- payload/arm/bin    : versi 32-bit (python 3.12 AAR)
- payload/arm   -> system/lib/faadl/usr = isi stdlib.zip (symlink di-resolve)
Jalankan dari root modul:  python prepare_payload.py
"""
import os
import shutil
import stat
import zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))
BINS = os.path.normpath(os.path.join(ROOT, "..", "FaaDL-Standalone", "bins"))
PAY = os.path.join(ROOT, "payload")

WRAPPER_64 = """#!/system/bin/sh
# faadl-python - wrapper Python 3.14 (FYDL, arm64)
export PYTHONHOME=/system/lib64/faadl
export LD_LIBRARY_PATH=/system/lib64/faadl/lib/python3.14/lib-dynload:/system/lib64/faadl:$LD_LIBRARY_PATH
export SSL_CERT_DIR=/system/etc/security/cacerts
export SSL_CERT_FILE=/system/lib64/faadl/etc/tls/cert.pem
export PIP_ROOT_USER_ACTION=ignore
exec /system/bin/faadl-python3.14 "$@"
"""

WRAPPER_32 = """#!/system/bin/sh
# faadl-python - wrapper Python 3.12 (FYDL, arm)
export PYTHONHOME=/system/lib/faadl/usr
export LD_LIBRARY_PATH=/system/lib/faadl/usr/lib:$LD_LIBRARY_PATH
export SSL_CERT_DIR=/system/etc/security/cacerts
export PIP_ROOT_USER_ACTION=ignore
exec /system/bin/faadl-python312 "$@"
"""


def put(src, dst, exec_ok=False):
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copyfile(src, dst)
    if exec_ok:
        os.chmod(dst, 0o755)


def copytree(src, dst):
    if os.path.isdir(dst):
        shutil.rmtree(dst)
    shutil.copytree(src, dst)


def unzip_resolved(zpath, dest):
    """Ekstrak zip ke dest, symlink di-resolve jadi copy target."""
    z = zipfile.ZipFile(zpath)
    links = []
    for info in z.infolist():
        name = info.filename
        if name.endswith("/"):
            continue
        islink = ((info.external_attr >> 16) & 0o170000) == 0o120000
        data = z.read(name)
        if islink:
            links.append((name, data.decode("utf-8", "replace").strip()))
            continue
        out = os.path.join(dest, *name.split("/"))
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with open(out, "wb") as f:
            f.write(data)
    for name, target in links:
        t = os.path.normpath(os.path.join(os.path.dirname(name), target))
        src = os.path.join(dest, *t.split("/"))
        dst = os.path.join(dest, *name.split("/"))
        if os.path.isfile(src):
            shutil.copyfile(src, dst)
        else:
            print(f"  ! link tanpa target: {name} -> {target}")
    z.close()


def build_arm64():
    d = os.path.join(PAY, "arm64")
    b = os.path.join(BINS, "arm64")
    put(os.path.join(b, "ffmpeg"), os.path.join(d, "bin", "faadl-ffmpeg"), True)
    put(os.path.join(b, "faapy", "bin", "python3.14"),
        os.path.join(d, "bin", "faadl-python3.14"), True)
    put(os.path.join(b, "fflib", "libc++_shared.so"),
        os.path.join(d, "lib64", "faadl", "libc++_shared.so"))
    put(os.path.join(BINS, "common", "yt-dlp"), os.path.join(d, "bin", "faadl-ytdlp"), True)
    put(os.path.join(b, "py", "libqjs.so"), os.path.join(d, "bin", "faadl-qjs"), True)
    with open(os.path.join(d, "bin", "faadl-python"), "w", newline="\n") as f:
        f.write(WRAPPER_64)
    os.chmod(os.path.join(d, "bin", "faadl-python"), 0o755)
    copytree(os.path.join(b, "faapy", "lib64"), os.path.join(d, "lib64", "faadl"))
    # pastikan executable bit loader (dari git/zip bisa hilang)
    os.chmod(os.path.join(d, "bin", "faadl-python3.14"), 0o755)


def build_arm():
    d = os.path.join(PAY, "arm")
    b = os.path.join(BINS, "arm32")
    put(os.path.join(b, "ffmpeg"), os.path.join(d, "bin", "faadl-ffmpeg"), True)
    put(os.path.join(b, "py", "libpython.so"),
        os.path.join(d, "bin", "faadl-python312"), True)
    put(os.path.join(b, "fflib", "libc++_shared.so"),
        os.path.join(d, "lib", "faadl", "usr", "lib", "libc++_shared.so"))
    put(os.path.join(BINS, "common", "yt-dlp"), os.path.join(d, "bin", "faadl-ytdlp"), True)
    put(os.path.join(b, "py", "libqjs.so"), os.path.join(d, "bin", "faadl-qjs"), True)
    with open(os.path.join(d, "bin", "faadl-python"), "w", newline="\n") as f:
        f.write(WRAPPER_32)
    os.chmod(os.path.join(d, "bin", "faadl-python"), 0o755)
    dest = os.path.join(d, "lib", "faadl")
    if os.path.isdir(dest):
        shutil.rmtree(dest)
    os.makedirs(dest, exist_ok=True)
    unzip_resolved(os.path.join(b, "py", "stdlib.zip"), dest)


FDL_SH = """#!/system/bin/sh
# fdl - CLI Faa YTDownloader (FYDL) untuk shell / adb shell / Termux.
#
#   fdl --test [URL]       diagnostik python/ffmpeg/yt-dlp/innertube
#   fdl mp3 <URL>          download audio -> /sdcard/Download/FaaDL/*.mp3
#   fdl mp4 <URL>          download video -> /sdcard/Download/FaaDL/*.mp4
#   fdl help               bantuan ini

OUTDIR=/sdcard/Download/FaaDL
YTM=/system/bin/faadl-ytdlp
PY=faadl-python

usage() {
  echo "Pakai: fdl --test [URL] | fdl mp3 <URL> | fdl mp4 <URL>"
  echo "Contoh: fdl mp3 https://www.youtube.com/watch?v=Oreek8z0yxk"
}

do_test() {
  URL=${1:-https://www.youtube.com/watch?v=Oreek8z0yxk}
  echo "== FYDL self-test =="
  echo "-- arch: $(getprop ro.product.cpu.abi 2>/dev/null)  sdk: $(getprop ro.build.version.sdk 2>/dev/null)"
  echo "-- file:"
  ls -l /system/bin/faadl-ffmpeg /system/bin/faadl-python /system/bin/faadl-ytdlp /system/bin/faadl-qjs 2>&1
  echo "-- python:"
  $PY --version 2>&1
  $PY -c "import ssl,json,urllib.request;print('ssl+json+urllib ok')" 2>&1
  echo "-- ffmpeg:"
  faadl-ffmpeg -version 2>&1 | head -n 2
  echo "-- yt-dlp:"
  $PY $YTM --version --no-cache-dir 2>&1
  echo "-- innertube ($URL):"
  $PY - "$URL" 2>&1 <<'PYEOF'
import json, re, sys, urllib.request
url = sys.argv[1] if len(sys.argv) > 1 else ''
m = (re.search(r'[?&]v=([A-Za-z0-9_-]{11})', url) or re.search(r'youtu\\.be/([A-Za-z0-9_-]{11})', url)
     or re.search(r'/shorts/([A-Za-z0-9_-]{11})', url))
if not m:
    print('ID video tidak ketemu di URL');
    sys.exit(2)
body = {'context': {'client': {
    'clientName': 'ANDROID', 'clientVersion': '21.26.364',
    'androidSdkVersion': 30, 'hl': 'en', 'gl': 'US',
    'osName': 'Android', 'osVersion': '11'}},
    'videoId': m.group(1), 'racyCheckOk': True, 'contentCheckOk': True}
req = urllib.request.Request(
    'https://www.youtube.com/youtubei/v1/player?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8&prettyPrint=false',
    data=json.dumps(body).encode(),
    headers={'Content-Type': 'application/json',
             'User-Agent': 'com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip'})
try:
    p = json.load(urllib.request.urlopen(req, timeout=30))
except Exception as e:
    print('HTTP gagal: %s: %s' % (type(e).__name__, str(e)[:200]))
    sys.exit(3)
ps = p.get('playabilityStatus', {})
print('status:', ps.get('status'), '| reason:', ps.get('reason', '-'))
print('judul:', p.get('videoDetails', {}).get('title'))
sd = p.get('streamingData', {}) or {}
fmts, adap = sd.get('formats', []), sd.get('adaptiveFormats', [])
print('formats:', len(fmts), 'adaptive:', len(adap),
      'direct:', sum(1 for f in fmts + adap if f.get('url')))
for f in fmts[:4]:
    print('  prog:', str(f.get('mimeType', ''))[:32], f.get('qualityLabel'),
          'url' if f.get('url') else 'CIPHER')
PYEOF
  echo "== selesai =="
}

do_dl() {
  FMT="$1"
  URL="$2"
  [ -z "$URL" ] && { usage; return 1; }
  mkdir -p "$OUTDIR" 2>/dev/null
  ARGS="--no-cache-dir --no-playlist --ignore-errors --extractor-args youtube:player_client=android --ffmpeg-location /system/bin/faadl-ffmpeg --js-runtimes quickjs:/system/bin/faadl-qjs"
  if [ "$FMT" = "mp3" ]; then
    # shellcheck disable=SC2086
    $PY $YTM $ARGS -x --audio-format mp3 --audio-quality 192K \
      -o "$OUTDIR/%(title)s.%(ext)s" "$URL" 2>&1
  elif [ "$FMT" = "mp4" ]; then
    # shellcheck disable=SC2086
    $PY $YTM $ARGS -f best \
      -o "$OUTDIR/%(title)s.%(ext)s" "$URL" 2>&1
  else
    echo "format harus mp3/mp4"; return 1
  fi
}

case "$1" in
  --test|test) shift; do_test "$@" ;;
  mp3|mp4) do_dl "$1" "$2" ;;
  help|-h|--help|"") usage ;;
  *) echo "perintah '$1' tidak dikenal"; usage; exit 1 ;;
esac
"""

WRAPPER_FFMPEG = """#!/system/bin/sh
# faadl-ffmpeg - wrapper agar libc++ pendamping ketemu linker
export LD_LIBRARY_PATH=/system/lib64/faadl:$LD_LIBRARY_PATH
exec /system/bin/faadl-ffmpeg.bin "$@"
"""

def build_system_arm64():
    """Susun langsung system/ final (arm64 only): bin + lib64/faadl."""
    b = os.path.join(BINS, "arm64")
    sysbin = os.path.join(ROOT, "system", "bin")
    syslib = os.path.join(ROOT, "system", "lib64", "faadl")
    os.makedirs(sysbin, exist_ok=True)
    put(os.path.join(b, "ffmpeg"), os.path.join(sysbin, "faadl-ffmpeg.bin"), True)
    with open(os.path.join(sysbin, "faadl-ffmpeg"), "w", newline="\n") as f:
        f.write(WRAPPER_FFMPEG)
    os.chmod(os.path.join(sysbin, "faadl-ffmpeg"), 0o755)
    put(os.path.join(b, "faapy", "bin", "python3.14"),
        os.path.join(sysbin, "faadl-python3.14"), True)
    put(os.path.join(BINS, "common", "yt-dlp"), os.path.join(sysbin, "faadl-ytdlp"), True)
    put(os.path.join(b, "py", "libqjs.so"), os.path.join(sysbin, "faadl-qjs"), True)
    with open(os.path.join(sysbin, "faadl-python"), "w", newline="\n") as f:
        f.write(WRAPPER_64)
    os.chmod(os.path.join(sysbin, "faadl-python"), 0o755)
    with open(os.path.join(sysbin, "fdl"), "w", newline="\n") as f:
        f.write(FDL_SH)
    os.chmod(os.path.join(sysbin, "fdl"), 0o755)
    copytree(os.path.join(b, "faapy", "lib64"), syslib)
    put(os.path.join(b, "fflib", "libc++_shared.so"),
        os.path.join(syslib, "libc++_shared.so"))
    # bundel CA sendiri (system CA store tak bisa dipakai openssl di semua ROM)
    _certzip = os.path.join(b, "py", "stdlib.zip")
    try:
        _z = zipfile.ZipFile(_certzip)
        _cert = _z.read("usr/etc/tls/cert.pem")
        _z.close()
        _cdst = os.path.join(syslib, "etc", "tls", "cert.pem")
        os.makedirs(os.path.dirname(_cdst), exist_ok=True)
        with open(_cdst, "wb") as _f:
            _f.write(_cert)
        print(f"  cert.pem {len(_cert) // 1024} KB", flush=True)
    except Exception as e:
        print(f"  ! cert.pem gagal: {e}", flush=True)
    os.chmod(os.path.join(sysbin, "faadl-python3.14"), 0o755)


def main():
    build_system_arm64()
    total = 0
    nfiles = 0
    for dp, _, fns in os.walk(os.path.join(ROOT, "system")):
        for f in fns:
            total += os.path.getsize(os.path.join(dp, f))
            nfiles += 1
    print(f"system/: {nfiles} file, {total // 1024} KB", flush=True)
    cxx = os.path.join(ROOT, "system", "lib64", "faadl", "libc++_shared.so")
    print("libc++:", os.path.getsize(cxx) if os.path.exists(cxx) else "HILANG", flush=True)
    print("SELESAI payload.", flush=True)


if __name__ == "__main__":
    main()
