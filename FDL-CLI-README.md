# FDL-CLI

Universal command-line interface for **Faa YTDownloader**. The same Python CLI implementation is designed to run on:

* Windows
* Linux
* Android through Termux

The CLI uses `yt-dlp` to download media and `ffmpeg` for audio extraction/conversion.

## Features

* Download audio as MP3 (`192K`).
* Download video using yt-dlp's `best` format selection.
* Run diagnostics with `--test`.
* Detect `yt-dlp` and `ffmpeg` from the system `PATH`.
* Fall back to `python -m yt_dlp` when the `yt-dlp` executable is unavailable but the Python module is installed.
* Select a platform-appropriate default output directory.
* Override the output directory with `FDL_OUTPUT_DIR`.

## Project files

```text
FDL-CLI/
├── fdl.py       # Cross-platform CLI implementation
├── fdl          # Extensionless executable for Linux / Termux
└── install.sh   # One-liner installer script for Linux / Termux
```

## ⚡ Quick Installation (Linux & Termux)

You can automatically install `fdl` using a single `curl` command.

### 1. Install Dependencies

**Termux:**

```bash
pkg update && pkg install python ffmpeg curl -y
python -m pip install -U yt-dlp
termux-setup-storage
```

**Linux (Debian/Ubuntu):**

```bash
sudo apt update && sudo apt install python3 ffmpeg curl -y
python3 -m pip install -U yt-dlp
```

### 2. Run One-Liner Installer

Execute this command in your terminal:

```bash
bash -c "$(curl -sSL https://raw.githubusercontent.com/FaaRamadhann/Faa-YDL/refs/heads/main/FDL-CLI/install.sh)"
```

or

```bash
curl -fsSL https://raw.githubusercontent.com/FaaRamadhann/Faa-YDL/refs/heads/main/FDL-CLI/install.sh | bash
```
---

## Manual Installation

### Termux (Manual)

```bash
mkdir -p "$PREFIX/bin"
curl -sSL "https://raw.githubusercontent.com/FaaRamadhann/Faa-YDL/refs/heads/main/FDL-CLI/fdl" -o "$PREFIX/bin/fdl"
chmod 755 "$PREFIX/bin/fdl"
hash -r
```

### Linux (Manual)

1. Download and grant execution permission:

```bash
mkdir -p "$HOME/.local/bin"
curl -sSL "https://raw.githubusercontent.com/FaaRamadhann/Faa-YDL/refs/heads/main/FDL-CLI/fdl" -o "$HOME/.local/bin/fdl"
chmod +x "$HOME/.local/bin/fdl"
```

2. Add `$HOME/.local/bin` to your `PATH`:

Append the following block to your shell configuration file (`~/.bashrc` or `~/.zshrc`):

```bash
if [ -d "$HOME/.local/bin" ]; then
    export PATH="$HOME/.local/bin:$PATH"
fi
```

Command add

```bash
cat << 'EOF' >> ~/.bashrc
if [ -d "$HOME/.local/bin" ]; then
    export PATH="$HOME/.local/bin:$PATH"
fi
EOF
```

> **📌 Note:** If neither `~/.bashrc` nor `~/.zshrc` exists in your home directory, create one first (e.g., `touch ~/.bashrc`), paste the code block above into it, and then apply changes:
> ```bash
> source ~/.bashrc
> ```

---

### Windows

Keep `fdl.py` and `fdl.bat` in the same directory.

Install Python 3, yt-dlp, and ffmpeg, and make sure their executable paths are available through `PATH`.

Run from Command Prompt or PowerShell:

```powershell
.\fdl.bat help
.\fdl.bat --test
```

To run `fdl` from any directory, add the directory containing both files to the Windows `PATH`.

---

## Usage

```bash
fdl help
fdl --test
fdl --test "URL"
fdl mp3 "URL"
fdl mp4 "URL"
```

Examples:

```bash
fdl mp3 "https://www.youtube.com/watch?v=VIDEO_ID"
fdl mp4 "https://youtu.be/VIDEO_ID"
```

### Commands

| Command | Description |
| :--- | :--- |
| `fdl` | Display usage information |
| `fdl help` | Display help |
| `fdl --test` | Run diagnostics using built-in test URL |
| `fdl --test URL` | Run diagnostics using supplied URL |
| `fdl mp3 URL` | Extract audio and save as MP3 at 192K |
| `fdl mp4 URL` | Download video using yt-dlp's `best` format selection |

---

## Output directory

The default output directory is selected automatically:

| Platform | Default directory |
| :--- | :--- |
| **Termux** | `/sdcard/Download/FaaDL` |
| **Linux** | `~/Downloads/FaaDL` |
| **Windows** | `%USERPROFILE%\Downloads\FaaDL` |

The directory is created automatically when a download starts.

### Custom output directory

Set `FDL_OUTPUT_DIR` to override the default.

Linux / Termux:

```bash
export FDL_OUTPUT_DIR="$HOME/Videos/FaaDL"
fdl mp4 "URL"
```

Windows PowerShell:

```powershell
$env:FDL_OUTPUT_DIR = "$HOME\Downloads\My-FaaDL"
.\fdl.bat mp4 "URL"
```

---

## Diagnostics

Run:

```bash
fdl --test
```

The diagnostic command reports:

* Operating system and CPU architecture.
* Python version.
* yt-dlp executable/module availability and version.
* ffmpeg availability and version.
* Selected output directory.
* YouTube Innertube player response for the supplied URL, including playability status and available format counts.

---

## Troubleshooting

### `fdl: command not found`

Make sure `$HOME/.local/bin` is in your `PATH`, then run `source ~/.bashrc` (or `source ~/.zshrc`), or restart your terminal.

### `Permission denied` on Linux / Termux

Make the extensionless executable runnable:

```bash
chmod 755 "$PREFIX/bin/fdl"
# or for Linux user binary:
chmod +x ~/.local/bin/fdl
```

### `yt-dlp: NOT FOUND`

Install or update yt-dlp:

```bash
python -m pip install -U yt-dlp
```

### `ffmpeg: NOT FOUND`

Install ffmpeg and ensure it is available in `PATH`.

Termux:

```bash
pkg install ffmpeg
```

Debian / Ubuntu:

```bash
sudo apt install ffmpeg
```

### Termux cannot write to `/sdcard/Download/FaaDL`

Run `termux-setup-storage` and grant permission.

---

## License

Refer to the license declared by the parent **Faa-YDL** repository. If no license is present, all rights remain with the respective copyright holder.
