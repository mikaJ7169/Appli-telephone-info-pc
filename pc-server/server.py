#!/usr/bin/env python3
import argparse
import json
import platform
import re
import socket
import subprocess
import threading
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


_AMDSMI = None


def _amdsmi_lib():
    global _AMDSMI
    if _AMDSMI is None:
        try:
            import amdsmi

            amdsmi.amdsmi_init()
            _AMDSMI = amdsmi
        except Exception:
            _AMDSMI = False
    return _AMDSMI or None


def _gpu_nvml():
    if not _NVML or pynvml.nvmlDeviceGetCount() == 0:
        return None
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


def _gpu_amdsmi():
    amdsmi = _amdsmi_lib()
    if amdsmi is None:
        return None
    handle = None
    for h in amdsmi.amdsmi_get_processor_handles():
        try:
            amdsmi.amdsmi_get_gpu_busy_percent(h)
            handle = h
            break
        except Exception:
            continue
    if handle is None:
        return None
    used = amdsmi.amdsmi_get_gpu_memory_usage(handle, amdsmi.AmdSmiMemoryType.VRAM)
    total = amdsmi.amdsmi_get_gpu_memory_total(handle, amdsmi.AmdSmiMemoryType.VRAM)
    name = ""
    try:
        name = amdsmi.amdsmi_get_gpu_asic_info(handle).get("market_name", "") or ""
    except Exception:
        pass
    temp = None
    try:
        temp = amdsmi.amdsmi_get_temp_metric(
            handle, amdsmi.AmdSmiTemperatureType.EDGE, amdsmi.AmdSmiTemperatureMetric.CURRENT
        )
    except Exception:
        pass
    return {
        "available": True,
        "name": name,
        "percent": amdsmi.amdsmi_get_gpu_busy_percent(handle),
        "mem_percent": round(used * 100.0 / total, 1) if total else 0.0,
        "mem_used": int(used),
        "mem_total": int(total),
        "temp": temp,
    }


def _gpu_rocm_smi():
    try:
        out = subprocess.run(
            ["rocm-smi", "--showuse", "--showvram", "--showtemp", "--json"],
            capture_output=True,
            text=True,
            timeout=10,
        ).stdout
        data = json.loads(out)
    except Exception:
        return None
    card = next(iter(data.values()), None)
    if not isinstance(card, dict):
        return None

    def find(*keys):
        for k, v in card.items():
            lk = k.lower()
            if all(s in lk for s in keys):
                return v
        return None

    def num(v):
        if v is None:
            return None
        if isinstance(v, (int, float)):
            return float(v)
        m = re.search(r"-?\d+(\.\d+)?", str(v))
        return float(m.group(0)) if m else None

    use = num(find("use"))
    used = num(find("vram", "used"))
    total = num(find("vram", "total"))
    temp = num(find("temp"))
    if use is None:
        return None
    return {
        "available": True,
        "name": "AMD (rocm-smi)",
        "percent": use,
        "mem_percent": round(used * 100.0 / total, 1) if used is not None and total else 0.0,
        "mem_used": int(used or 0),
        "mem_total": int(total or 0),
        "temp": int(temp) if temp is not None else None,
    }


_win_gpu_cache = {"t": 0.0, "m": None}


def _gpu_windows_counters():
    if platform.system() != "Windows":
        return None
    now = time.time()
    if now - _win_gpu_cache["t"] < 5:
        return _win_gpu_cache["m"]
    ps = (
        "$u=(Get-Counter '\\GPU Engine(*)\\Utilization Percentage' -ErrorAction SilentlyContinue).CounterSamples | Measure-Object CookedValue -Sum | Select-Object -ExpandProperty Sum;"
        "$m=(Get-Counter '\\GPU Adapter Memory(*)\\Dedicated Usage' -ErrorAction SilentlyContinue).CounterSamples | Measure-Object CookedValue -Sum | Select-Object -ExpandProperty Sum;"
        "$t=(Get-Counter '\\GPU Adapter Memory(*)\\Dedicated Limit' -ErrorAction SilentlyContinue).CounterSamples | Measure-Object CookedValue -Maximum | Select-Object -ExpandProperty Maximum;"
        "$n=(Get-CimInstance Win32_VideoController -ErrorAction SilentlyContinue | Where-Object {$_.Name -notmatch 'basic'} | Select-Object -First 1 -ExpandProperty Name);"
        "[PSCustomObject]@{u=[double]$u;m=[long]$m;t=[long]$t;n=$n} | ConvertTo-Json -Compress"
    )
    try:
        r = subprocess.run(
            ["powershell", "-NoProfile", "-Command", ps],
            capture_output=True,
            text=True,
            timeout=20,
        )
        d = json.loads(r.stdout.strip())
    except Exception:
        d = {}
    used = int(d.get("m") or 0)
    total = int(d.get("t") or 0)
    m = None
    if d.get("n"):
        m = {
            "available": True,
            "name": d["n"],
            "percent": round(float(d.get("u") or 0.0), 1),
            "mem_percent": round(used * 100.0 / total, 1) if total else 0.0,
            "mem_used": used,
            "mem_total": total,
            "temp": None,
        }
    _win_gpu_cache["t"] = now
    _win_gpu_cache["m"] = m
    return m


def _gpu_name_only():
    try:
        if platform.system() == "Windows":
            import wmi

            for v in wmi.WMI().Win32_VideoController():
                if v.Name and "basic" not in v.Name.lower():
                    return v.Name
        else:
            out = subprocess.run(["lspci"], capture_output=True, text=True, timeout=10).stdout
            for line in out.splitlines():
                if "VGA compatible controller" in line or "3D controller" in line:
                    return line.split(":", 2)[-1].strip()
    except Exception:
        pass
    return ""


def gpu_metrics():
    for fn in (_gpu_nvml, _gpu_amdsmi, _gpu_rocm_smi, _gpu_windows_counters):
        try:
            m = fn()
        except Exception:
            m = None
        if m and m.get("available"):
            return m
    return {"available": False, "name": _gpu_name_only()}


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


DISCOVERY_PORT = 8766


def _discovery_broadcast(port):
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    msg = f"PCMON/1 {socket.gethostname()} {port}".encode("utf-8")
    targets = {"255.255.255.255"}
    try:
        for addrs in psutil.net_if_addrs().values():
            for a in addrs:
                if a.family == socket.AF_INET and getattr(a, "broadcast", None):
                    targets.add(a.broadcast)
    except Exception:
        pass
    while True:
        for t in targets:
            try:
                s.sendto(msg, (t, DISCOVERY_PORT))
            except Exception:
                pass
        time.sleep(2)


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

    threading.Thread(target=_discovery_broadcast, args=(args.port,), daemon=True).start()
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
    print(f"Découverte auto : annonce UDP 'PCMON/1 <hôte> <port>' sur le port {DISCOVERY_PORT} (toutes les interfaces)")
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
