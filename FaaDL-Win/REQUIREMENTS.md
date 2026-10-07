# Syarat FaaDL-Win (Windows / Linux)

Web UI yang sama seperti versi Termux, jalan di PC/laptop. Hasil download:
- Windows: `%USERPROFILE%\Downloads\FaaDL`
  (contoh: `C:\Users\faa_ramadhan\Downloads\FaaDL`)
- Linux: `~/Downloads/FaaDL`

## 1. Yang dibutuhkan

| Kebutuhan | Windows | Linux |
|---|---|---|
| Node.js LTS 18+ | https://nodejs.org (`node --version`) | `sudo apt install nodejs npm` |
| yt-dlp (di PATH) | `winget install yt-dlp.yt-dlp` | `pip install -U yt-dlp` |
| ffmpeg (di PATH) | `winget install Gyan.FFmpeg` | `sudo apt install ffmpeg` |
| Browser | Chrome/Edge/Firefox | apa saja |

> `yt-dlp` dan `ffmpeg` **wajib kebaca dari PATH**. Cek dengan menutup
> lalu membuka ulang terminal setelah install.

## 2. Cek cepat (semua harus ada outputnya)

```bat
node --version
yt-dlp --version
ffmpeg -version
```

## 3. Install sekali (Windows) — biar bisa ketik `fydl` di mana saja

Klik kanan `install.bat` → **Run as administrator** (atau klik 2x, nanti
minta admin sendiri). Script ini daftarkan folder FaaDL-Win ke **system
PATH**, cek node, dan jalankan `npm install`.

Sesudah itu tutup cmd lama, buka cmd **baru**, lalu:

```bat
fydl                :: jalankan server Web UI (buka browser otomatis)
fydl mp3 URL        :: download MP3 ke Downloads\FaaDL
fydl mp4 URL        :: download MP4 ke Downloads\FaaDL
fydl --test [URL]   :: cek node, yt-dlp, ffmpeg (+ judul video)
```

Ada juga `fdl` (Python, mirip `fdl` di modul Magisk) khusus download +
diagnostik innertube, hasilnya sama ke `Downloads\FaaDL`:

```bat
fdl mp3 URL         :: download MP3
fdl mp4 URL         :: download MP4
fdl --test [URL]    :: cek yt-dlp/ffmpeg + status innertube + judul video
```

Hapus dari PATH: klik kanan `uninstall.bat` → Run as administrator.

## 4. Jalanin manual (tanpa install)

```bat
cd FaaDL-Win
npm install
node server.js
```

Linux:

```bash
cd FaaDL-Win
npm install
node server.js
```

Buka `http://localhost:2080`. Hasil MP3/MP4 masuk folder Downloads\FaaDL.

## 5. Troubleshooting

| Gejala | Solusi |
|---|---|
| `'yt-dlp' is not recognized` | Belum di PATH — tutup/buka terminal, cek `where yt-dlp` |
| Download gagal semua | Update: `yt-dlp -U` (Windows) / `pip install -U yt-dlp` (Linux) |
| `port already in use` | Server lama nyangkut — tutup jendela CMD-nya / `taskkill /F /IM node.exe` |
| Hasil tidak di Downloads | Cek folder `downloads/` lokal + isi log server |
| `npm install` gagal | Internet / ulangi; pastikan Node LTS bukan Current aneh |

## Yang TIDAK dibutuhkan

- Root, HP Android, WSL, Docker.
