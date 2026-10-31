#!/usr/bin/env bash
URL="https://raw.githubusercontent.com/FaaRamadhann/Faa-YDL/refs/heads/main/FDL-CLI/fdl"
OUT="fdl"
OUT_DIR="$HOME/.local/bin"

mkdir -p "$OUT_DIR"
curl -sSL "$URL" -O "$OUT_DIR/$OUT" || wget -c "$URL" -O "$OUT_DIR/$OUT"

echo "Downloaded $OUT to $OUT_DIR"
chmod 755 "$OUT_DIR/$OUT"

ALIAS_CMD='alias fdl="$HOME/.local/bin/fdl"'
for rc in "$HOME/.bashrc" "$HOME/.zshrc"; do
    if [ -f "$rc" ] && ! grep -q "alias fdl=" "$rc"; then
        echo "$ALIAS_CMD" >> "$rc"
        echo "Added alias to $rc"
    fi
done

echo "Instalasi selesai! Ketik 'source ~/.bashrc' atau buka ulang terminal."
