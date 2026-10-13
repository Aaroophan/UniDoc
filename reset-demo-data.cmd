@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\reset-demo-data.ps1"
exit /b %ERRORLEVEL%
