@echo off
start "" /min "D:\develop\Redis-x64-3.2.100\redis-server.exe" "D:\develop\Redis-x64-3.2.100\redis.windows.conf"
timeout /t 2 >nul
"D:\develop\Redis-x64-3.2.100\redis-cli.exe" ping

