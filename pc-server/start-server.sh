#!/bin/sh
cd "$(dirname "$0")"
python3 -c "import psutil" 2>/dev/null || pip install -r requirements.txt
exec python3 server.py --host 0.0.0.0
