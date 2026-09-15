"""Ambil Python 3.14 arm64 dari modul Magisk-Python milik sendiri.
Sumber: faapython-3.14.7-arm64-Magisk.zip (GitHub FaaRamadhann/Magisk-Python).
Hasil di bins/arm64/faapy/{bin,lib64} (siap di-bundle ke assets/faapy/).
"""
import os
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BINS = os.path.join(ROOT, "bins")
TMP = os.path.join(BINS, "_tmp")
ZIP_URL = ("https://github.com/FaaRamadhann/Magisk-Python"
           "/raw/main/faapython-3.14.7-arm64-Magisk.zip")
ZIP_PATH = os.path.join(TMP, "faapython-magisk.zip")
DEST = os.path.join(BINS, "arm64", "faapy")

SKIP_SUBSTR = ("ensurepip/_bundled", "__pycache__")


def main():
    os.makedirs(TMP, exist_ok=True)
    if not os.path.exists(ZIP_PATH):
        print("DL faapython...", flush=True)
        req = urllib.request.Request(ZIP_URL, headers={"User-Agent": "FaaDL/1.0"})
        with urllib.request.urlopen(req, timeout=300) as r, open(ZIP_PATH, "wb") as f:
            while True:
                b = r.read(1 << 20)
                if not b:
                    break
                f.write(b)
    print(f"zip: {os.path.getsize(ZIP_PATH) // 1024} KB", flush=True)
    z = zipfile.ZipFile(ZIP_PATH)
    n = 0
    total = 0
    for name in z.namelist():
        if not (name.startswith("system/bin/") or name.startswith("system/lib64/")):
            continue
        if name.endswith("/"):
            continue
        if any(s in name for s in SKIP_SUBSTR):
            continue
        rel = name[len("system/"):]  # bin/... atau lib64/...
        out = os.path.join(DEST, *rel.split("/"))
        if os.path.exists(out) and os.path.getsize(out) > 0:
            continue
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with z.open(name) as s, open(out, "wb") as f:
            f.write(s.read())
        n += 1
        total += os.path.getsize(out)
    print(f"SELESAI faapy: {n} file baru, total {total // 1024} KB di {DEST}", flush=True)


if __name__ == "__main__":
    main()
