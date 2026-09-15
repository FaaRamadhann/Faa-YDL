# Syarat FaaDL-termux

Jalankan server Web UI di Termux (HP jadi server download).

## 1. HP & aplikasi

- Android 7.0+ (disarankan 8.0+), RAM bebas 200MB+, sisa storage cukup
  untuk hasil download.
- **Termux dari F-Droid** (https://f-droid.org/packages/com.termux/),
  BUKAN dari Play Store (versi Play sudah lama mati dan `pkg` rusak).
- Satu jaringan WiFi antara HP server dan HP/PC yang buka Web UI
  (khusus scan QR kamera).

## 2. Paket Termux

```bash
pkg update && pkg upgrade -y
pkg install -y nodejs python ffmpeg git
pip install -U yt-dlp
termux-setup-storage   # sekali saja, WAJIB agar bisa tulis /sdcard
```

| Paket | Buat apa | Minimal |
|---|---|---|
| `nodejs` | jalanin `server.js` | 18+ (`node --version`) |
| `python` + `yt-dlp` | mesin download (dipanggil server) | yt-dlp terbaru (`yt-dlp --version`) |
| `ffmpeg` | convert MP3 / merge MP4 | 5+ (`ffmpeg -version`) |
| `git` | clone repo ini | apa saja |
| `termux-setup-storage` | izin tulis `/sdcard/Download/FaaDL` | sekali saja |

## 3. Cek cepat (semua harus ada outputnya)

```bash
node --version
yt-dlp --version
ffmpeg -version
ls /sdcard/Download
```

## 4. Jalanin

```bash
cd FaaDL-termux
npm install
node server.js
```

Buka `http://localhost:2080` di browser HP yang sama, atau URL
`http://<IP-HP>:2080` dari perangkat lain. Untuk scan QR kamera pakai
URL HTTPS (`https://<IP-HP>:2081`) lalu Proceed unsafe di browser.

## 5. Alias `fydl` (biar cepat)

Supaya bisa jalanin server dari mana saja cukup ketik `fydl`,
tambahkan alias ke `~/.bashrc` (sekali saja):

```bash
echo "alias fydl='cd ~/Faa-YDL/FaaDL-termux && node server.js'" >> ~/.bashrc
source ~/.bashrc
```

> Sesuaikan path-nya kalau clone di lokasi lain. Mau lebih sakti, pakai
> function biar bisa `fydl stop` juga:
>
> ```bash
> fydl() {
>   case "$1" in
>     stop) pkill -f "node server.js" && echo "server berhenti" ;;
>     *) cd ~/Faa-YDL/FaaDL-termux && node server.js ;;
>   esac
> }
> ```

## 6. Troubleshooting

| Gejala | Solusi |
|---|---|
| `EACCES /sdcard/...` | Jalankan `termux-setup-storage`, pilih Izinkan |
| `yt-dlp: command not found` | `pip install -U yt-dlp`, restart Termux |
| `port already in use` | Ada server nyangkut: `pkill -f server.js`, lalu ulang |
| Kamera tidak bisa (HTTP) | Wajib HTTPS `:2081`, atau scan dari gambar |
| Download gagal semua | Update dulu: `pip install -U yt-dlp` (YouTube sering berubah) |
| `npm install` lambat/gagal | Ganti registry / pakai WiFi stabil, ulangi |

## 7. Yang TIDAK dibutuhkan

- Root, Magisk, APK lain, laptop/PC (opsional, cuma buat buka UI).
