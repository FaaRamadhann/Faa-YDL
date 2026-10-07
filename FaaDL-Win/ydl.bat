@echo off
rem ydl - CLI download Faa YTDownloader (Windows), mirip fdl di modul Magisk.
rem   ydl mp3 URL        -> download MP3 ke Downloads\FaaDL
rem   ydl mp4 URL        -> download MP4 ke Downloads\FaaDL
rem   ydl --test [URL]   -> diagnostik yt-dlp/ffmpeg/innertube
rem   ydl help           -> bantuan ini
python "%~dp0ydl.py" %*
