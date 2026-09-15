"""
build.py - Faa DL (com.faa.faadl) TANPA Gradle / Android Studio.
Diadaptasi dari ex-build.py (Example-Build):
    javac -> d8 -> aapt -> keystore -> zipalign -> apksigner

Membangun 3 varian:
    1. faa-dl_v{ver}_arm64.apk      (64 bit / arm64-v8a)
    2. faa-dl_v{ver}_arm32.apk      (32 bit / armeabi-v7a)
    3. faa-dl_v{ver}_universal.apk  (universal, dua ffmpeg + pilih saat jalan)

Pakai:
    python build.py [arm64|arm32|universal|all]   # default: all
"""

import fnmatch
import glob
import json
import os
import shutil
import subprocess
import sys
import zipfile

# ---------------- KONFIG (ubah sesuai mesin) ----------------
PACKAGE = "com.faa.faadl"
MIN_SDK = "24"
BUILD_TOOLS = r"C:\AndroidSDK\build-tools\35.0.0"
ANDROID_JAR = r"C:\AndroidSDK\platforms\android-34\android.jar"
JAVA_HOME = r"C:\Program Files\Java\jdk-21.0.10"
KEYSTORE = "debug.keystore"   # relatif ke root project
KEY_ALIAS = "faadl"
STOREPASS = "android"
KEYPASS = "android"
# -------------- akhir KONFIG ------------------------------------

# varian -> { file assets/bin : file sumber di bins/ }
# NOTE: ELF executable TIDAK ditaruh di assets (duplikat boros) — mereka
# hanya ada di lib/<abi>/ (diekstrak PackageManager, boleh di-exec).
# assets/bin hanya: script yt-dlp, node placeholder, qjs cadangan, stdlib zip.
VARIANTS = {
    "arm64": {
        "yt-dlp": "common/yt-dlp",
        "node": "arm64/node",
        "py-qjs": "arm64/py/libqjs.so",
    },
    "arm32": {
        "yt-dlp": "common/yt-dlp",
        "node": "arm32/node",
        "py-stdlib": "arm32/py/stdlib.zip",
        "py-qjs": "arm32/py/libqjs.so",
    },
    "universal": {
        "yt-dlp": "common/yt-dlp",
        "node": "common/node",
        "node-arm64": "arm64/node",
        "node-arm32": "arm32/node",
        "py-armeabi-stdlib": "arm32/py/stdlib.zip",
        "py-armeabi-qjs": "arm32/py/libqjs.so",
        "py-arm64-qjs": "arm64/py/libqjs.so",
    },
}

# varian -> { dir di assets/ : dir sumber di bins/ } (disalin rekursif)
VARIANT_TREES = {
    "arm64": {"faapy": "arm64/faapy"},
    "arm32": {},
    "universal": {"faapy": "arm64/faapy"},
}

# Native libs: diekstrak PackageManager ke nativeLibraryDir (bisa di-exec
# walau SELinux melarang exec dari filesDir). Format entri:
#   ("bins", rel, dst) | ("zip", ziprel, inner, dst|None) |
#   ("zipcopy", ziprel, inner, dst) | ("glob", dirrel, pattern)
_ARM64_NL = [
    ("bins", "arm64/ffmpeg", "libffmpeg.so"),
    ("bins", "arm64/faapy/bin/python3.14", "libfpy.so"),
    ("bins", "arm64/fflib/libc++_shared.so", "libc++_shared.so"),
    ("bins", "arm64/py/libqjs.so", "libqjs.so"),
    ("glob", "arm64/faapy/lib64", "*.so"),
    ("glob", "arm64/faapy/lib64/lib/python3.14/lib-dynload", "*.so"),
]
_ARM32_NL = [
    ("bins", "arm32/ffmpeg", "libffmpeg.so"),
    ("bins", "arm32/fflib/libc++_shared.so", "libc++_shared.so"),
    ("bins", "arm32/py/libpython.so", "libfpy32.so"),
    ("bins", "arm32/py/libqjs.so", "libqjs.so"),
    ("zip", "arm32/py/stdlib.zip", "usr/lib/libandroid-support.so", None),
    ("zip", "arm32/py/stdlib.zip", "usr/lib/libpython3.12.so.1.0", None),
    ("zip", "arm32/py/stdlib.zip", "usr/lib/libssl.so.3", None),
    ("zip", "arm32/py/stdlib.zip", "usr/lib/libcrypto.so.3", None),
    ("zipcopy", "arm32/py/stdlib.zip", "usr/lib/libsqlite3.so.3.50.4", "libsqlite3.so.0"),
    ("zipcopy", "arm32/py/stdlib.zip", "usr/lib/libz.so.1.3.1", "libz.so.1"),
    ("zipcopy", "arm32/py/stdlib.zip", "usr/lib/libexpat.so.1.11.1", "libexpat.so.1"),
    ("zipcopy", "arm32/py/stdlib.zip", "usr/lib/liblzma.so.5.8.1", "liblzma.so.5"),
    ("zip", "arm32/py/stdlib.zip", "usr/lib/libffi.so", None),
    ("zip", "arm32/py/stdlib.zip", "usr/lib/libbz2.so.1.0", None),
    ("zipglob", "arm32/py/stdlib.zip", "usr/lib/python3.12/lib-dynload/*.so"),
]
NATIVELIBS = {
    "arm64": {"arm64-v8a": _ARM64_NL},
    "arm32": {"armeabi-v7a": _ARM32_NL},
    "universal": {"arm64-v8a": _ARM64_NL, "armeabi-v7a": _ARM32_NL},
}


def run(cmd, cwd):
    """Jalankan command, raise SystemExit bila gagal (dengan output)."""
    print("  $", " ".join(cmd), flush=True)
    # d8/apksigner .bat butuh shell di Windows bila dipanggil langsung
    r = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True,
                       shell=(os.name == "nt" and cmd[0].endswith(".bat")))
    if r.returncode != 0:
        if r.stdout:
            print(r.stdout[-2000:])
        if r.stderr:
            print(r.stderr[-2000:])
        sys.exit(f"GAGAL (rc={r.returncode}): {cmd[0]}")
    return r


def get_version(root):
    try:
        with open(os.path.join(root, "package.json")) as f:
            return json.load(f)["version"]
    except Exception:
        return "1.0.0"


CHUNK_SIZE = 4 * 1024 * 1024   # binary >8MB dipecah agar AssetManager aman
SPLIT_LIMIT = 8 * 1024 * 1024


def split_big(path):
    """Pecah file besar jadi <path>.partNN, hapus aslinya. Return jml part/0."""
    size = os.path.getsize(path)
    if size <= SPLIT_LIMIT:
        return 0
    idx = 0
    with open(path, "rb") as f:
        while True:
            data = f.read(CHUNK_SIZE)
            if not data:
                break
            with open(f"{path}.part{idx:02d}", "wb") as o:
                o.write(data)
            idx += 1
    os.remove(path)
    return idx


def stage_assets(root, variant):
    """Siapkan assets/bin sesuai varian dari bins/. Return daftar file."""
    src_bins = os.path.join(root, "bins")
    dst_bin = os.path.join(root, "assets", "bin")
    os.makedirs(dst_bin, exist_ok=True)
    for f in os.listdir(dst_bin):
        fp = os.path.join(dst_bin, f)
        if os.path.isfile(fp):
            os.remove(fp)
    staged = []
    for dst_name, src_rel in VARIANTS[variant].items():
        src = os.path.join(src_bins, *src_rel.split("/"))
        dst = os.path.join(dst_bin, dst_name)
        if os.path.isfile(src):
            shutil.copyfile(src, dst)
            npart = split_big(dst)
            if npart:
                print(f"  assets/bin/{dst_name} -> {npart}x part "
                      f"({os.path.getsize(dst + '.part00') // 1024} KB/part)")
                staged.append((dst_name + f" ({npart} part)", sum(
                    os.path.getsize(f"{dst}.part{i:02d}") for i in range(npart))))
            else:
                staged.append((dst_name, os.path.getsize(dst)))
        else:
            print(f"  ! sumber tidak ada, skip: {src_rel}")
    # bersihkan tree variant lain yang menempel (biar tak ikut ke APK)
    all_trees = set()
    for _v, _t in VARIANT_TREES.items():
        all_trees.update(_t.keys())
    for _t in all_trees - set(VARIANT_TREES.get(variant, {}).keys()):
        _d = os.path.join(root, "assets", _t)
        if os.path.isdir(_d):
            shutil.rmtree(_d)
            print(f"  bersih-bersih assets/{_t}/ (bukan varian {variant})")
    for dst_dir, src_rel in VARIANT_TREES.get(variant, {}).items():
        src = os.path.join(src_bins, *src_rel.split("/"))
        dst = os.path.join(root, "assets", dst_dir)
        if os.path.isdir(src):
            if os.path.isdir(dst):
                shutil.rmtree(dst)
            shutil.copytree(src, dst)
            total = sum(os.path.getsize(os.path.join(dp, f))
                        for dp, _, fns in os.walk(dst) for f in fns)
            nfiles = sum(len(fns) for _, _, fns in os.walk(dst))
            print(f"  assets/{dst_dir}/  {nfiles} file, {total // 1024} KB")
            staged.append((dst_dir + "/", total))
        else:
            print(f"  ! sumber tree tidak ada, skip: {src_rel}")
    # www kosong tidak apa-apa; pastikan folder ada
    os.makedirs(os.path.join(root, "assets", "www"), exist_ok=True)
    for name, size in staged:
        where = "assets/bin/" if not name.endswith("/") else "assets/"
        print(f"  {where}{name}  {size // 1024} KB")
    return staged


def stage_nativelibs(root, build, variant):
    """Susun build-<v>/nl/lib/<abi>/*.so dari bins. Return dir nl / None."""
    spec = NATIVELIBS.get(variant, {})
    if not spec:
        return None
    src_bins = os.path.join(root, "bins")
    nldir = os.path.join(build, "nl")
    if os.path.isdir(nldir):
        shutil.rmtree(nldir)
    count = 0
    for abi, items in spec.items():
        for it in items:
            kind = it[0]
            if kind == "bins":
                _, rel, dst = it
                src = os.path.join(src_bins, *rel.split("/"))
                if not os.path.isfile(src):
                    print(f"  ! nl sumber tidak ada: {rel}")
                    continue
                out = os.path.join(nldir, "lib", abi, dst)
                os.makedirs(os.path.dirname(out), exist_ok=True)
                shutil.copyfile(src, out)
                count += 1
            elif kind == "glob":
                _, drel, pat = it
                for fp in glob.glob(os.path.join(src_bins, *drel.split("/"), pat)):
                    if not os.path.isfile(fp):
                        continue
                    out = os.path.join(nldir, "lib", abi, os.path.basename(fp))
                    os.makedirs(os.path.dirname(out), exist_ok=True)
                    shutil.copyfile(fp, out)
                    count += 1
            elif kind in ("zip", "zipcopy", "zipglob"):
                _, zrel, inner = it[0], it[1], it[2]
                zpath = os.path.join(src_bins, *zrel.split("/"))
                if not os.path.isfile(zpath):
                    print(f"  ! nl zip tidak ada: {zrel}")
                    continue
                zf = zipfile.ZipFile(zpath)
                jobs = []
                if kind == "zipglob":
                    jobs = [(n, os.path.basename(n)) for n in zf.namelist()
                            if fnmatch.fnmatch(n, inner)]
                elif kind == "zipcopy":
                    jobs = [(inner, it[3])]
                else:
                    dst = it[3] or os.path.basename(inner)
                    jobs = [(inner, dst)]
                for inm, dstn in jobs:
                    try:
                        data = zf.read(inm)
                    except KeyError:
                        print(f"  ! nl entri tidak ada: {inm}")
                        continue
                    out = os.path.join(nldir, "lib", abi, dstn)
                    os.makedirs(os.path.dirname(out), exist_ok=True)
                    with open(out, "wb") as f:
                        f.write(data)
                    count += 1
                zf.close()
    print(f"  native libs: {count} file")
    return nldir if count else None


def recompress_libs(apk_path):
    """Paksa entri lib/** jadi DEFLATED (aapt add menyimpan STORED)."""
    tmp = apk_path + ".repack"
    zin = zipfile.ZipFile(apk_path, "r")
    zout = zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED, compresslevel=6)
    n = 0
    for info in zin.infolist():
        data = zin.read(info.filename)
        ni = zipfile.ZipInfo(info.filename, date_time=info.date_time)
        ni.external_attr = info.external_attr
        ni.create_system = info.create_system
        if info.filename.startswith("lib/"):
            ni.compress_type = zipfile.ZIP_DEFLATED
            n += 1
        else:
            ni.compress_type = info.compress_type
        zout.writestr(ni, data)
    zin.close()
    zout.close()
    os.replace(tmp, apk_path)
    print(f"  lib recompressed: {n} file")
    return n


def build_one(root, tools, variant, ver):
    apk_name = f"faa-dl_v{ver}_{variant}.apk"
    build = os.path.join(root, f"build-{variant}")
    os.makedirs(os.path.join(build, "obj"), exist_ok=True)
    os.makedirs(os.path.join(build, "dex"), exist_ok=True)
    print(f"\n================= {apk_name} [{variant}] =================")

    print("[0/6] siapkan assets/bin...")
    stage_assets(root, variant)

    print("[1/6] kumpulkan source...")
    sources = []
    for dp, _, fns in os.walk(os.path.join(root, "src")):
        sources += [os.path.join(dp, f) for f in fns if f.endswith(".java")]
    if not sources:
        sys.exit("Tidak ada file .java di src/")
    print(f"  {len(sources)} file java")

    print("[2/6] javac...")
    with open(os.path.join(build, "sources.txt"), "w") as fh:
        fh.write("\n".join(sources))
    run([tools["javac"], "--release", "8", "-classpath", ANDROID_JAR,
         "-d", os.path.join(build, "obj"),
         "@" + os.path.join(build, "sources.txt")], root)

    print("[3/6] jar + d8 (java -> dex)...")
    jarfile = os.path.join(build, "classes.jar")
    run([tools["jar"], "--create", "--file", jarfile,
         "-C", os.path.join(build, "obj"), "."], root)
    dexdir = os.path.join(build, "dex")
    # bersihkan dex lama biar tidak nyampur
    for f in os.listdir(dexdir):
        fp = os.path.join(dexdir, f)
        if os.path.isfile(fp):
            os.remove(fp)
    run([tools["d8"], "--min-api", MIN_SDK, "--lib", ANDROID_JAR,
         "--output", dexdir, jarfile], root)

    print("[4/6] aapt package...")
    cmd = [tools["aapt"], "package", "-f", "-M", "AndroidManifest.xml"]
    if os.path.isdir(os.path.join(root, "res")):
        cmd += ["-S", "res"]
    if os.path.isdir(os.path.join(root, "assets")):
        cmd += ["-A", "assets"]
    cmd += ["-I", ANDROID_JAR, "-F", os.path.join(build, "unsigned.apk")]
    run(cmd, root)
    run([tools["aapt"], "add", os.path.join(build, "unsigned.apk"),
         "classes.dex"], os.path.join(build, "dex"))

    print("[4b/6] native libs (lib/<abi>, bisa di-exec)...")
    nldir = stage_nativelibs(root, build, variant)
    if nldir:
        rel = []
        for dp, _, fns in os.walk(nldir):
            for f in fns:
                rel.append(os.path.relpath(os.path.join(dp, f), nldir).replace(os.sep, "/"))
        if rel:
            run([tools["aapt"], "add", os.path.join(build, "unsigned.apk")] + rel, nldir)
        recompress_libs(os.path.join(build, "unsigned.apk"))

    print("[5/6] keystore (sekali saja, lalu dipakai terus)...")
    print("  PENTING: backup debug.keystore - update APK wajib key yang sama!")
    ks = os.path.join(root, KEYSTORE)
    if not os.path.exists(ks):
        run([tools["keytool"], "-genkeypair", "-keystore", ks,
             "-alias", KEY_ALIAS, "-keyalg", "RSA", "-keysize", "2048",
             "-validity", "10950", "-storepass", STOREPASS,
             "-keypass", KEYPASS, "-dname", "CN=Faa DL"], root)

    print("[6/6] zipalign + apksigner...")
    run([tools["zipalign"], "-f", "4", os.path.join(build, "unsigned.apk"),
         os.path.join(build, "aligned.apk")], root)
    out_apk = os.path.join(root, "build", apk_name)
    os.makedirs(os.path.join(root, "build"), exist_ok=True)
    run([tools["apksigner"], "sign", "--ks", ks,
         "--ks-key-alias", KEY_ALIAS, "--ks-pass", f"pass:{STOREPASS}",
         "--key-pass", f"pass:{KEYPASS}", "--out", out_apk,
         os.path.join(build, "aligned.apk")], root)
    run([tools["apksigner"], "verify", out_apk], root)
    print(f"SELESAI: {out_apk} ({os.path.getsize(out_apk) // 1024} KB)")
    return out_apk


def main():
    arg = sys.argv[1] if len(sys.argv) > 1 else "all"
    root = os.path.abspath(os.path.dirname(__file__))
    for must in ("AndroidManifest.xml", "src"):
        if not os.path.exists(os.path.join(root, must)):
            sys.exit(f"Bukan project Android: {must} tidak ada di {root}")
    if arg in ("64",):
        arg = "arm64"
    if arg in ("32",):
        arg = "arm32"
    if arg == "all":
        targets = ["arm64", "arm32", "universal"]
    elif arg in VARIANTS:
        targets = [arg]
    else:
        sys.exit("Pakai: python build.py [arm64|arm32|universal|all]")

    ver = get_version(root)
    print(f"Faa DL v{ver}  package={PACKAGE}")
    tools = {
        "javac": os.path.join(JAVA_HOME, "bin", "javac.exe"),
        "jar": os.path.join(JAVA_HOME, "bin", "jar.exe"),
        "keytool": os.path.join(JAVA_HOME, "bin", "keytool.exe"),
        "d8": os.path.join(BUILD_TOOLS, "d8.bat"),
        "aapt": os.path.join(BUILD_TOOLS, "aapt.exe"),
        "zipalign": os.path.join(BUILD_TOOLS, "zipalign.exe"),
        "apksigner": os.path.join(BUILD_TOOLS, "apksigner.bat"),
    }
    for t, p in tools.items():
        if not os.path.exists(p):
            sys.exit(f"Tool tidak ada [{t}]: {p}")
    if not os.path.exists(ANDROID_JAR):
        sys.exit(f"android.jar tidak ada: {ANDROID_JAR}")

    outs = [build_one(root, tools, v, ver) for v in targets]
    print("\nSELESAI SEMUA (di folder build/):")
    for o in outs:
        print(f"  build\\{os.path.basename(o)}")


if __name__ == "__main__":
    main()
