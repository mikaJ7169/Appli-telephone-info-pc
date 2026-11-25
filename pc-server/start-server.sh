#!/bin/sh
cd "$(dirname "$0")"
pip install -r requirements.txt
python3 server.py --host 0.0.0.0
