@echo off
cd /d "%~dp0..\apps\front"
"D:\develop\node\npm.cmd" run dev -- --hostname 127.0.0.1 --port 3000 --webpack
