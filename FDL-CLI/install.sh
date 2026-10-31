#!/usr/bin/env bash
set -e

URL="https://raw.githubusercontent.com/FaaRamadhann/Faa-YDL/refs/heads/main/FDL-CLI/fdl"
OUT="fdl"
OUT_DIR="$HOME/.local/bin"

mkdir -p "$OUT_DIR"

if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$URL" -o "$OUT_DIR/$OUT"
elif command -v wget >/dev/null 2>&1; then
    wget -qO "$OUT_DIR/$OUT" "$URL"
else
    echo "Error: curl or wget is required."
    exit 1
fi

chmod 755 "$OUT_DIR/$OUT"
echo "Downloaded $OUT to $OUT_DIR"
ls -la "$OUT_DIR/$OUT"

echo ""
echo "Instalasi selesai! Ketik 'source ~/.bashrc' atau buka ulang terminal."
