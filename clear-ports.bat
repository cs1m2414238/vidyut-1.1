@echo off
title Vidyut EV - Port Cleaner
echo.
echo ========================================================
echo   VIDYUT EV - CLEARING ALL OCCUPIED PORTS
echo ========================================================
echo.

node "%~dp0scripts\clear-ports.js"

echo.
echo ========================================================
echo   Done! You can now run "npm run dev"
echo ========================================================
echo.
pause
