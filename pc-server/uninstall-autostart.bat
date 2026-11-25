@echo off
echo Suppression du demarrage automatique de PCMon...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0uninstall-autostart.ps1"
pause
