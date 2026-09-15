# Syarat build FaaDL-Standalone (APK tanpa Android Studio / Gradle)

Build langsung pakai **JDK + Android SDK build-tools** via `build.py`
(adaptasi `ex-build.py` Example-Build: `javac → jar → d8 → aapt →
keystore → zipalign → apksigner`).

## 1. JDK 17+ (butuh: `javac.exe`, `jar.exe`, `keytool.exe`)

- Yang teruji: JDK 21 LTS.
- Cek: `javac -version`
- Download: https://adoptium.net
- JRE saja **tidak cukup**, harus JDK.

## 2. Android SDK: 1 build-tools + 1 platform

Butuh: `aapt`, `d8`, `zipalign`, `apksigner`.

- Yang teruji: `build-tools 35.0.0` + `platforms;android-34`.
- Tanpa Android Studio, install via `sdkmanager`:
  ```bat
  sdkmanager "platform-tools" "platforms;android-34" "build-tools;35.0.0"
  ```
- Download `commandlinetools`:
  https://developer.android.com/studio#command-line-tools-only
- Sesuaikan path `BUILD_TOOLS`, `ANDROID_JAR`, `JAVA_HOME` di blok KONFIG
  `build.py` bila SDK/JDK-mu beda lokasi.

## 3. Python 3.8+ (untuk `build.py`, tanpa library tambahan)

- Cek: `python --version`

## 4. Windows

Script ditulis untuk Windows (`.exe` / `.bat`). Di Linux/Mac sesuaikan
ekstensi tool di KONFIG.

## Cek cepat (semua harus ada outputnya)

```bat
javac -version
keytool -help
C:\AndroidSDK\build-tools\35.0.0\aapt.exe version
C:\AndroidSDK\build-tools\35.0.0\d8.bat --version
dir C:\AndroidSDK\platforms\android-34\android.jar
python --version
```

## Alur build penuh (dari fresh clone)

```bat
cd FaaDL-Standalone
python tools\prepare_bins.py      :: ffmpeg + yt-dlp (sekali saja, butuh internet)
python tools\prepare_python.py    :: runtime python Android (sekali saja)
python build.py all               :: 3 APK, atau: arm64 | arm32 | universal
```

Hasil di `build/`:
`faa-dl_v<ver>_arm64.apk` (64-bit), `faa-dl_v<ver>_arm32.apk` (32-bit),
`faa-dl_v<ver>_universal.apk` (dua-duanya, aplikasi pilih otomatis).

Versi APK ngikut `version` di `package.json`.

## Troubleshooting

| Gejala | Solusi |
|---|---|
| `javac is not recognized` | `JAVA_HOME` salah / belum install JDK |
| `android.jar tidak ada` | Path `ANDROID_JAR` salah, cek `dir` |
| `aapt/d8/zipalign/apksigner tidak ditemukan` | `BUILD_TOOLS` salah / build-tools belum install |
| `Tidak ada file .java di src/` | Jalankan dari root `FaaDL-Standalone` |
| APK gagal update di HP | Backup `debug.keystore` — update wajib key sama |
| Gagal download saat build prepare | Internet putus / GitHub limit — ulangi scriptnya |

## Yang TIDAK dibutuhkan

- Android Studio / Gradle, `adb` (cuma pas install ke HP),
  internet (cuma pas download awal via prepare_*).
