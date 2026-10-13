@echo off
setlocal
REM Local, non-destructive quality gates only. No demo reset, MySQL mutation, or SMTP run.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\verify-release.ps1" %*
exit /b %ERRORLEVEL%
