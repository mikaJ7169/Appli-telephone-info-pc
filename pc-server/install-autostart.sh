#!/bin/sh
DIR="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$HOME/.config/systemd/user"
cat > "$HOME/.config/systemd/user/pcmon.service" <<EOF
[Unit]
Description=PCMon serveur de metriques
After=network-online.target

[Service]
ExecStart=/usr/bin/env python3 $DIR/server.py --host 0.0.0.0 --port 8765
Restart=always
RestartSec=3

[Install]
WantedBy=default.target
EOF
systemctl --user daemon-reload
systemctl --user enable --now pcmon.service
echo "OK : pcmon.service active — le serveur demarre automatiquement a chaque session."
echo "Voir l'etat : systemctl --user status pcmon.service   |   retirer : ./uninstall-autostart.sh"
