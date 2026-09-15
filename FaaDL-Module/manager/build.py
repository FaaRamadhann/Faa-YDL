"""
build.py - FYDL Manager (com.faa.faadlmod) TANPA Gradle / Android Studio.
Diadaptasi dari ex-build.py (Example-Build):
    javac -> jar -> d8 -> aapt -> keystore -> zipalign -> apksigner

APK manager SENGAJA ramping: tanpa ffmpeg/python di dalam APK,
karena modul Magisk menyediakan system-wide di /system/bin/faadl-*.
Pakai:  python build.py
Hasil:  build/manager.apk (siap dipasang customize.sh ke root modul)
"""

import json
import os
import shutil
import subprocess
import sys

# ---------------- KONFIG (ubah sesuai mesin) ----------------
PACKAGE = "com.faa.faadlmod"
APP_NAME = "manager"
MIN_SDK = "24"
BUILD_TOOLS = r"C:\AndroidSDK\build-tools\35.0.0"
ANDROID_JAR = r"C:\AndroidSDK\platforms\android-34\android.jar"
JAVA_HOME = r"C:\Program Files\Java\jdk-21.0.10"
KEYSTORE = "debug.keystore"   # relatif ke root manager
KEY_ALIAS = "fydlmod"
STOREPASS = "android"
KEYPASS = "android"
# -------------- akhir KONFIG ------------------------------------


def run(cmd, cwd):
    """Jalankan command, raise SystemExit bila gagal (dengan output)."""
    print("  $", " ".join(cmd), flush=True)
    r = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True,
                       shell=(os.name == "nt" and cmd[0].endswith(".bat")))
    if r.returncode != 0:
        if r.stdout:
            print(r.stdout[-2000:])
        if r.stderr:
            print(r.stderr[-2000:])
        sys.exit(f"GAGAL (rc={r.returncode}): {cmd[0]}")
    return r


def main():
    root = os.path.abspath(os.path.dirname(__file__))
    for must in ("AndroidManifest.xml", "src"):
        if not os.path.exists(os.path.join(root, must)):
            sys.exit(f"Bukan project Android: {must} tidak ada di {root}")

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

    build = os.path.join(root, "build")
    os.makedirs(os.path.join(build, "obj"), exist_ok=True)
    os.makedirs(os.path.join(build, "dex"), exist_ok=True)

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
         "classes.dex"], dexdir)

    print("[5/6] keystore (sekali saja, lalu dipakai terus)...")
    print("  PENTING: backup debug.keystore - update APK wajib key yang sama!")
    ks = os.path.join(root, KEYSTORE)
    if not os.path.exists(ks):
        run([tools["keytool"], "-genkeypair", "-keystore", ks,
             "-alias", KEY_ALIAS, "-keyalg", "RSA", "-keysize", "2048",
             "-validity", "10950", "-storepass", STOREPASS,
             "-keypass", KEYPASS, "-dname", "CN=FYDL"], root)

    print("[6/6] zipalign + apksigner...")
    run([tools["zipalign"], "-f", "4", os.path.join(build, "unsigned.apk"),
         os.path.join(build, "aligned.apk")], root)
    out_apk = os.path.join(build, f"{APP_NAME}.apk")
    run([tools["apksigner"], "sign", "--ks", ks,
         "--ks-key-alias", KEY_ALIAS, "--ks-pass", f"pass:{STOREPASS}",
         "--key-pass", f"pass:{KEYPASS}", "--out", out_apk,
         os.path.join(build, "aligned.apk")], root)
    run([tools["apksigner"], "verify", out_apk], root)
    print(f"\nSELESAI: {out_apk} ({os.path.getsize(out_apk) // 1024} KB)")


if __name__ == "__main__":
    main()
