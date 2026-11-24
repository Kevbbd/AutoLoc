@echo off
cd /d "%~dp0.."
docker compose down
if /i not "%~1"=="nopause" pause
