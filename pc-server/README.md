# PCMon — serveur PC

Serveur HTTP minimal qui pousse les métriques du PC en temps réel (SSE) vers l'appli Android.

## Installation

```bash
pip install -r requirements.txt
```

- `psutil` : CPU, RAM, disque, réseau, températures, batterie, processus (obligatoire).
- `nvidia-ml-py` : GPU **NVIDIA** (utilisation, VRAM, température) via NVML.
- `amdsmi` (Linux uniquement) : GPU **AMD** via la librairie officielle AMD (utilisation,
  VRAM, température). Sans `amdsmi`, repli sur la commande `rocm-smi` si installée.
- `wmi` (Windows uniquement) : nom de la carte graphique.
- Sur **Windows**, en l'absence de NVML, les **compteurs de performance Windows**
  (`Get-Counter` : GPU Engine / GPU Adapter Memory) donnent utilisation + VRAM pour
  n'importe quel GPU (AMD, Intel…), sans dépendance supplémentaire.

Sans aucun de ces backends, le champ `gpu` vaut `{"available": false, "name": "<carte>"}`
quand le nom est trouvable.

## Lancement

```bash
python3 server.py --host 0.0.0.0      # recommandé : 0.0.0.0:8765, 1 échantillon/s
python3 server.py                     # 127.0.0.1:8765 (adb reverse uniquement)
python3 server.py --port 9000 --interval 0.5
# Windows : double-clic sur start-server.bat (idem pour start-server.sh sous Linux/macOS)
```

**Découverte automatique** : le serveur s'annonce toutes les 2 s en broadcast UDP sur le
port **8766** (`PCMON/1 <nom-du-PC> <port>`), sur 255.255.255.255 et sur l'adresse de
broadcast de chaque interface (Wi-Fi, Ethernet, partage USB…). L'appli écoute ce port et
se connecte toute seule — plus besoin de connaître l'IP du PC.

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

## Lancement automatique au démarrage du PC

Rien à lancer à la main : une fois installé, le serveur tourne dès l'ouverture de session.

### Windows

Double-clic sur **`install-autostart.bat`** → crée un lanceur caché
(`pcmon-launch.vbs`) + un raccourci dans le dossier **Démarrage**
(`%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\PCMon Server.lnk`).
Le serveur démarre alors automatiquement à chaque ouverture de session, fenêtre cachée.
Pour retirer : double-clic sur **`uninstall-autostart.bat`**.

> Au premier lancement, le pare-feu Windows peut demander d'autoriser `python.exe` :
> coche **réseau privé** → OK (à faire une seule fois).

### Linux (systemd, utilisateur)

```bash
./install-autostart.sh     # crée et active ~/.config/systemd/user/pcmon.service
./uninstall-autostart.sh   # retire le démarrage automatique
```

Vérifier : `systemctl --user status pcmon.service` — le serveur démarre à chaque session
et redémarre automatiquement s'il plante (`Restart=always`).

## Sécurité

Par défaut (`--host 127.0.0.1`), seules les connexions locales sont acceptées — idéal avec
`adb reverse`. Avec `--host 0.0.0.0` (nécessaire en Wi-Fi), le port est visible sur le réseau
local : garde le pare-feu actif et n'ouvre le port 8765 que sur ton réseau de confiance.
