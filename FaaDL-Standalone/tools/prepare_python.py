"""Ambil runtime Python Android per-ABI dari AAR youtubedl-android (Maven Central).
Hasil di bins/{arm64,arm32}/py/ + bins/common/yt-dlp-aar (referensi).
AAR sudah teruji kombinasinya oleh upstream (Seal dkk).
"""
import os
import sys
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BINS = os.path.join(ROOT, "bins")
TMP = os.path.join(BINS, "_tmp")
AAR_URL = ("https://repo1.maven.org/maven2/io/github/junkfood02/youtubedl-android"
           "/library/0.18.1/library-0.18.1.aar")
AAR_PATH = os.path.join(TMP, "ytdl-library.aar")

WANT = {
    "arm64": {
        "jni/arm64-v8a/libpython.so": "libpython.so",
        "jni/arm64-v8a/libpython.zip.so": "stdlib.zip",
        "jni/arm64-v8a/libqjs.so": "libqjs.so",
    },
    "arm32": {
        "jni/armeabi-v7a/libpython.so": "libpython.so",
        "jni/armeabi-v7a/libpython.zip.so": "stdlib.zip",
        "jni/armeabi-v7a/libqjs.so": "libqjs.so",
    },
}


def main():
    os.makedirs(TMP, exist_ok=True)
    if not os.path.exists(AAR_PATH):
        print("DL AAR...", flush=True)
        req = urllib.request.Request(AAR_URL, headers={"User-Agent": "FaaDL/1.0"})
        with urllib.request.urlopen(req, timeout=180) as r, open(AAR_PATH, "wb") as f:
            while True:
                b = r.read(1 << 20)
                if not b:
                    break
                f.write(b)
        print(f"  OK {os.path.getsize(AAR_PATH) // 1024} KB", flush=True)
    else:
        print(f"AAR sudah ada ({os.path.getsize(AAR_PATH) // 1024} KB)", flush=True)

    z = zipfile.ZipFile(AAR_PATH)
    for abi, mapping in WANT.items():
        d = os.path.join(BINS, abi, "py")
        os.makedirs(d, exist_ok=True)
        for inner, out_name in mapping.items():
            out = os.path.join(d, out_name)
            if os.path.exists(out) and os.path.getsize(out) > 0:
                print(f"  skip {abi}/py/{out_name}", flush=True)
                continue
            with z.open(inner) as s, open(out, "wb") as f:
                f.write(s.read())
            print(f"  {abi}/py/{out_name}  {os.path.getsize(out) // 1024} KB", flush=True)

    # yt-dlp bawaan AAR sebagai referensi versi yang teruji
    ref = os.path.join(BINS, "common", "yt-dlp-aar")
    if not os.path.exists(ref):
        with z.open("res/raw/ytdlp") as s, open(ref, "wb") as f:
            f.write(s.read())
        print(f"  common/yt-dlp-aar  {os.path.getsize(ref) // 1024} KB", flush=True)
    print("SELESAI prepare python.", flush=True)


if __name__ == "__main__":
    main()
