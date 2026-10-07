#!/usr/bin/env python3
"""ydl - CLI download Faa YTDownloader (Windows), mirip fdl di modul Magisk.

    ydl --test [URL]   diagnostik yt-dlp/ffmpeg/innertube
    ydl mp3 <URL>      download audio -> Downloads\\FaaDL\\*.mp3
    ydl mp4 <URL>      download video -> Downloads\\FaaDL\\*.mp4
    ydl help           bantuan ini
"""
import json
import os
import platform
import shutil
import subprocess
import sys
import urllib.request

OUTDIR = os.path.join(os.path.expanduser("~"), "Downloads", "FaaDL")
YTDLP = shutil.which("yt-dlp")
FFMPEG = shutil.which("ffmpeg")
TEST_URL = "https://www.youtube.com/watch?v=Oreek8z0yxk"


def usage():
    print("Pakai: ydl --test [URL] | mp3 URL | mp4 URL | help")
    print("Contoh: ydl mp3 https://www.youtube.com/watch?v=Oreek8z0yxk")
    print("Hasil: " + OUTDIR)


def run(cmd):
    try:
        p = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
        return p.returncode, (p.stdout + p.stderr).strip()
    except FileNotFoundError:
        return 127, "perintah tidak ketemu: " + cmd[0]
    except Exception as e:
        return 1, str(e)


def do_test(url=TEST_URL):
    print("== FYDL self-test (Windows) ==")
    print("-- arch: %s  os: %s" % (platform.machine(), platform.system()))
    print("-- file:")
    print("   yt-dlp: " + (YTDLP or "TIDAK ADA (winget install yt-dlp.yt-dlp)"))
    print("   ffmpeg: " + (FFMPEG or "TIDAK ADA (winget install Gyan.FFmpeg)"))
    if YTDLP:
        rc, out = run([YTDLP, "--version"])
        print("-- yt-dlp: " + (out.splitlines()[0] if out else "rc=%d" % rc))
    if FFMPEG:
        rc, out = run([FFMPEG, "-version"])
        print("-- ffmpeg: " + (out.splitlines()[0] if out else "rc=%d" % rc))
    print("-- innertube (%s):" % url)
    import re
    m = (re.search(r"[?&]v=([A-Za-z0-9_-]{11})", url)
         or re.search(r"youtu\.be/([A-Za-z0-9_-]{11})", url)
         or re.search(r"/shorts/([A-Za-z0-9_-]{11})", url))
    if not m:
        print("ID video tidak ketemu di URL")
        return 2
    body = {"context": {"client": {
        "clientName": "ANDROID", "clientVersion": "21.26.364",
        "androidSdkVersion": 30, "hl": "en", "gl": "US",
        "osName": "Android", "osVersion": "11"}},
        "videoId": m.group(1), "racyCheckOk": True, "contentCheckOk": True}
    req = urllib.request.Request(
        "https://www.youtube.com/youtubei/v1/player?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8&prettyPrint=false",
        data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json",
                 "User-Agent": "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip"})
    try:
        p = json.load(urllib.request.urlopen(req, timeout=30))
    except Exception as e:
        print("HTTP gagal: %s: %s" % (type(e).__name__, str(e)[:200]))
        return 3
    ps = p.get("playabilityStatus", {})
    print("status:", ps.get("status"), "| reason:", ps.get("reason", "-"))
    print("judul:", p.get("videoDetails", {}).get("title"))
    sd = p.get("streamingData", {}) or {}
    fmts, adap = sd.get("formats", []), sd.get("adaptiveFormats", [])
    print("formats:", len(fmts), "adaptive:", len(adap),
          "direct:", sum(1 for f in fmts + adap if f.get("url")))
    for f in fmts[:4]:
        print("  prog:", str(f.get("mimeType", ""))[:32], f.get("qualityLabel"),
              "url" if f.get("url") else "CIPHER")
    print("== selesai ==")
    return 0


def do_dl(fmt, url):
    if not url:
        usage()
        return 1
    if not YTDLP:
        print("yt-dlp tidak ketemu di PATH. Contoh: winget install yt-dlp.yt-dlp")
        return 1
    os.makedirs(OUTDIR, exist_ok=True)
    args = ["--no-playlist", "--ignore-errors",
            "--extractor-args", "youtube:player_client=android"]
    if FFMPEG:
        args += ["--ffmpeg-location", FFMPEG]
    if fmt == "mp3":
        args += ["-x", "--audio-format", "mp3", "--audio-quality", "192K"]
    elif fmt == "mp4":
        args += ["-f", "best"]
    else:
        print("format harus mp3/mp4")
        return 1
    args += ["-o", os.path.join(OUTDIR, "%(title)s.%(ext)s"), url]
    print("$ " + " ".join([YTDLP] + args))
    p = subprocess.run([YTDLP] + args, cwd=OUTDIR)
    return p.returncode


def main(argv):
    if len(argv) < 2:
        usage()
        return 0
    cmd = argv[1].lower()
    if cmd in ("--test", "test"):
        return do_test(argv[2] if len(argv) > 2 else TEST_URL)
    if cmd in ("mp3", "mp4"):
        return do_dl(cmd, argv[2] if len(argv) > 2 else "")
    if cmd in ("help", "-h", "--help"):
        usage()
        return 0
    print("perintah '%s' tidak dikenal" % argv[1])
    usage()
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
