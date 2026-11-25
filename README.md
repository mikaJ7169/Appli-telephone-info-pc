# PCMON — moniteur PC temps réel sur ton téléphone

Branche ton téléphone à ton PC (USB ou Wi-Fi), lance l'appli : **aucune IP à saisir**,
le PC est détecté automatiquement. L'appli affiche alors en temps réel des courbes lissées
dans un thème sombre moderne (noir / blanc / gris), utilisable en **portrait et paysage**,
et elle **empêche la mise en veille de l'écran** tant qu'elle tourne :

- **CPU** : charge globale %, nombre de cœurs, fréquence
- **RAM** : utilisation %, Go utilisés / total, swap
- **GPU** : charge %, VRAM, température — **NVIDIA (NVML) et AMD (amdsmi / rocm-smi / compteurs Windows)**
- **Réseau** : débit descendant / montant (Ko/s, Mo/s)
- **Disque (SSD)** : occupation %, débit lecture / écriture (Mo/s)
- **Température CPU**
- **Système** : hôte, OS, modèle CPU, uptime, batterie (si portable)
- **Processus** : top 5 par consommation CPU

## Architecture

```
┌────────────┐  UDP 8766 : annonce ─────────►  ┌──────────────────────┐
│  Téléphone  │                               │   PC : server.py     │
│   PCMON    │  HTTP/SSE 8765 : métriques ◄── │  psutil + NVML /     │
│  (Android) │  /stream → data: {json} 1/s   │  amdsmi + counters   │
└────────────┘  (découverte automatique)      └──────────────────────┘
```

- `pc-server/` : petit serveur Python (stdlib + `psutil` + `nvidia-ml-py` / `amdsmi` + `wmi`)
  qui expose `/metrics` (JSON ponctuel) et `/stream` (Server-Sent Events, poussé chaque
  seconde), et s'annonce en broadcast UDP sur le port 8766 (`PCMON/1 <hôte> <port>`).
- `android/` : appli Android Jetpack Compose. Courbes lissées dessinées sur `Canvas`
  (lignes arrondies + remplissage en dégradé), grille adaptative (2–4 colonnes selon
  portrait/paysage), reconnexion et découverte automatiques.

## Guide visuel (fichier canvas)

Ouvrez [`docs/guide-canvas.html`](docs/guide-canvas.html) dans n'importe quel navigateur :
un poster pixel-art (thème noir/blanc/gris) dessiné sur un élément `<canvas>`, en 2 pages
(clic ou flèche → pour changer de page) — **1/2 Installer** (PC, lien téléphone, appli)
et **2/2 Utiliser** (légende des cartes, dépannage). Capture d'écran ou Ctrl+P pour
l'exporter en image (importable ensuite dans Canva ou autre outil de design).

## 1. Installation côté PC

Prérequis : Python 3.10+.

```bash
cd pc-server
pip install -r requirements.txt        # psutil + nvidia-ml-py (NVIDIA) + amdsmi (AMD Linux) + wmi (Windows)
python3 server.py --host 0.0.0.0       # ou double-clic sur start-server.bat (Windows)
```

**Zéro configuration ensuite** : double-clic une seule fois sur `pc-server\install-autostart.bat`
(Windows) ou `pc-server/install-autostart.sh` (Linux) → le serveur démarre **tout seul à
chaque démarrage du PC**. Ensuite : tu branches le téléphone, tu lances l'appli, c'est connecté.

Options : `--host 0.0.0.0` (accès depuis le Wi-Fi), `--port 8765`, `--interval 1.0` (secondes).

Test rapide dans un navigateur du PC : [http://127.0.0.1:8765/](http://127.0.0.1:8765/)

Par défaut le serveur n'écoute que sur `127.0.0.1` (rien d'exposé sur le réseau).
Passe `--host 0.0.0.0` uniquement pour le Wi-Fi, et laisse le pare-feu bloquer le port 8765
depuis l'extérieur (ou limite-le à ton réseau local).

## 2. Relier le téléphone au PC — c'est automatique

Lance le serveur avec `--host 0.0.0.0` (ou `start-server.bat`), puis :

- **Câble USB + partage de connexion** activé sur le téléphone, **ou**
- téléphone sur le **même Wi-Fi** que le PC,

et ouvre simplement l'appli : elle détecte le PC toute seule (annonce UDP 8766 reçue du
serveur, + sonde de la passerelle du réseau actif — en USB tethering la passerelle est
le PC). Aucune adresse à saisir. ✅

**Secours manuel** (champ dans l'appli, ex. `http://192.168.1.21:8765` — l'IP du PC est
affichée au démarrage du serveur ou via `ipconfig`) :

- USB + `adb reverse tcp:8765 tcp:8765` → `http://127.0.0.1:8765` (débogage USB requis)
- Partage USB → `http://192.168.42.129:8765` (IP du PC côté partage)

## 3. Compiler et installer l'appli Android

Prérequis : Android Studio (ou un JDK 17 + Android SDK) avec accès à Internet pour la
première synchronisation Gradle.

```bash
cd android
./gradlew assembleDebug        # Linux / macOS
.\gradlew.bat assembleDebug    # Windows
```

L'APK est généré dans `android/app/build/outputs/apk/debug/app-debug.apk`.
Transfère-le sur le téléphone (mail, câble, cloud...) et installe-le
("autoriser les sources inconnues" si demandé). Alternative : ouvrir le dossier `android/`
directement dans Android Studio puis **Run** sur le téléphone en USB.

Au premier lancement, l'appli tente de se reconnecter automatiquement à la dernière adresse
enregistrée.

## Dépannage

- **« hors ligne » dans l'appli** : vérifie que `server.py` tourne, que l'adresse est bonne,
  et teste depuis le navigateur du téléphone (`http://<IP>:8765/`).
- **adb reverse** : la commande ne survit pas au débranchement du câble ; la relancer à
  chaque reconnexion, ou passe en partage USB / Wi-Fi.
- **GPU « N/A »** : NVIDIA → `nvidia-ml-py` (NVML) ; AMD → `amdsmi` sous Linux (ou
  `rocm-smi` installé) et les compteurs de performance Windows (`Get-Counter`, fonctionne
  aussi pour Intel). Sinon, seul le nom de la carte est affiché.
- **Température CPU absente** : dépend des capteurs exposés par la carte mère (Linux :
  paquet `lm-sensors`, Windows : généralement OK).
- **Wi-Fi** : le pare-feu Windows peut demander d'autoriser Python sur le réseau privé.

## Structure du dépôt

```
pc-server/            serveur de métriques (Python)
  server.py           endpoints / /metrics /stream
  requirements.txt
android/              appli Android (Kotlin + Jetpack Compose)
  app/src/main/java/dev/vibe/pcmon/
    data/             modèle JSON, client SSE, historique glissant
    ui/               écrans, courbes pixel, ViewModel
    theme/            palette noir/blanc/gris + police monospace
```
