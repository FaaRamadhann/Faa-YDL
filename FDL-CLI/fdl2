#!/usr/bin/env python3
"""fdl - Cross-platform CLI for Faa YTDownloader.

CLI:
    fdl --test [URL]
    fdl mp3 URL [--quality 192K]
    fdl mp4 URL [--quality best]
    fdl playlist URL [mp3|mp4] [--quality ...]
    fdl help

Web:
    fdl -w [PORT]
    fdl --web [PORT]

Examples:
    fdl -w
    fdl -w 8080
    py fdl.py -w 9000

Supported:
    Windows, Linux and Termux.

Optional output override:
    FDL_OUTPUT_DIR=/path/to/folder
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
import threading
import time
import urllib.request
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse


# ============================================================
# CONFIG
# ============================================================

TEST_URL = "https://www.youtube.com/watch?v=Oreek8z0yxk"

SYSTEM = platform.system().lower()

IS_TERMUX = (
    "com.termux" in os.environ.get("PREFIX", "").lower()
    or bool(os.environ.get("TERMUX_VERSION"))
)

if os.environ.get("FDL_OUTPUT_DIR"):
    OUTDIR = Path(
        os.path.expandvars(
            os.path.expanduser(os.environ["FDL_OUTPUT_DIR"])
        )
    )
elif IS_TERMUX:
    OUTDIR = Path("/sdcard/Download/FaaDL")
else:
    OUTDIR = Path.home() / "Downloads" / "FaaDL"


YTDLP_PATH = shutil.which("yt-dlp")
FFMPEG_PATH = shutil.which("ffmpeg")


if YTDLP_PATH:
    YTDLP_CMD = [YTDLP_PATH]
elif importlib.util.find_spec("yt_dlp") is not None:
    YTDLP_CMD = [sys.executable, "-m", "yt_dlp"]
else:
    YTDLP_CMD = None


# ============================================================
# WEB JOB STORAGE
# ============================================================

WEB_JOBS = {}
WEB_JOBS_LOCK = threading.Lock()


def create_job():
    job_id = uuid.uuid4().hex[:12]

    job = {
        "id": job_id,
        "status": "queued",
        "progress": 0,
        "stage": "Queued",
        "log": [],
        "returncode": None,
        "created": time.time(),
        "finished": None,
    }

    with WEB_JOBS_LOCK:
        WEB_JOBS[job_id] = job

    return job_id


def update_job(job_id, **values):
    with WEB_JOBS_LOCK:
        job = WEB_JOBS.get(job_id)
        if not job:
            return

        job.update(values)


def append_job_log(job_id, line):
    line = line.rstrip()

    if not line:
        return

    with WEB_JOBS_LOCK:
        job = WEB_JOBS.get(job_id)

        if not job:
            return

        job["log"].append(line)

        # Keep memory bounded.
        if len(job["log"]) > 500:
            job["log"] = job["log"][-500:]


def get_job(job_id):
    with WEB_JOBS_LOCK:
        job = WEB_JOBS.get(job_id)

        if not job:
            return None

        return dict(job)


# ============================================================
# COMMON HELPERS
# ============================================================

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

        return hints.get(
            package,
            "install " + package + " and add it to PATH",
        )

    if SYSTEM == "linux":
        if package == "yt-dlp":
            return (
                "python3 -m pip install -U yt-dlp "
                "(or use your distro package manager)"
            )

        if package == "ffmpeg":
            return "sudo apt install ffmpeg  # Debian/Ubuntu"

    return "install " + package + " and add it to PATH"


def usage():
    print("Faa YTDownloader - universal CLI")

    print("\nUsage:")
    print("  fdl --test [URL]")
    print("  fdl mp3 <URL> [--quality 192K]")
    print("  fdl mp4 <URL> [--quality best]")
    print("  fdl playlist <URL> [mp3|mp4] [--quality ...]")
    print("  fdl -w [PORT]")
    print("  fdl --web [PORT]")
    print("  fdl help")

    print("\nExamples:")
    print(
        "  fdl mp3 "
        "https://www.youtube.com/watch?v=Oreek8z0yxk "
        "--quality 320K"
    )

    print(
        "  fdl mp4 "
        "https://youtu.be/Oreek8z0yxk "
        "--quality 1080p"
    )

    print(
        "  fdl playlist "
        "https://www.youtube.com/playlist?list=... mp3"
    )

    print("  fdl -w 8080")

    print("\nOutput: " + str(OUTDIR))
    print("Override: FDL_OUTPUT_DIR=/custom/path")


def run(command, timeout=60):
    try:
        process = subprocess.run(
            command,
            capture_output=True,
            text=True,
            timeout=timeout,
            check=False,
        )

        return (
            process.returncode,
            (process.stdout + process.stderr).strip(),
        )

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


def parse_args_options(argv):
    quality = None

    if "--quality" in argv:
        try:
            q_idx = argv.index("--quality")

            if q_idx + 1 < len(argv):
                quality = argv[q_idx + 1]

        except ValueError:
            pass

    return quality


# ============================================================
# SELF TEST
# ============================================================

def do_test(url=TEST_URL):
    print("== FDL self-test ==")

    print(
        "-- platform: %s (%s)"
        % (platform.system(), platform.machine())
    )

    print(
        "-- python: %s"
        % sys.version.split()[0]
    )

    print(
        "-- runtime: %s"
        % ("Termux" if IS_TERMUX else platform.system())
    )

    print("-- tools:")

    if YTDLP_CMD:
        print(
            "   yt-dlp: "
            + display_command(YTDLP_CMD)
        )

        rc, output = run(
            YTDLP_CMD + ["--version"]
        )

        print(
            "   yt-dlp version: "
            + (
                output.splitlines()[0]
                if output
                else "unavailable (exit %d)" % rc
            )
        )

    else:
        print("   yt-dlp: NOT FOUND")
        print(
            "   install: "
            + install_hint("yt-dlp")
        )

    if FFMPEG_PATH:
        print(
            "   ffmpeg: "
            + FFMPEG_PATH
        )

        rc, output = run(
            [FFMPEG_PATH, "-version"]
        )

        print(
            "   ffmpeg version: "
            + (
                output.splitlines()[0]
                if output
                else "unavailable (exit %d)" % rc
            )
        )

    else:
        print("   ffmpeg: NOT FOUND")
        print(
            "   install: "
            + install_hint("ffmpeg")
        )

    print(
        "-- output directory: "
        + str(OUTDIR)
    )

    print(
        "-- innertube (%s):"
        % url
    )

    video_id = extract_video_id(url)

    if not video_id:
        print("Video ID tidak ditemukan di URL.")
        return 2

    body = {
        "context": {
            "client": {
                "clientName": "ANDROID",
                "clientVersion": "21.26.364",
                "androidSdkVersion": 30,
                "hl": "en",
                "gl": "US",
                "osName": "Android",
                "osVersion": "11",
            }
        },
        "videoId": video_id,
        "racyCheckOk": True,
        "contentCheckOk": True,
    }

    request = urllib.request.Request(
        "https://www.youtube.com/youtubei/v1/player"
        "?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        "&prettyPrint=false",
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "User-Agent": (
                "com.google.android.youtube/21.26.364 "
                "(Linux; U; Android 11) gzip"
            ),
        },
    )

    try:
        with urllib.request.urlopen(
            request,
            timeout=30,
        ) as response:
            payload = json.load(response)

    except Exception as exc:
        print(
            "HTTP gagal: %s: %s"
            % (
                type(exc).__name__,
                str(exc)[:200],
            )
        )

        return 3

    playability = payload.get(
        "playabilityStatus",
        {},
    )

    print(
        "status:",
        playability.get("status"),
        "| reason:",
        playability.get("reason", "-"),
    )

    print(
        "judul:",
        payload.get("videoDetails", {}).get("title"),
    )

    streaming = payload.get(
        "streamingData"
    ) or {}

    formats = streaming.get(
        "formats",
        []
    )

    adaptive = streaming.get(
        "adaptiveFormats",
        []
    )

    direct_count = sum(
        1
        for item in formats + adaptive
        if item.get("url")
    )

    print(
        "formats:",
        len(formats),
        "adaptive:",
        len(adaptive),
        "direct:",
        direct_count,
    )

    for item in formats[:4]:
        print(
            "  prog:",
            str(item.get("mimeType", ""))[:32],
            item.get("qualityLabel"),
            "url"
            if item.get("url")
            else "CIPHER",
        )

    print("== selesai ==")

    return 0


# ============================================================
# DOWNLOAD CORE
# ============================================================

def build_download_command(
    fmt,
    url,
    is_playlist=False,
    quality=None,
):
    if fmt not in ("mp3", "mp4"):
        raise ValueError(
            "Format harus mp3 atau mp4."
        )

    if not url:
        raise ValueError(
            "URL belum diberikan."
        )

    if not YTDLP_CMD:
        raise RuntimeError(
            "yt-dlp tidak ditemukan."
        )

    try:
        OUTDIR.mkdir(
            parents=True,
            exist_ok=True,
        )

        if not OUTDIR.is_dir():
            raise OSError(
                "output path bukan directory"
            )

    except OSError as exc:
        raise OSError(
            "Gagal mengakses output directory: %s"
            % exc
        ) from exc

    args = [
        "--ignore-errors",
        "--extractor-args",
        "youtube:player_client=android",
    ]

    if not is_playlist:
        args.append("--no-playlist")

    archive_path = OUTDIR / ".downloaded_archive.txt"

    args += [
        "--download-archive",
        str(archive_path),
    ]

    if FFMPEG_PATH:
        args += [
            "--ffmpeg-location",
            FFMPEG_PATH,
        ]

    if fmt == "mp3":
        audio_quality = (
            quality
            if quality
            else "192K"
        )

        args += [
            "-x",
            "--audio-format",
            "mp3",
            "--audio-quality",
            audio_quality,
        ]

    else:
        vid_quality = (
            quality
            if quality
            else "best"
        )

        if vid_quality == "1080p":
            args += [
                "-f",
                "bv*[height<=1080]+ba/"
                "b[height<=1080]",
            ]

        elif vid_quality == "720p":
            args += [
                "-f",
                "bv*[height<=720]+ba/"
                "b[height<=720]",
            ]

        else:
            args += [
                "-f",
                "best",
            ]

    args += [
        "-o",
        str(
            OUTDIR
            / "%(title)s.%(ext)s"
        ),
        url,
    ]

    return YTDLP_CMD + args


def do_dl(
    fmt,
    url,
    is_playlist=False,
    quality=None,
):
    if not url:
        print("URL belum diberikan.")
        usage()
        return 1

    if not YTDLP_CMD:
        print("yt-dlp tidak ditemukan.")
        print(
            "Install: "
            + install_hint("yt-dlp")
        )
        return 1

    if fmt not in ("mp3", "mp4"):
        print("Format harus mp3 atau mp4.")
        return 1

    try:
        command = build_download_command(
            fmt,
            url,
            is_playlist,
            quality,
        )

    except Exception as exc:
        print(str(exc))

        if IS_TERMUX:
            print(
                "Jalankan: "
                "termux-setup-storage"
            )

        return 1

    if fmt == "mp3" and not FFMPEG_PATH:
        print(
            "WARNING: ffmpeg tidak ditemukan; "
            "konversi MP3 mungkin gagal."
        )

        print(
            "Install: "
            + install_hint("ffmpeg")
        )

    if is_playlist:
        print(
            "Mode Playlist/Channel diaktifkan."
        )

    print(
        "$ "
        + display_command(command)
    )

    print(
        "Output: "
        + str(OUTDIR)
    )

    try:
        process = subprocess.run(
            command,
            cwd=str(OUTDIR),
            check=False,
        )

        return process.returncode

    except FileNotFoundError:
        print(
            "yt-dlp executable tidak dapat "
            "dijalankan."
        )

        return 127

    except KeyboardInterrupt:
        print(
            "\nDownload dibatalkan."
        )

        return 130

    except Exception as exc:
        print(
            "Download gagal: %s"
            % exc
        )

        return 1


# ============================================================
# WEB DOWNLOAD WORKER
# ============================================================

def web_download_worker(
    job_id,
    fmt,
    url,
    is_playlist,
    quality,
):
    try:
        update_job(
            job_id,
            status="starting",
            stage="Preparing download",
        )

        append_job_log(
            job_id,
            "[FDL] Preparing download...",
        )

        command = build_download_command(
            fmt,
            url,
            is_playlist,
            quality,
        )

        if fmt == "mp3" and not FFMPEG_PATH:
            append_job_log(
                job_id,
                "[WARNING] ffmpeg not found. "
                "MP3 conversion may fail.",
            )

        append_job_log(
            job_id,
            "$ " + display_command(command),
        )

        update_job(
            job_id,
            status="downloading",
            stage="Downloading",
            progress=1,
        )

        process = subprocess.Popen(
            command,
            cwd=str(OUTDIR),
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1,
        )

        last_progress = 1

        for raw_line in iter(
            process.stdout.readline,
            "",
        ):
            line = raw_line.rstrip()

            if not line:
                continue

            append_job_log(
                job_id,
                line,
            )

            # yt-dlp progress line:
            # [download]  42.5% of ...
            match = re.search(
                r"\[download\]\s+(\d+(?:\.\d+)?)%",
                line,
            )

            if match:
                try:
                    progress = float(
                        match.group(1)
                    )

                    progress = max(
                        0,
                        min(100, progress),
                    )

                    last_progress = progress

                    update_job(
                        job_id,
                        progress=progress,
                        stage="Downloading",
                    )

                except ValueError:
                    pass

            elif "[ExtractAudio]" in line:
                update_job(
                    job_id,
                    stage="Converting audio",
                    progress=max(
                        last_progress,
                        95,
                    ),
                )

            elif "[Merger]" in line:
                update_job(
                    job_id,
                    stage="Merging video/audio",
                    progress=max(
                        last_progress,
                        95,
                    ),
                )

            elif "has already been recorded" in line:
                update_job(
                    job_id,
                    stage="Already downloaded",
                )

        process.wait()

        if process.returncode == 0:
            update_job(
                job_id,
                status="completed",
                stage="Completed",
                progress=100,
                returncode=0,
                finished=time.time(),
            )

            append_job_log(
                job_id,
                "[FDL] Download completed.",
            )

        else:
            update_job(
                job_id,
                status="failed",
                stage="Download failed",
                returncode=process.returncode,
                finished=time.time(),
            )

            append_job_log(
                job_id,
                "[FDL] yt-dlp exited with code %d."
                % process.returncode,
            )

    except Exception as exc:
        update_job(
            job_id,
            status="failed",
            stage="Error",
            returncode=1,
            finished=time.time(),
        )

        append_job_log(
            job_id,
            "[ERROR] %s: %s"
            % (
                type(exc).__name__,
                exc,
            ),
        )


# ============================================================
# WEB UI
# ============================================================

HTML_PAGE = r"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta
    name="viewport"
    content="width=device-width, initial-scale=1.0"
>
<title>Faa YTDownloader</title>

<style>
:root {
    --bg: #050b16;
    --bg2: #081426;
    --panel: rgba(12, 27, 48, 0.88);
    --panel2: rgba(15, 35, 61, 0.82);
    --border: #173a62;
    --border2: #245782;
    --blue: #38a9ff;
    --blue2: #1678c9;
    --cyan: #65d8ff;
    --text: #eaf6ff;
    --muted: #8da8c2;
    --success: #5ee7a2;
    --danger: #ff6b81;
    --shadow: rgba(0, 0, 0, 0.45);
}

* {
    box-sizing: border-box;
}

html,
body {
    margin: 0;
    min-height: 100%;
    font-family:
        Inter,
        system-ui,
        -apple-system,
        BlinkMacSystemFont,
        "Segoe UI",
        sans-serif;
    color: var(--text);
    background:
        radial-gradient(
            circle at 15% 10%,
            rgba(22, 120, 201, 0.20),
            transparent 30%
        ),
        radial-gradient(
            circle at 85% 15%,
            rgba(56, 169, 255, 0.12),
            transparent 25%
        ),
        linear-gradient(
            145deg,
            #030811 0%,
            #061225 48%,
            #071a30 100%
        );
}

body {
    padding: 32px 16px;
}

.container {
    width: min(920px, 100%);
    margin: auto;
}

.header {
    text-align: center;
    margin-bottom: 24px;
}

.logo {
    width: 64px;
    height: 64px;
    margin: 0 auto 14px;
    border-radius: 18px;
    display: grid;
    place-items: center;
    font-size: 28px;
    font-weight: 900;
    color: white;
    background:
        linear-gradient(
            145deg,
            #35b9ff,
            #1555a0
        );
    box-shadow:
        0 12px 45px rgba(21, 120, 201, 0.35);
}

h1 {
    margin: 0;
    font-size: 30px;
    letter-spacing: -0.8px;
}

.subtitle {
    margin-top: 7px;
    color: var(--muted);
    font-size: 14px;
}

.card {
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: 20px;
    padding: 22px;
    margin-bottom: 18px;
    box-shadow:
        0 20px 60px var(--shadow),
        inset 0 1px 0 rgba(255,255,255,0.03);
    backdrop-filter: blur(12px);
}

label {
    display: block;
    color: #b7cce0;
    font-size: 13px;
    font-weight: 700;
    margin-bottom: 8px;
}

input,
select {
    width: 100%;
    border: 1px solid var(--border);
    border-radius: 12px;
    background: #071426;
    color: var(--text);
    outline: none;
    padding: 13px 14px;
    font-size: 14px;
    transition: 0.18s ease;
}

input:focus,
select:focus {
    border-color: var(--blue);
    box-shadow:
        0 0 0 3px rgba(56, 169, 255, 0.10);
}

.url-row {
    margin-bottom: 18px;
}

.options {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 14px;
}

.mode {
    display: flex;
    gap: 8px;
    margin-bottom: 18px;
}

.mode button {
    flex: 1;
}

button {
    border: 1px solid var(--border2);
    border-radius: 11px;
    padding: 12px 16px;
    cursor: pointer;
    color: #ccecff;
    background: #0a1b31;
    font-weight: 800;
    transition:
        transform 0.15s ease,
        border-color 0.15s ease,
        background 0.15s ease;
}

button:hover {
    border-color: var(--blue);
    background: #0d2744;
}

button:active {
    transform: translateY(1px);
}

.mode button.active {
    color: white;
    border-color: var(--blue);
    background:
        linear-gradient(
            135deg,
            #1266a8,
            #0d3760
        );
}

.download-btn {
    width: 100%;
    margin-top: 18px;
    padding: 14px;
    font-size: 15px;
    color: white;
    border-color: #238ad1;
    background:
        linear-gradient(
            135deg,
            #1688d3,
            #125496
        );
    box-shadow:
        0 10px 28px rgba(22, 120, 201, 0.25);
}

.download-btn:hover {
    background:
        linear-gradient(
            135deg,
            #1b98e8,
            #1564ad
        );
}

.download-btn:disabled {
    cursor: wait;
    opacity: 0.55;
}

.progress-wrap {
    display: none;
}

.status-line {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 10px;
}

.status {
    color: var(--cyan);
    font-size: 13px;
    font-weight: 800;
}

.percent {
    color: white;
    font-weight: 900;
    font-variant-numeric: tabular-nums;
}

.progress {
    height: 10px;
    overflow: hidden;
    border-radius: 99px;
    background: #071321;
    border: 1px solid #12304f;
}

.progress-bar {
    height: 100%;
    width: 0%;
    border-radius: inherit;
    background:
        linear-gradient(
            90deg,
            #1678c9,
            #46c7ff
        );
    box-shadow:
        0 0 18px rgba(70, 199, 255, 0.35);
    transition: width 0.25s ease;
}

.log {
    height: 250px;
    overflow: auto;
    margin-top: 16px;
    padding: 14px;
    border-radius: 12px;
    border: 1px solid #102b48;
    background: #030914;
    color: #8eb0cb;
    font-family:
        "Cascadia Code",
        "Consolas",
        monospace;
    font-size: 12px;
    line-height: 1.55;
    white-space: pre-wrap;
    word-break: break-word;
}

.info {
    color: var(--muted);
    font-size: 12px;
    line-height: 1.6;
}

.footer {
    text-align: center;
    color: #52718d;
    font-size: 12px;
    margin-top: 22px;
}

.badge {
    display: inline-block;
    padding: 4px 8px;
    border-radius: 99px;
    border: 1px solid #1a476e;
    background: #081b30;
    color: #76b8e8;
    font-size: 11px;
    font-weight: 800;
}

.hidden {
    display: none !important;
}

@media (max-width: 650px) {
    body {
        padding: 18px 10px;
    }

    .card {
        padding: 16px;
        border-radius: 16px;
    }

    .options {
        grid-template-columns: 1fr;
    }

    h1 {
        font-size: 25px;
    }
}
</style>
</head>

<body>
<div class="container">

    <div class="header">
        <div class="logo">F</div>
        <h1>Faa YTDownloader</h1>
        <div class="subtitle">
            FDL Web Interface
            <span class="badge">Faa Ramadhan</span>
        </div>
    </div>

    <div class="card">

        <div class="url-row">
            <label for="url">YouTube URL</label>
            <input
                id="url"
                type="url"
                placeholder="https://www.youtube.com/watch?v=..."
                autocomplete="off"
            >
        </div>

        <div class="mode">
            <button
                id="mp3Btn"
                class="active"
                onclick="setMode('mp3')"
            >
                MP3
            </button>

            <button
                id="mp4Btn"
                onclick="setMode('mp4')"
            >
                MP4
            </button>

            <button
                id="playlistBtn"
                onclick="setPlaylist()"
            >
                Playlist
            </button>
        </div>

        <div class="options">

            <div>
                <label for="format">Format</label>

                <select id="format">
                    <option value="mp3">MP3 Audio</option>
                    <option value="mp4">MP4 Video</option>
                </select>
            </div>

            <div>
                <label for="quality">Quality</label>

                <select id="quality">
                </select>
            </div>

        </div>

        <button
            id="downloadBtn"
            class="download-btn"
            onclick="startDownload()"
        >
            Download
        </button>

        <div
            id="error"
            class="info hidden"
            style="margin-top:12px;color:#ff8295"
        ></div>

    </div>

    <div
        id="progressCard"
        class="card progress-wrap"
    >
        <div class="status-line">
            <span
                id="status"
                class="status"
            >
                Preparing...
            </span>

            <span
                id="percent"
                class="percent"
            >
                0%
            </span>
        </div>

        <div class="progress">
            <div
                id="progressBar"
                class="progress-bar"
            ></div>
        </div>

        <div
            id="log"
            class="log"
        ></div>
    </div>

    <div class="footer">
        Faa YTDownloader
    </div>

</div>

<script>
let mode = "mp3";
let playlist = false;
let currentJob = null;
let pollTimer = null;

const formatEl = document.getElementById("format");
const qualityEl = document.getElementById("quality");

function updateQuality() {
    qualityEl.innerHTML = "";

    if (formatEl.value === "mp3") {
        [
            ["128K", "128 Kbps"],
            ["192K", "192 Kbps"],
            ["320K", "320 Kbps"]
        ].forEach(([value, label]) => {
            const option = document.createElement("option");
            option.value = value;
            option.textContent = label;
            qualityEl.appendChild(option);
        });
    } else {
        [
            ["best", "Best Available"],
            ["1080p", "1080p"],
            ["720p", "720p"]
        ].forEach(([value, label]) => {
            const option = document.createElement("option");
            option.value = value;
            option.textContent = label;
            qualityEl.appendChild(option);
        });
    }
}

function setMode(newMode) {
    mode = newMode;
    playlist = false;

    document
        .getElementById("mp3Btn")
        .classList.toggle(
            "active",
            newMode === "mp3"
        );

    document
        .getElementById("mp4Btn")
        .classList.toggle(
            "active",
            newMode === "mp4"
        );

    document
        .getElementById("playlistBtn")
        .classList.remove("active");

    formatEl.value = newMode;
    updateQuality();
}

function setPlaylist() {
    playlist = true;

    document
        .getElementById("mp3Btn")
        .classList.remove("active");

    document
        .getElementById("mp4Btn")
        .classList.remove("active");

    document
        .getElementById("playlistBtn")
        .classList.add("active");
}

formatEl.addEventListener(
    "change",
    () => {
        mode = formatEl.value;
        updateQuality();
    }
);

function showError(message) {
    const el = document.getElementById("error");

    el.textContent = message;
    el.classList.remove("hidden");
}

function clearError() {
    const el = document.getElementById("error");

    el.textContent = "";
    el.classList.add("hidden");
}

function appendLog(lines) {
    const log = document.getElementById("log");

    log.textContent = lines.join("\n");

    log.scrollTop = log.scrollHeight;
}

function setProgress(value) {
    value = Math.max(
        0,
        Math.min(
            100,
            Number(value) || 0
        )
    );

    document
        .getElementById("progressBar")
        .style.width = value + "%";

    document
        .getElementById("percent")
        .textContent =
        Math.round(value) + "%";
}

async function startDownload() {
    clearError();

    const url =
        document
            .getElementById("url")
            .value
            .trim();

    if (!url) {
        showError(
            "Masukkan YouTube URL terlebih dahulu."
        );
        return;
    }

    const format = formatEl.value;
    const quality = qualityEl.value;

    const button =
        document.getElementById(
            "downloadBtn"
        );

    button.disabled = true;
    button.textContent = "Starting...";

    document
        .getElementById("progressCard")
        .style.display = "block";

    document
        .getElementById("log")
        .textContent = "";

    setProgress(0);

    try {
        const response = await fetch(
            "/api/download",
            {
                method: "POST",
                headers: {
                    "Content-Type":
                        "application/json"
                },
                body: JSON.stringify({
                    url: url,
                    format: format,
                    quality: quality,
                    playlist: playlist
                })
            }
        );

        const data = await response.json();

        if (!response.ok) {
            throw new Error(
                data.error ||
                "Failed to start download."
            );
        }

        currentJob = data.job_id;

        button.textContent = "Downloading...";

        pollJob();

    } catch (error) {
        button.disabled = false;
        button.textContent = "Download";

        showError(error.message);
    }
}

async function pollJob() {
    if (!currentJob) {
        return;
    }

    try {
        const response = await fetch(
            "/api/status?id=" +
            encodeURIComponent(currentJob)
        );

        const data = await response.json();

        if (!response.ok) {
            throw new Error(
                data.error ||
                "Failed to read job status."
            );
        }

        document
            .getElementById("status")
            .textContent = data.stage;

        setProgress(data.progress);

        appendLog(data.log || []);

        if (
            data.status === "completed"
            || data.status === "failed"
        ) {
            const button =
                document.getElementById(
                    "downloadBtn"
                );

            button.disabled = false;
            button.textContent =
                data.status === "completed"
                    ? "Download Again"
                    : "Try Again";

            if (data.status === "failed") {
                showError(
                    "Download gagal. Lihat log untuk detail."
                );
            }

            currentJob = null;

            if (pollTimer) {
                clearTimeout(pollTimer);
                pollTimer = null;
            }

            return;
        }

        pollTimer = setTimeout(
            pollJob,
            700
        );

    } catch (error) {
        showError(error.message);

        const button =
            document.getElementById(
                "downloadBtn"
            );

        button.disabled = false;
        button.textContent = "Try Again";

        currentJob = null;
    }
}

updateQuality();
</script>

</body>
</html>
"""


# ============================================================
# WEB SERVER
# ============================================================

class FDLRequestHandler(BaseHTTPRequestHandler):

    server_version = "FDLWeb/1.0"

    def log_message(self, format, *args):
        # Keep HTTP access logs quiet.
        return

    def send_json(self, status, data):
        payload = json.dumps(
            data,
            ensure_ascii=False,
        ).encode("utf-8")

        self.send_response(status)

        self.send_header(
            "Content-Type",
            "application/json; charset=utf-8",
        )

        self.send_header(
            "Content-Length",
            str(len(payload)),
        )

        self.send_header(
            "Cache-Control",
            "no-store",
        )

        self.end_headers()

        self.wfile.write(payload)

    def send_html(self, html):
        payload = html.encode("utf-8")

        self.send_response(200)

        self.send_header(
            "Content-Type",
            "text/html; charset=utf-8",
        )

        self.send_header(
            "Content-Length",
            str(len(payload)),
        )

        self.send_header(
            "Cache-Control",
            "no-store",
        )

        self.end_headers()

        self.wfile.write(payload)

    def do_GET(self):
        parsed = urlparse(self.path)

        if parsed.path in ("/", "/index.html"):
            self.send_html(HTML_PAGE)
            return

        if parsed.path == "/api/status":
            query = parse_qs(parsed.query)
            job_id = (
                query.get("id", [None])[0]
            )

            if not job_id:
                self.send_json(
                    400,
                    {
                        "error":
                            "Missing job id."
                    },
                )

                return

            job = get_job(job_id)

            if not job:
                self.send_json(
                    404,
                    {
                        "error":
                            "Job not found."
                    },
                )

                return

            self.send_json(
                200,
                job,
            )

            return

        self.send_json(
            404,
            {
                "error": "Not found."
            },
        )

    def do_POST(self):
        parsed = urlparse(self.path)

        if parsed.path != "/api/download":
            self.send_json(
                404,
                {
                    "error": "Not found."
                },
            )

            return

        try:
            content_length = int(
                self.headers.get(
                    "Content-Length",
                    "0",
                )
            )

        except ValueError:
            content_length = 0

        if content_length <= 0:
            self.send_json(
                400,
                {
                    "error":
                        "Request body is empty."
                },
            )

            return

        if content_length > 64 * 1024:
            self.send_json(
                413,
                {
                    "error":
                        "Request body too large."
                },
            )

            return

        try:
            raw = self.rfile.read(
                content_length
            )

            data = json.loads(
                raw.decode("utf-8")
            )

        except Exception:
            self.send_json(
                400,
                {
                    "error":
                        "Invalid JSON body."
                },
            )

            return

        url = str(
            data.get("url", "")
        ).strip()

        fmt = str(
            data.get("format", "mp3")
        ).lower()

        quality = data.get("quality")

        if quality is not None:
            quality = str(quality)

        is_playlist = bool(
            data.get("playlist", False)
        )

        if not url:
            self.send_json(
                400,
                {
                    "error":
                        "URL belum diberikan."
                },
            )

            return

        if fmt not in ("mp3", "mp4"):
            self.send_json(
                400,
                {
                    "error":
                        "Format harus mp3 atau mp4."
                },
            )

            return

        if not YTDLP_CMD:
            self.send_json(
                503,
                {
                    "error":
                        "yt-dlp tidak ditemukan. "
                        + install_hint("yt-dlp")
                },
            )

            return

        if fmt == "mp3":
            allowed_quality = (
                "128K",
                "192K",
                "320K",
            )

        else:
            allowed_quality = (
                "best",
                "1080p",
                "720p",
            )

        if quality not in allowed_quality:
            quality = (
                "192K"
                if fmt == "mp3"
                else "best"
            )

        try:
            OUTDIR.mkdir(
                parents=True,
                exist_ok=True,
            )

            if not OUTDIR.is_dir():
                raise OSError(
                    "Output path bukan directory."
                )

        except OSError as exc:
            self.send_json(
                500,
                {
                    "error": str(exc)
                },
            )

            return

        job_id = create_job()

        worker = threading.Thread(
            target=web_download_worker,
            args=(
                job_id,
                fmt,
                url,
                is_playlist,
                quality,
            ),
            daemon=True,
        )

        worker.start()

        self.send_json(
            202,
            {
                "job_id": job_id,
                "status": "queued",
            },
        )


class FDLHTTPServer(
    ThreadingHTTPServer
):
    daemon_threads = True
    allow_reuse_address = True


def get_local_addresses(port):
    addresses = []

    try:
        hostname = platform.node()

        _, _, ips = (
            __import__("socket")
            .gethostbyname_ex(hostname)
        )

        addresses.extend(ips)

    except Exception:
        pass

    # Remove duplicates and loopback.
    result = []

    for ip in addresses:
        if ip.startswith("127."):
            continue

        if ip not in result:
            result.append(ip)

    result.sort()

    return result


def run_web_server(port):
    if port < 1 or port > 65535:
        print(
            "Invalid port: %d"
            % port
        )

        return 1

    try:
        server = FDLHTTPServer(
            ("0.0.0.0", port),
            FDLRequestHandler,
        )

    except OSError as exc:
        print(
            "Gagal membuka port %d: %s"
            % (
                port,
                exc,
            )
        )

        print(
            "Port mungkin sedang digunakan "
            "atau diblokir firewall."
        )

        return 1

    print()
    print("=" * 54)
    print("          FAA YTDOWNLOADER - WEB")
    print("=" * 54)
    print()
    print(
        "  Bind     : 0.0.0.0"
    )
    print(
        "  Port     : %d"
        % port
    )
    print(
        "  Output   : %s"
        % OUTDIR
    )
    print()

    print(
        "  Local:"
    )

    print(
        "    http://127.0.0.1:%d"
        % port
    )

    addresses = get_local_addresses(
        port
    )

    if addresses:
        print()
        print(
            "  LAN:"
        )

        for ip in addresses:
            print(
                "    http://%s:%d"
                % (
                    ip,
                    port,
                )
            )

    print()
    print(
        "  Press Ctrl+C to stop."
    )
    print()
    print("=" * 54)
    print()

    try:
        server.serve_forever()

    except KeyboardInterrupt:
        print(
            "\nStopping FDL Web Server..."
        )

    finally:
        server.shutdown()
        server.server_close()

    print("FDL Web Server stopped.")

    return 0


# ============================================================
# CLI
# ============================================================

def parse_web_port(argv):
    """
    Supports:

        fdl -w
        fdl -w 8080
        fdl --web
        fdl --web 9000
    """

    try:
        index = argv.index("-w")

    except ValueError:
        try:
            index = argv.index("--web")

        except ValueError:
            return 8080

    if index + 1 >= len(argv):
        return 8080

    candidate = argv[index + 1]

    if candidate.startswith("-"):
        return 8080

    try:
        return int(candidate)

    except ValueError:
        print(
            "Invalid web port: %s"
            % candidate
        )

        return -1


def main(argv):
    if len(argv) < 2:
        usage()
        return 0

    command = argv[1].lower()

    # --------------------------------------------------------
    # WEB
    # --------------------------------------------------------

    if command in (
        "-w",
        "--web",
    ):
        port = parse_web_port(argv)

        if port < 0:
            return 1

        return run_web_server(port)

    # --------------------------------------------------------
    # TEST
    # --------------------------------------------------------

    if command in (
        "--test",
        "test",
    ):
        return do_test(
            argv[2]
            if (
                len(argv) > 2
                and not argv[2].startswith("-")
            )
            else TEST_URL
        )

    # --------------------------------------------------------
    # MP3 / MP4
    # --------------------------------------------------------

    if command in (
        "mp3",
        "mp4",
    ):
        url = (
            argv[2]
            if (
                len(argv) > 2
                and not argv[2].startswith("-")
            )
            else ""
        )

        quality = parse_args_options(
            argv
        )

        return do_dl(
            command,
            url,
            is_playlist=False,
            quality=quality,
        )

    # --------------------------------------------------------
    # PLAYLIST
    # --------------------------------------------------------

    if command == "playlist":
        url = (
            argv[2]
            if (
                len(argv) > 2
                and not argv[2].startswith("-")
            )
            else ""
        )

        fmt = "mp4"

        if (
            len(argv) > 3
            and argv[3].lower()
            in ("mp3", "mp4")
        ):
            fmt = argv[3].lower()

        quality = parse_args_options(
            argv
        )

        return do_dl(
            fmt,
            url,
            is_playlist=True,
            quality=quality,
        )

    # --------------------------------------------------------
    # HELP
    # --------------------------------------------------------

    if command in (
        "help",
        "-h",
        "--help",
    ):
        usage()
        return 0

    print(
        "Perintah tidak dikenal: %s"
        % argv[1]
    )

    usage()

    return 1


if __name__ == "__main__":
    try:
        sys.exit(
            main(sys.argv)
        )

    except KeyboardInterrupt:
        print(
            "\nDibatalkan."
        )

        sys.exit(130)
