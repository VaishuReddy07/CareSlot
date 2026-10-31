@echo off
setlocal
cd /d "%~dp0"

docker info >nul 2>&1
if errorlevel 1 (
  echo Docker Desktop is not running. Start Docker Desktop, wait until it is ready, then run this file again.
  pause
  exit /b 1
)

docker compose up --build -d
if errorlevel 1 (
  echo CareSlot could not start. Review the Docker Compose output above.
  pause
  exit /b 1
)

start "" "http://localhost:5173"
echo CareSlot is starting. The first launch may take a few minutes while images are built.
pause
