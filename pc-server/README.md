# PCMon — serveur PC

Serveur HTTP minimal qui pousse les métriques du PC en temps réel (SSE) vers l'appli Android.

## Installation

```bash
pip install -r requirements.txt
```

- `psutil` : CPU, RAM, disque, réseau, températures, batterie, processus (obligatoire).
- `nvidia-ml-py` : GPU NVIDIA (utilisation, VRAM, température). Sans cette librairie ou
  sans GPU NVIDIA, le champ `gpu` vaut simplement `{"available": false}`.

## Lancement

```bash
python3 server.py                     # 127.0.0.1:8765, 1 échantillon/s
python3 server.py --host 0.0.0.0      # accessible depuis le Wi-Fi
python3 server.py --port 9000 --interval 0.5
```

Endpoints :

| Endpoint   | Format | Description                                  |
|------------|--------|----------------------------------------------|
| `/`        | HTML   | page de test (JSON affiché)                  |
| `/metrics` | JSON   | un échantillon ponctuel                     |
| `/stream`  | SSE    | `data: {json}` poussé toutes les `--interval` secondes |

Exemple de JSON (`/metrics`) :

```json
{
  "ts": 1763900000.0,
  "host": "DESKTOP-XYZ",
  "os": "Windows 11",
  "cpu_model": "AMD Ryzen 7 7800X3D",
  "cpu": {"percent": 23.4, "per_core": [12.0, ...], "count": 16, "freq_mhz": 4200},
  "ram": {"total": 34359738368, "used": 12345678901, "percent": 35.9},
  "swap": {"percent": 0.0},
  "disk": {"percent": 61.2, "total": 512000000000, "used": 313000000000,
           "read_mbps": 120.5, "write_mbps": 42.1},
  "net": {"down_kbps": 9120.4, "up_kbps": 512.0},
  "gpu": {"available": true, "name": "NVIDIA GeForce RTX 4070",
          "percent": 12, "mem_percent": 31.0, "mem_used": 3758096384,
          "mem_total": 12884901888, "temp": 45},
  "temps": {"cpu": 55.2},
  "uptime": 86400,
  "battery": null,
  "processes": [{"name": "chrome.exe", "cpu": 12.3, "mem": 4.5}]
}
```

Les débits réseau/disk sont des taux calculés entre deux échantillons (0 au premier appel).

## Lancement automatique

### Windows (tâche planifiée)

`Planificateur de tâches` → Créer une tâche → déclencheur « À la connexion » :

```
Programme : python3.exe
Arguments : C:\chemin\vers\pc-server\server.py --host 127.0.0.1 --port 8765
```

### Linux (systemd, utilisateur)

`~/.config/systemd/user/pcmon.service` :

```ini
[Unit]
Description=PCMon serveur de métriques

[Service]
ExecStart=/usr/bin/python3 /home/%u/pc-server/server.py --host 127.0.0.1 --port 8765
Restart=always

[Install]
WantedBy=default.target
```

```bash
systemctl --user enable --now pcmon.service
```

## Sécurité

Par défaut (`--host 127.0.0.1`), seules les connexions locales sont acceptées — idéal avec
`adb reverse`. Avec `--host 0.0.0.0` (nécessaire en Wi-Fi), le port est visible sur le réseau
local : garde le pare-feu actif et n'ouvre le port 8765 que sur ton réseau de confiance.
