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

## 3. Jalanin

Windows — klik 2x `start.bat` (otomatis `npm install` + buka browser),
atau manual:

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

## 4. Troubleshooting

| Gejala | Solusi |
|---|---|
| `'yt-dlp' is not recognized` | Belum di PATH — tutup/buka terminal, cek `where yt-dlp` |
| Download gagal semua | Update: `yt-dlp -U` (Windows) / `pip install -U yt-dlp` (Linux) |
| `port already in use` | Server lama nyangkut — tutup jendela CMD-nya / `taskkill /F /IM node.exe` |
| Hasil tidak di Downloads | Cek folder `downloads/` lokal + isi log server |
| `npm install` gagal | Internet / ulangi; pastikan Node LTS bukan Current aneh |

## Yang TIDAK dibutuhkan

- Root, HP Android, WSL, Docker.
