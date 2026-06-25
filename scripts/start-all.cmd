@echo off
echo Starting Love Travel local development services...
echo.

echo [1/4] Starting Redis...
start "love-travel-redis" /min "%~dp0start-redis.cmd"

echo [2/4] Starting Python FastAPI AI service...
start "love-travel-ai-service" cmd /k "%~dp0start-ai-service.cmd"

echo [3/4] Starting Spring Boot server...
start "love-travel-server" cmd /k "%~dp0start-server.cmd"

echo [4/4] Starting Next.js front...
start "love-travel-front" cmd /k "%~dp0start-front.cmd"

echo.
echo Started. Useful URLs:
echo   Front:       http://127.0.0.1:3000
echo   Java API:    http://127.0.0.1:8080
echo   AI health:   http://127.0.0.1:8000/internal/health
echo.
echo Keep the opened command windows running while developing.
