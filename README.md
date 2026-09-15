# FaaDL — Faa YTDownloader

Download audio (MP3) / video (MP4) dari YouTube, plus scan QR. Hasil selalu ke
`/sdcard/Download/FaaDL/` layaknya hasil download biasa.

Tiga varian, satu repo:

| Folder | Untuk siapa | Butuh root? |
|---|---|---|
| `FaaDL-termux/` | Versi pertama (Web UI, jalan di Termux) | Tidak |
| `FaaDL-Win/` | Versi Windows/Linux (Web UI, jalan di PC) | Tidak |
| `FaaDL-Standalone/` | APK langsung install (64-bit / 32-bit / universal) | Tidak (opsional, buat superpower) |
| `FaaDL-Module/` | Modul Magisk/KernelSU/APatch + aplikasi FYDL + CLI `fdl` | **Ya** |

## Clone

```bash
git clone https://github.com/FaaRamadhann/Faa-YDL.git
cd Faa-YDL
```

## 1. FaaDL-termux (versi awal)

Web UI (Manual / Scan QR / History), cocok untuk yang suka ngoprek.

Syarat: Termux + Node.js + `yt-dlp` + `ffmpeg`:
```bash
pkg install -y nodejs python ffmpeg
termux-setup-storage   # sekali saja, agar bisa tulis /sdcard
```

Cara pakai:
```bash
cd FaaDL-termux
npm install
node server.js
```

Buka di browser HP: `http://localhost:2080` (atau URL HTTPS di log untuk scan QR
dari kamera). Hasil: `/sdcard/Download/FaaDL/`.

> Syarat lengkap + troubleshooting: [`FaaDL-termux/REQUIREMENTS.md`](FaaDL-termux/REQUIREMENTS.md).

## 1b. FaaDL-Win (Windows / Linux)

Sama kayak versi Termux tapi jalan di PC. Hasil:
`%USERPROFILE%\Downloads\FaaDL` (Windows) atau `~/Downloads/FaaDL` (Linux).

Syarat: Node.js LTS + `yt-dlp` + `ffmpeg` (semuanya kebaca dari PATH).
Klik 2x `FaaDL-Win/start.bat`, atau manual `npm install` lalu `node server.js`.

> Syarat lengkap + troubleshooting: [`FaaDL-Win/REQUIREMENTS.md`](FaaDL-Win/REQUIREMENTS.md).

## 2. FaaDL-Standalone (APK non-module)

APK `com.faa.faadl` yang bawa sendiri ffmpeg + python + yt-dlp di dalamnya.
Tanpa root bisa jalan; kalau ada root, exec yang ditolak sistem otomatis
diulang via `su`.

> Syarat + troubleshooting build: [`FaaDL-Standalone/REQUIREMENTS.md`](FaaDL-Standalone/REQUIREMENTS.md).

Syarat build (tanpa Android Studio / Gradle): JDK 17+ dan Android SDK
build-tools + 1 platform (lihat `FaaDL-Standalone/`).

Cara build:
```bat
cd FaaDL-Standalone
python tools\prepare_bins.py      :: ffmpeg + yt-dlp (sekali saja)
python tools\prepare_python.py    :: runtime python (sekali saja)
python build.py all               :: hasil: build\faa-dl_v<ver>_{arm64,arm32,universal}.apk
```

Install APK sesuai HP (kebanyakan `*_arm64.apk`), buka, download MP3/MP4 atau
scan QR dari tab-nya. File built-in yang besar (`bins/`, hasil `build/`)
tidak ikut repo — diambil/dibangun ulang lewat script di atas.

## 3. FaaDL-Module (Magisk / KernelSU / APatch, arm64)

Paket lengkap: binary system-wide (`/system/bin/faadl-*`), aplikasi manager
FYDL (`com.faa.faadlmod`, otomatis terinstall), dan CLI `fdl`.

Syarat: HP arm64 yang sudah root (Magisk/KernelSU/APatch).

Cara build zip modul:
```bat
cd FaaDL-Module\manager && build.bat   :: hasilkan manager/build/manager.apk
cd ..\..
cd FaaDL-Module
python prepare_payload.py              :: rakit system/ dari FaaDL-Standalone/bins
python zip.py                          :: hasil: build/faadl-v<ver>.zip
```

Cara install: Magisk/KernelSU → Modul → Install dari penyimpanan → pilih
`faadl-v<ver>.zip` → **reboot**.

Cara pakai:
- Buka aplikasi **FYDL** (download MP3/MP4 + scan QR + tab Debug).
- Atau dari terminal/adb shell:
  ```sh
  fdl --test [URL]   # diagnostik python/ffmpeg/yt-dlp/innertube
  fdl mp3 <URL>      # download MP3
  fdl mp4 <URL>      # download video
  ```

## Lisensi

MIT — lihat `LICENSE`.
