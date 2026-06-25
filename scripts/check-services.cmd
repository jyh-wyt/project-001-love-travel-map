@echo off
echo Checking Redis...
"D:\develop\Redis-x64-3.2.100\redis-cli.exe" ping

echo.
echo Checking Java server...
powershell -NoProfile -Command "$tcp = New-Object Net.Sockets.TcpClient; try { $tcp.Connect('127.0.0.1', 8080); Write-Host 'Java server port 8080 is open' } catch { Write-Host $_.Exception.Message; exit 1 } finally { $tcp.Close() }"

echo.
echo Checking AI service...
powershell -NoProfile -Command "try { Invoke-RestMethod -Uri 'http://127.0.0.1:8000/internal/health' } catch { Write-Host $_.Exception.Message; exit 1 }"

echo.
echo Checking front...
powershell -NoProfile -Command "try { $r = Invoke-WebRequest -Uri 'http://127.0.0.1:3000' -UseBasicParsing; Write-Host $r.StatusCode } catch { Write-Host $_.Exception.Message; exit 1 }"
