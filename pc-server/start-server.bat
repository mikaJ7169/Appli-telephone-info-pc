@echo off
cd /d "%~dp0"
python -c "import psutil" 2>nul || python -m pip install -r requirements.txt
python server.py --host 0.0.0.0
pause
