# PCMON — moniteur PC temps réel sur ton téléphone

Une fois ton téléphone relié à ton PC (USB ou Wi-Fi), l'appli affiche en temps réel des
courbes style pixel-art (thème noir / blanc / gris) :

- **CPU** : charge globale %, nombre de cœurs, fréquence
- **RAM** : utilisation %, Go utilisés / total, swap
- **GPU** : charge %, VRAM, température (NVIDIA via NVML)
- **Réseau** : débit descendant / montant (Ko/s, Mo/s)
- **Disque (SSD)** : occupation %, débit lecture / écriture (Mo/s)
- **Température CPU**
- **Système** : hôte, OS, modèle CPU, uptime, batterie (si portable)
- **Processus** : top 5 par consommation CPU

## Architecture

```
┌────────────┐   HTTP / SSE (1 échantillon/s)   ┌──────────────────┐
│  Téléphone  │ ◄─────────────────────────────  │  PC : server.py  │
│  (Android) │   /stream  →  data: {json}      │  (psutil + NVML) │
│   PCMON    │                                 │  port 8765       │
└────────────┘                                 └──────────────────┘
```

- `pc-server/` : petit serveur Python (stdlib + `psutil` + `nvidia-ml-py`) qui expose
  `/metrics` (JSON ponctuel) et `/stream` (Server-Sent Events, poussé chaque seconde).
- `android/` : appli Android Jetpack Compose. Les courbes sont dessinées sur un `Canvas`
  par colonnes de pixels (rendu sans anti-aliasing, grille 20 rangées).

## 1. Installation côté PC

Prérequis : Python 3.10+.

```bash
cd pc-server
pip install -r requirements.txt        # psutil (obligatoire) + nvidia-ml-py (GPU NVIDIA)
python3 server.py                      # écoute sur 127.0.0.1:8765
```

Options : `--host 0.0.0.0` (accès depuis le Wi-Fi), `--port 8765`, `--interval 1.0` (secondes).

Test rapide dans un navigateur du PC : [http://127.0.0.1:8765/](http://127.0.0.1:8765/)

Par défaut le serveur n'écoute que sur `127.0.0.1` (rien d'exposé sur le réseau).
Passe `--host 0.0.0.0` uniquement pour le Wi-Fi, et laisse le pare-feu bloquer le port 8765
depuis l'extérieur (ou limite-le à ton réseau local).

## 2. Relier le téléphone au PC

### a) Câble USB + `adb reverse` (le plus simple)

```bash
adb reverse tcp:8765 tcp:8765
```

Dans l'appli, adresse : `http://127.0.0.1:8765` (le tunnel USB redirige le port du téléphone
vers le PC). Nécessite le débogage USB activé sur le téléphone.

### b) Câble USB + partage de connexion (USB tethering)

Active le partage de connexion USB sur le téléphone. Le PC obtient une IP du type
`192.168.42.129`. Dans l'appli, adresse : `http://192.168.42.129:8765`
(adapte l'IP affichée dans les réglages du partage de connexion).

### c) Wi-Fi (même réseau local)

```bash
python3 server.py --host 0.0.0.0
```

Dans l'appli, adresse : `http://<IP-du-PC>:8765` (l'IP du PC est affichée au démarrage
du serveur, ou via `ipconfig` / `ip addr`).

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
- **GPU « N/A »** : NVML ne voit que les cartes NVIDIA. Sur AMD/Intel, la carte affiche N/A
  (le reste fonctionne). Vérifie `pip install nvidia-ml-py`.
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
