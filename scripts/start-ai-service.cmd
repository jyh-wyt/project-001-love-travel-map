@echo off
cd /d "%~dp0..\apps\ai-service"
"D:\develop\python\python.exe" -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload

