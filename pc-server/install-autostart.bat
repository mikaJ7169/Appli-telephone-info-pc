@echo off
echo Installation du demarrage automatique de PCMon (ouverture de session Windows)...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-autostart.ps1"
pause
