# FDL-CLI

Universal command-line interface for **Faa YTDownloader**. The same
Python CLI implementation is designed to run on:

-   Windows
-   Linux
-   Android through Termux

The CLI uses `yt-dlp` to download media and `ffmpeg` for audio
extraction/conversion.

## Features

-   Download audio as MP3 (`192K`).
-   Download video using yt-dlp's `best` format selection.
-   Run diagnostics with `--test`.
-   Detect `yt-dlp` and `ffmpeg` from the system `PATH`.
-   Fall back to `python -m yt_dlp` when the `yt-dlp` executable is
    unavailable but the Python module is installed.
-   Select a platform-appropriate default output directory.
-   Override the output directory with `FDL_OUTPUT_DIR`.

## Project files

``` text
FDL-CLI/
├── fdl.py       # Cross-platform CLI implementation
└── fdl          # Extensionless executable for Linux / Termux
```

`fdl` and `fdl.py` contain the same Python CLI. The extensionless `fdl`
file is intended for systems where the command is installed directly
into a directory in `PATH`.

## Requirements

  -----------------------------------------------------------------------
  Platform                            Requirements
  ----------------------------------- -----------------------------------
  Windows                             Python 3, yt-dlp, ffmpeg

  Linux                               Python 3, yt-dlp, ffmpeg

  Termux                              Python, yt-dlp, ffmpeg,
                                      shared-storage permission
                                      (optional, for the default output
                                      path)
  -----------------------------------------------------------------------

`ffmpeg` is required for MP3 extraction. It is recommended to install it
for all download formats.

## Installation

### Termux

Install dependencies:

``` bash
pkg update
pkg install python ffmpeg
python -m pip install -U yt-dlp
```

Allow Termux to access shared storage if you want downloads in
`/sdcard/Download/FaaDL`:

``` bash
termux-setup-storage
```

Install the extensionless command from this directory:

``` bash
mkdir -p "$PREFIX/bin"
install -m 755 fdl "$PREFIX/bin/fdl"
```

If the `install` command or its options are unavailable, use:

``` bash
cp fdl "$PREFIX/bin/fdl"
chmod 755 "$PREFIX/bin/fdl"
```

Refresh Bash's command lookup cache and verify:

``` bash
hash -r
fdl help
fdl --test
```

### Debian / Ubuntu

Install dependencies:

``` bash
sudo apt update
sudo apt install -y python3 ffmpeg
```

Install yt-dlp using a supported method for your distribution. For
example, use your distribution package if available, or install it in an
isolated Python environment with `pipx`:

``` bash
sudo apt install -y pipx
pipx ensurepath
pipx install yt-dlp
```

Open a new shell if needed, then install the CLI:

``` bash
sudo install -m 755 fdl /usr/local/bin/fdl
```

Verify:

``` bash
fdl help
fdl --test
```

### Windows

Keep `fdl.py` and `fdl.bat` in the same directory.

Install Python 3, yt-dlp, and ffmpeg, and make sure their executable
paths are available through `PATH`.

Run from Command Prompt or PowerShell:

``` powershell
.\fdl.bat help
.\fdl.bat --test
```

To run `fdl` from any directory, add the directory containing both files
to the Windows `PATH`.

The batch launcher tries `py -3` first and falls back to `python`.

## Usage

``` bash
fdl help
fdl --test
fdl --test "URL"
fdl mp3 "URL"
fdl mp4 "URL"
```

Examples:

``` bash
fdl mp3 "https://www.youtube.com/watch?v=VIDEO_ID"
fdl mp4 "https://youtu.be/VIDEO_ID"
```

### Commands

  -----------------------------------------------------------------------
  Command                             Description
  ----------------------------------- -----------------------------------
  `fdl`                               Display usage information

  `fdl help`                          Display help

  `fdl --test`                        Run diagnostics using the built-in
                                      test URL

  `fdl --test URL`                    Run diagnostics using the supplied
                                      URL

  `fdl mp3 URL`                       Extract audio and save it as MP3 at
                                      192K

  `fdl mp4 URL`                       Download video using yt-dlp's
                                      `best` format selection
  -----------------------------------------------------------------------

The `mp4` command uses yt-dlp's `-f best` selector. The resulting
container and quality depend on the formats available for the selected
video.

## Output directory

The default output directory is selected automatically:

  Platform   Default directory
  ---------- ---------------------------------
  Termux     `/sdcard/Download/FaaDL`
  Linux      `~/Downloads/FaaDL`
  Windows    `%USERPROFILE%\Downloads\FaaDL`

The directory is created automatically when a download starts.

### Custom output directory

Set `FDL_OUTPUT_DIR` to override the default.

Linux / Termux:

``` bash
export FDL_OUTPUT_DIR="$HOME/Videos/FaaDL"
fdl mp4 "URL"
```

Windows PowerShell:

``` powershell
$env:FDL_OUTPUT_DIR = "$HOME\Downloads\My-FaaDL"
.\fdl.bat mp4 "URL"
```

## Diagnostics

Run:

``` bash
fdl --test
```

The diagnostic command reports:

-   Operating system and CPU architecture.
-   Python version.
-   yt-dlp executable/module availability and version.
-   ffmpeg availability and version.
-   Selected output directory.
-   YouTube Innertube player response for the supplied URL, including
    playability status and available format counts.

A successful diagnostic does not guarantee that every video can be
downloaded. YouTube availability, authentication, age restrictions,
network conditions, and extractor changes can affect results.

## Troubleshooting

### `Permission denied` on Linux / Termux

Make the extensionless executable runnable:

``` bash
chmod 755 "$PREFIX/bin/fdl"
```

For Linux installed under `/usr/local/bin`:

``` bash
sudo chmod 755 /usr/local/bin/fdl
```

### `yt-dlp: NOT FOUND`

Install or update yt-dlp and ensure its executable directory is in
`PATH`:

``` bash
python -m pip install -U yt-dlp
```

If the executable is not in `PATH`, the CLI can use `python -m yt_dlp`
when the module is installed for the same Python interpreter used by
`fdl`.

### `ffmpeg: NOT FOUND`

Install ffmpeg and ensure it is available in `PATH`.

Termux:

``` bash
pkg install ffmpeg
```

Debian / Ubuntu:

``` bash
sudo apt install ffmpeg
```

### Termux cannot write to `/sdcard/Download/FaaDL`

Run:

``` bash
termux-setup-storage
```

Grant the requested storage permission. Alternatively, choose a writable
directory:

``` bash
export FDL_OUTPUT_DIR="$HOME/storage/downloads/FaaDL"
```

### `fdl: command not found`

Check that the installation directory is in `PATH`.

Termux:

``` bash
echo "$PATH"
ls -l "$PREFIX/bin/fdl"
hash -r
```

Linux:

``` bash
echo "$PATH"
command -v fdl
```

## Notes

-   `fdl` is a command-line wrapper around yt-dlp; it does not host or
    proxy media.
-   An internet connection is required for URL diagnostics and
    downloads.
-   MP3 extraction requires ffmpeg.
-   Keep yt-dlp updated because website extractors can change.
-   Use the tool only for content you are authorized to download and in
    accordance with the applicable platform terms and laws.

## License

Refer to the license declared by the parent **Faa-YDL** repository. If
no license is present, all rights remain with the respective copyright
holder.
