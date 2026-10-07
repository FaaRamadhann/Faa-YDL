@echo off
rem fdl - CLI download Faa YTDownloader (Windows), mirip fdl di modul Magisk.
rem   fdl mp3 URL        -> download MP3 ke Downloads\FaaDL
rem   fdl mp4 URL        -> download MP4 ke Downloads\FaaDL
rem   fdl --test [URL]   -> diagnostik yt-dlp/ffmpeg/innertube
rem   fdl help           -> bantuan ini
python "%~dp0fdl.py" %*
