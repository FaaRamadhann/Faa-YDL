"""Siapkan binary built-in per ABI untuk Faa DL.
- ffmpeg arm64/arm32 dari Tyrrrz/FFmpegBin (Android)
- yt-dlp (arch-independent, pure python zipapp)
- node: opsional, placeholder bila download gagal
Hasil di bins/{arm64,arm32,common}/ + dirilis ke assets oleh build.bat.
"""
import os, sys, urllib.request, zipfile, shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BINS = os.path.join(ROOT, "bins")
URLS = {
    "ffmpeg-arm64": "https://github.com/Tyrrrz/FFmpegBin/releases/download/8.1.2/ffmpeg-android-arm64.zip",
    "ffmpeg-arm32": "https://github.com/Tyrrrz/FFmpegBin/releases/download/8.1.2/ffmpeg-android-arm.zip",
    "yt-dlp": "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp",
}

def dl(url, dst):
    print(f"  DL {url}\n   -> {dst}", flush=True)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    req = urllib.request.Request(url, headers={"User-Agent": "FaaDL/1.0"})
    with urllib.request.urlopen(req, timeout=120) as r, open(dst, "wb") as f:
        shutil.copyfileobj(r, f)
    print(f"     OK {os.path.getsize(dst)//1024} KB", flush=True)
    return dst

def extract_ffmpeg(zip_path, out_path):
    with zipfile.ZipFile(zip_path) as z:
        names = z.namelist()
        print(f"     isi zip: {names[:5]}", flush=True)
        # cari file bernama ffmpeg (tanpa ekstensi)
        cand = None
        for n in names:
            b = os.path.basename(n).lower()
            if b == "ffmpeg" or b == "ffmpeg.exe":
                cand = n; break
        if cand is None:
            # fallback: file terbesar tanpa '/' dalam?
            cand = max(names, key=lambda n: z.getinfo(n).file_size)
        print(f"     pakai entry: {cand}", flush=True)
        with z.open(cand) as src, open(out_path, "wb") as dst:
            shutil.copyfileobj(src, dst)
    print(f"     ffmpeg -> {out_path} ({os.path.getsize(out_path)//1024} KB)", flush=True)

def main():
    os.makedirs(os.path.join(BINS, "arm64"), exist_ok=True)
    os.makedirs(os.path.join(BINS, "arm32"), exist_ok=True)
    os.makedirs(os.path.join(BINS, "common"), exist_ok=True)
    tmp = os.path.join(BINS, "_tmp")
    os.makedirs(tmp, exist_ok=True)

    # 1) ffmpeg arm64
    try:
        z64 = os.path.join(tmp, "ffmpeg-arm64.zip")
        if not os.path.exists(os.path.join(BINS, "arm64", "ffmpeg")) or os.path.getsize(os.path.join(BINS, "arm64", "ffmpeg")) < 1000000:
            dl(URLS["ffmpeg-arm64"], z64)
            extract_ffmpeg(z64, os.path.join(BINS, "arm64", "ffmpeg"))
        else:
            print("  ffmpeg arm64 sudah ada, skip", flush=True)
    except Exception as e:
        print(f"  GAGAL ffmpeg arm64: {e}", flush=True)

    # 2) ffmpeg arm32
    try:
        z32 = os.path.join(tmp, "ffmpeg-arm32.zip")
        if not os.path.exists(os.path.join(BINS, "arm32", "ffmpeg")) or os.path.getsize(os.path.join(BINS, "arm32", "ffmpeg")) < 1000000:
            dl(URLS["ffmpeg-arm32"], z32)
            extract_ffmpeg(z32, os.path.join(BINS, "arm32", "ffmpeg"))
        else:
            print("  ffmpeg arm32 sudah ada, skip", flush=True)
    except Exception as e:
        print(f"  GAGAL ffmpeg arm32: {e}", flush=True)

    # 3) yt-dlp (sama untuk semua ABI)
    try:
        y = os.path.join(BINS, "common", "yt-dlp")
        if not os.path.exists(y) or os.path.getsize(y) < 500000:
            dl(URLS["yt-dlp"], y)
        else:
            print("  yt-dlp sudah ada, skip", flush=True)
    except Exception as e:
        print(f"  GAGAL yt-dlp: {e}", flush=True)

    # 4) node opsional: placeholder (biar build tidak gagal bila offline)
    for abi in ("arm64", "arm32"):
        p = os.path.join(BINS, abi, "node")
        if not os.path.exists(p):
            with open(p, "w") as f:
                f.write("#!/system/bin/sh\n# node opsional Faa DL - placeholder\n# ganti dengan node android arm64/arm32 asli bila perlu\n")
            print(f"  node placeholder -> {p}", flush=True)
    yc = os.path.join(BINS, "common", "node")
    if not os.path.exists(yc):
        with open(yc, "w") as f:
            f.write("#!/system/bin/sh\n# node opsional placeholder\n")
    print("SELESAI prepare bins. Isi:", flush=True)
    for dp, dn, fn in os.walk(BINS):
        for x in fn:
            fp = os.path.join(dp, x)
            if "_tmp" in fp: continue
            print(f"  {os.path.relpath(fp, BINS)}  {os.path.getsize(fp)//1024} KB", flush=True)

if __name__ == "__main__":
    main()
