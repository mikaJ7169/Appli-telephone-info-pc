#!/usr/bin/env python3
import argparse
import json
import platform
import socket
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import psutil

try:
    import pynvml

    pynvml.nvmlInit()
    _NVML = True
except Exception:
    _NVML = False

INTERVAL = 1.0
_prev = {"t": None, "net": None, "disk": None}

INDEX = """<!doctype html>
<html lang="fr"><head><meta charset="utf-8"><title>PCMon</title>
<style>body{background:#000;color:#eee;font-family:monospace;padding:2em}pre{color:#9a9a9a}</style>
</head><body><h1>PCMon OK</h1><pre id="o">connexion...</pre>
<script>
const o = document.getElementById('o');
fetch('/metrics').then(r => r.json()).then(j => { o.textContent = JSON.stringify(j, null, 2); });
</script></body></html>
"""


def cpu_model():
    if platform.system() == "Linux":
        try:
            with open("/proc/cpuinfo", encoding="utf-8", errors="ignore") as f:
                for line in f:
                    if line.startswith("model name"):
                        return line.split(":", 1)[1].strip()
        except OSError:
            pass
    return platform.processor() or "CPU"


def gpu_metrics():
    if not _NVML:
        return {"available": False}
    try:
        if pynvml.nvmlDeviceGetCount() == 0:
            return {"available": False}
        h = pynvml.nvmlDeviceGetHandleByIndex(0)
        name = pynvml.nvmlDeviceGetName(h)
        if isinstance(name, bytes):
            name = name.decode("utf-8", "ignore")
        util = pynvml.nvmlDeviceGetUtilizationRates(h)
        mem = pynvml.nvmlDeviceGetMemoryInfo(h)
        temp = pynvml.nvmlDeviceGetTemperature(h, pynvml.NVML_TEMPERATURE_GPU)
        return {
            "available": True,
            "name": name,
            "percent": util.gpu,
            "mem_percent": round(mem.used * 100.0 / mem.total, 1) if mem.total else 0.0,
            "mem_used": mem.used,
            "mem_total": mem.total,
            "temp": temp,
        }
    except Exception:
        return {"available": False}


def io_rates():
    now = time.time()
    net = psutil.net_io_counters()
    disk = psutil.disk_io_counters()
    rates = {"down_kbps": 0.0, "up_kbps": 0.0, "read_mbps": 0.0, "write_mbps": 0.0}
    if _prev["t"] is not None:
        dt = max(now - _prev["t"], 1e-3)
        rates = {
            "down_kbps": max(net.bytes_recv - _prev["net"].bytes_recv, 0) * 8.0 / 1e3 / dt,
            "up_kbps": max(net.bytes_sent - _prev["net"].bytes_sent, 0) * 8.0 / 1e3 / dt,
            "read_mbps": max(disk.read_bytes - _prev["disk"].read_bytes, 0) / 1e6 / dt,
            "write_mbps": max(disk.write_bytes - _prev["disk"].write_bytes, 0) / 1e6 / dt,
        }
    _prev["t"] = now
    _prev["net"] = net
    _prev["disk"] = disk
    return rates


def cpu_temp():
    try:
        temps = psutil.sensors_temperatures()
    except Exception:
        return None
    for entries in temps.values():
        if entries:
            return round(entries[0].current, 1)
    return None


def battery():
    try:
        b = psutil.sensors_battery()
    except Exception:
        return None
    if b is None:
        return None
    return {"percent": b.percent, "plugged": bool(b.power_plugged)}


def top_processes(n=5):
    out = []
    for p in psutil.process_iter(["name", "cpu_percent", "memory_percent"]):
        try:
            out.append(
                {
                    "name": p.info["name"] or "?",
                    "cpu": round(p.info["cpu_percent"] or 0.0, 1),
                    "mem": round(p.info["memory_percent"] or 0.0, 2),
                }
            )
        except Exception:
            pass
    out.sort(key=lambda x: x["cpu"], reverse=True)
    return out[:n]


def snapshot():
    per_core = psutil.cpu_percent(interval=None, percpu=True)
    overall = round(sum(per_core) / len(per_core), 1) if per_core else 0.0
    freq = psutil.cpu_freq()
    vm = psutil.virtual_memory()
    sm = psutil.swap_memory()
    du = psutil.disk_usage("/")
    r = io_rates()
    return {
        "ts": time.time(),
        "host": socket.gethostname(),
        "os": f"{platform.system()} {platform.release()}",
        "cpu_model": cpu_model(),
        "cpu": {
            "percent": overall,
            "per_core": [round(c, 1) for c in per_core],
            "count": psutil.cpu_count(logical=True),
            "freq_mhz": round(freq.current) if freq else None,
        },
        "ram": {"total": vm.total, "used": vm.used, "percent": vm.percent},
        "swap": {"percent": sm.percent},
        "disk": {
            "percent": du.percent,
            "total": du.total,
            "used": du.used,
            "read_mbps": round(r["read_mbps"], 2),
            "write_mbps": round(r["write_mbps"], 2),
        },
        "net": {"down_kbps": round(r["down_kbps"], 1), "up_kbps": round(r["up_kbps"], 1)},
        "gpu": gpu_metrics(),
        "temps": {"cpu": cpu_temp()},
        "uptime": round(time.time() - psutil.boot_time()),
        "battery": battery(),
        "processes": top_processes(),
    }


class Handler(BaseHTTPRequestHandler):
    server_version = "PCMon/1.0"

    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path == "/metrics":
            self._send_json(snapshot())
        elif path == "/stream":
            self._send_sse()
        elif path in ("/", "/index.html"):
            body = INDEX.encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_error(404)

    def _send_json(self, obj):
        body = json.dumps(obj).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_sse(self):
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "keep-alive")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        try:
            while True:
                payload = f"data: {json.dumps(snapshot())}\n\n".encode("utf-8")
                self.wfile.write(payload)
                self.wfile.flush()
                time.sleep(INTERVAL)
        except (BrokenPipeError, ConnectionResetError, OSError):
            pass

    def log_message(self, fmt, *args):
        pass


def main():
    global INTERVAL
    ap = argparse.ArgumentParser(description="Serveur de métriques PC temps réel pour l'appli PCMon")
    ap.add_argument("--host", default="127.0.0.1", help="adresse d'écoute (127.0.0.1 par défaut, 0.0.0.0 pour le Wi-Fi)")
    ap.add_argument("--port", type=int, default=8765)
    ap.add_argument("--interval", type=float, default=1.0, help="secondes entre deux échantillons")
    args = ap.parse_args()
    INTERVAL = max(args.interval, 0.2)

    for p in psutil.process_iter():
        try:
            p.cpu_percent(None)
        except Exception:
            pass

    srv = ThreadingHTTPServer((args.host, args.port), Handler)
    ips = sorted(
        {
            addr.address
            for addrs in psutil.net_if_addrs().values()
            for addr in addrs
            if addr.family == socket.AF_INET and not addr.address.startswith("127.")
        }
    )
    print(f"PCMon écoute sur http://{args.host}:{args.port} (intervalle {INTERVAL}s)")
    print("Endpoints : /  (test navigateur), /metrics (JSON), /stream (SSE)")
    if args.host in ("127.0.0.1", "localhost"):
        print(f"USB via adb : adb reverse tcp:{args.port} tcp:{args.port}")
        print(f"  puis dans l'appli : http://127.0.0.1:{args.port}")
    print(f"Wi-Fi : lance avec --host 0.0.0.0 puis connecte l'appli à http://<IP-du-PC>:{args.port}")
    if ips:
        print(f"  IPs du PC : {', '.join(ips)}")
    print("Ctrl+C pour arrêter.")
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
