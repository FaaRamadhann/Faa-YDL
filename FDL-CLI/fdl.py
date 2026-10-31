#!/usr/bin/env python3
"""fdl - Cross-platform CLI for Faa YTDownloader.

Commands:
    fdl --test [URL]   Diagnose Python, yt-dlp, ffmpeg and YouTube Innertube
    fdl mp3 URL        Download audio as MP3 (192K)
    fdl mp4 URL        Download video
    fdl help           Show help

Supported platforms: Windows, Linux and Termux.
Optional output override: FDL_OUTPUT_DIR=/path/to/folder
"""

import importlib.util
import json
import os
import platform
import re
import shlex
import shutil
import subprocess
import sys
import urllib.request
from pathlib import Path

TEST_URL = "https://www.youtube.com/watch?v=Oreek8z0yxk"
SYSTEM = platform.system().lower()
IS_TERMUX = (
    "com.termux" in os.environ.get("PREFIX", "").lower()
    or bool(os.environ.get("TERMUX_VERSION"))
)

# FDL_OUTPUT_DIR overrides the platform-specific default output directory.
if os.environ.get("FDL_OUTPUT_DIR"):
    OUTDIR = Path(os.path.expandvars(os.path.expanduser(os.environ["FDL_OUTPUT_DIR"])))
elif IS_TERMUX:
    # Requires `termux-setup-storage` for shared Download access.
    OUTDIR = Path("/sdcard/Download/FaaDL")
else:
    OUTDIR = Path.home() / "Downloads" / "FaaDL"

YTDLP_PATH = shutil.which("yt-dlp")
FFMPEG_PATH = shutil.which("ffmpeg")

# Support both the yt-dlp executable and Python-module installations.
if YTDLP_PATH:
    YTDLP_CMD = [YTDLP_PATH]
elif importlib.util.find_spec("yt_dlp") is not None:
    YTDLP_CMD = [sys.executable, "-m", "yt_dlp"]
else:
    YTDLP_CMD = None


def display_command(command):
    if os.name == "nt":
        return subprocess.list2cmdline(command)
    return shlex.join(command)


def install_hint(package):
    if IS_TERMUX:
        return "pkg install " + package
    if SYSTEM == "windows":
        hints = {
            "yt-dlp": "winget install yt-dlp.yt-dlp",
            "ffmpeg": "winget install Gyan.FFmpeg",
        }
        return hints.get(package, "install " + package + " and add it to PATH")
    if SYSTEM == "linux":
        if package == "yt-dlp":
            return "python3 -m pip install -U yt-dlp (or use your distro package manager)"
        if package == "ffmpeg":
            return "sudo apt install ffmpeg  # Debian/Ubuntu"
    return "install " + package + " and add it to PATH"


def usage():
    print("Faa YTDownloader - universal CLI")
    print("\nUsage:")
    print("  fdl --test [URL]")
    print("  fdl mp3 <URL>")
    print("  fdl mp4 <URL>")
    print("  fdl help")
    print("\nExamples:")
    print("  fdl --test")
    print("  fdl mp3 https://www.youtube.com/watch?v=Oreek8z0yxk")
    print("  fdl mp4 https://youtu.be/Oreek8z0yxk")
    print("\nOutput: " + str(OUTDIR))
    print("Override: FDL_OUTPUT_DIR=/custom/path")


def run(command, timeout=60):
    try:
        process = subprocess.run(
            command, capture_output=True, text=True, timeout=timeout, check=False
        )
        return process.returncode, (process.stdout + process.stderr).strip()
    except FileNotFoundError:
        return 127, "command not found: " + command[0]
    except subprocess.TimeoutExpired:
        return 124, "command timed out"
    except Exception as exc:
        return 1, str(exc)


def extract_video_id(url):
    patterns = (
        r"[?&]v=([A-Za-z0-9_-]{11})",
        r"youtu\.be/([A-Za-z0-9_-]{11})",
        r"/shorts/([A-Za-z0-9_-]{11})",
        r"/embed/([A-Za-z0-9_-]{11})",
        r"/live/([A-Za-z0-9_-]{11})",
    )
    for pattern in patterns:
        match = re.search(pattern, url)
        if match:
            return match.group(1)
    return None


def do_test(url=TEST_URL):
    print("== FDL self-test ==")
    print("-- platform: %s (%s)" % (platform.system(), platform.machine()))
    print("-- python: %s" % sys.version.split()[0])
    print("-- runtime: %s" % ("Termux" if IS_TERMUX else platform.system()))

    print("-- tools:")
    if YTDLP_CMD:
        print("   yt-dlp: " + display_command(YTDLP_CMD))
        rc, output = run(YTDLP_CMD + ["--version"])
        print("   yt-dlp version: " + (output.splitlines()[0] if output else "unavailable (exit %d)" % rc))
    else:
        print("   yt-dlp: NOT FOUND")
        print("   install: " + install_hint("yt-dlp"))

    if FFMPEG_PATH:
        print("   ffmpeg: " + FFMPEG_PATH)
        rc, output = run([FFMPEG_PATH, "-version"])
        print("   ffmpeg version: " + (output.splitlines()[0] if output else "unavailable (exit %d)" % rc))
    else:
        print("   ffmpeg: NOT FOUND")
        print("   install: " + install_hint("ffmpeg"))

    print("-- output directory: " + str(OUTDIR))
    print("-- innertube (%s):" % url)
    video_id = extract_video_id(url)
    if not video_id:
        print("Video ID tidak ditemukan di URL.")
        return 2

    body = {
        "context": {"client": {
            "clientName": "ANDROID", "clientVersion": "21.26.364",
            "androidSdkVersion": 30, "hl": "en", "gl": "US",
            "osName": "Android", "osVersion": "11"
        }},
        "videoId": video_id, "racyCheckOk": True, "contentCheckOk": True
    }
    request = urllib.request.Request(
        "https://www.youtube.com/youtubei/v1/player?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8&prettyPrint=false",
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "User-Agent": "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip"
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            payload = json.load(response)
    except Exception as exc:
        print("HTTP gagal: %s: %s" % (type(exc).__name__, str(exc)[:200]))
        return 3

    playability = payload.get("playabilityStatus", {})
    print("status:", playability.get("status"), "| reason:", playability.get("reason", "-"))
    print("judul:", payload.get("videoDetails", {}).get("title"))
    streaming = payload.get("streamingData") or {}
    formats = streaming.get("formats", [])
    adaptive = streaming.get("adaptiveFormats", [])
    direct_count = sum(1 for item in formats + adaptive if item.get("url"))
    print("formats:", len(formats), "adaptive:", len(adaptive), "direct:", direct_count)
    for item in formats[:4]:
        print("  prog:", str(item.get("mimeType", ""))[:32], item.get("qualityLabel"),
              "url" if item.get("url") else "CIPHER")
    print("== selesai ==")
    return 0


def do_dl(fmt, url):
    if not url:
        print("URL belum diberikan.")
        usage()
        return 1
    if not YTDLP_CMD:
        print("yt-dlp tidak ditemukan.")
        print("Install: " + install_hint("yt-dlp"))
        return 1
    if fmt not in ("mp3", "mp4"):
        print("Format harus mp3 atau mp4.")
        return 1

    try:
        OUTDIR.mkdir(parents=True, exist_ok=True)
        if not OUTDIR.is_dir():
            raise OSError("output path bukan directory")
    except OSError as exc:
        print("Gagal mengakses output directory: %s" % exc)
        if IS_TERMUX:
            print("Jalankan: termux-setup-storage")
            print("Lalu izinkan akses storage dan ulangi command.")
        print("Atau set FDL_OUTPUT_DIR ke folder yang dapat ditulis.")
        return 1

    args = ["--no-playlist", "--ignore-errors", "--extractor-args",
            "youtube:player_client=android"]
    if FFMPEG_PATH:
        args += ["--ffmpeg-location", FFMPEG_PATH]
    if fmt == "mp3":
        if not FFMPEG_PATH:
            print("WARNING: ffmpeg tidak ditemukan; konversi MP3 mungkin gagal.")
            print("Install: " + install_hint("ffmpeg"))
        args += ["-x", "--audio-format", "mp3", "--audio-quality", "192K"]
    else:
        args += ["-f", "best"]

    args += ["-o", str(OUTDIR / "%(title)s.%(ext)s"), url]
    command = YTDLP_CMD + args
    print("$ " + display_command(command))
    print("Output: " + str(OUTDIR))
    try:
        process = subprocess.run(command, cwd=str(OUTDIR), check=False)
        return process.returncode
    except FileNotFoundError:
        print("yt-dlp executable tidak dapat dijalankan.")
        return 127
    except KeyboardInterrupt:
        print("\nDownload dibatalkan.")
        return 130
    except Exception as exc:
        print("Download gagal: %s" % exc)
        return 1


def main(argv):
    if len(argv) < 2:
        usage()
        return 0
    command = argv[1].lower()
    if command in ("--test", "test"):
        return do_test(argv[2] if len(argv) > 2 else TEST_URL)
    if command in ("mp3", "mp4"):
        return do_dl(command, argv[2] if len(argv) > 2 else "")
    if command in ("help", "-h", "--help"):
        usage()
        return 0
    print("Perintah tidak dikenal: %s" % argv[1])
    usage()
    return 1


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv))
    except KeyboardInterrupt:
        print("\nDibatalkan.")
        sys.exit(130)
