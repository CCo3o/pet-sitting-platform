@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0frontend.ps1" %*
exit /b %errorlevel%
