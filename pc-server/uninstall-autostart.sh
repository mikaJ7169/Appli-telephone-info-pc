#!/bin/sh
systemctl --user disable --now pcmon.service 2>/dev/null
rm -f "$HOME/.config/systemd/user/pcmon.service"
systemctl --user daemon-reload
echo "OK : demarrage automatique supprime."
