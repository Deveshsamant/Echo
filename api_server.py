"""
Echo AI Agent — HTTP API Server
Serves real-time PC stats and command execution for the Echo Mobile App.
Runs on port 5000 over local WiFi.
Supports PC Control mode and Personal AI Assistant mode.
"""

import base64
import json
import logging
import os
import platform
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

import psutil

import config
import database as db

log = logging.getLogger("echo.api")

# Reference to the brains — set by main.py
_brain = None           # EchoBrain (PC control)
_assistant = None       # AssistantBrain (personal AI)
_ngrok_url = None       # Set by main.py when tunnel starts
_current_mode = "pc_control"  # "pc_control" or "assistant"

# Command queue: only one command runs at a time
_command_lock = threading.Lock()

# Shared command log — visible from all sources (PC dashboard, mobile, Telegram, voice)
_command_log = []  # list of {id, source, command, response, timestamp, status}
_command_log_id = 0

def add_command_log(source: str, command: str, response: str = None, status: str = "done"):
    """Add an entry to the shared command log."""
    global _command_log_id
    import time as _t
    _command_log_id += 1
    entry = {
        "id": _command_log_id,
        "source": source,
        "command": command,
        "response": response or "",
        "timestamp": _t.time(),
        "status": status,
    }
    _command_log.append(entry)
    if len(_command_log) > 100:
        _command_log.pop(0)
    return entry

# Shared camera frame buffer — dashboard sends its video frames here
_latest_camera_frame = None  # base64 JPEG string
_camera_frame_time = 0       # timestamp of last frame

def set_camera_frame(frame_b64: str):
    """Store the latest camera frame from the dashboard."""
    global _latest_camera_frame, _camera_frame_time
    import time as _t
    _latest_camera_frame = frame_b64
    _camera_frame_time = _t.time()

def get_camera_frame():
    """Get the latest camera frame if it's fresh (< 10 seconds old)."""
    import time as _t
    if _latest_camera_frame and (_t.time() - _camera_frame_time) < 10:
        return _latest_camera_frame
    return None

# Cached CPU % updated in background to avoid blocking API handler
_cached_cpu = 0.0

def _cpu_sampler():
    global _cached_cpu
    import time
    while True:
        _cached_cpu = psutil.cpu_percent(interval=2)
        time.sleep(2)

_cpu_thread = threading.Thread(target=_cpu_sampler, daemon=True)
_cpu_thread.start()

# Cached GPU stats — nvidia-smi is expensive, so cache for 30 seconds
_cached_gpu = {"gpu_percent": -1, "gpu_mem_used_mb": 0, "gpu_mem_total_mb": 0}
_gpu_cache_time = 0

def _get_gpu_stats() -> dict:
    global _cached_gpu, _gpu_cache_time
    import time as _time
    now = _time.time()
    if now - _gpu_cache_time < 30:
        return _cached_gpu
    try:
        import subprocess
        result = subprocess.run(
            "nvidia-smi --query-gpu=utilization.gpu,memory.used,memory.total --format=csv,noheader,nounits",
            shell=True, capture_output=True, text=True, timeout=3,
        )
        if result.returncode == 0:
            parts = result.stdout.strip().split(",")
            _cached_gpu = {
                "gpu_percent": int(parts[0].strip()),
                "gpu_mem_used_mb": int(parts[1].strip()),
                "gpu_mem_total_mb": int(parts[2].strip()),
            }
        else:
            _cached_gpu = {"gpu_percent": -1, "gpu_mem_used_mb": 0, "gpu_mem_total_mb": 0}
    except Exception:
        _cached_gpu = {"gpu_percent": -1, "gpu_mem_used_mb": 0, "gpu_mem_total_mb": 0}
    _gpu_cache_time = now
    return _cached_gpu


def set_brain(brain):
    global _brain
    _brain = brain


def set_assistant(assistant):
    global _assistant
    _assistant = assistant


def _get_system_stats() -> dict:
    """Collect comprehensive system stats."""
    mem = psutil.virtual_memory()
    stats = {
        "status": "online",
        "name": config.AGENT_NAME,
        "os": f"{platform.system()} {platform.release()}",
        "cpu_percent": _cached_cpu,
        "cpu_count": psutil.cpu_count(),
        "ram_used_gb": round(mem.used / (1024**3), 1),
        "ram_total_gb": round(mem.total / (1024**3), 1),
        "ram_percent": mem.percent,
    }

    # Disk
    try:
        disk = psutil.disk_usage("C:\\")
        stats["disk_used_gb"] = round(disk.used / (1024**3), 1)
        stats["disk_total_gb"] = round(disk.total / (1024**3), 1)
        stats["disk_percent"] = round(disk.percent, 1)
    except Exception:
        stats["disk_percent"] = 0

    # Battery
    try:
        bat = psutil.sensors_battery()
        if bat:
            stats["battery_percent"] = bat.percent
            stats["battery_plugged"] = bat.power_plugged
            stats["battery_secs_left"] = bat.secsleft if bat.secsleft != psutil.POWER_TIME_UNLIMITED else -1
        else:
            stats["battery_percent"] = -1
            stats["battery_plugged"] = True
            stats["battery_secs_left"] = -1
    except Exception:
        stats["battery_percent"] = -1
        stats["battery_plugged"] = True
        stats["battery_secs_left"] = -1

    # Active window
    try:
        import ctypes
        hwnd = ctypes.windll.user32.GetForegroundWindow()
        buf = ctypes.create_unicode_buffer(256)
        ctypes.windll.user32.GetWindowTextW(hwnd, buf, 256)
        stats["active_window"] = buf.value or "Desktop"
        stats["active_window_pid"] = 0
        pid = ctypes.c_ulong()
        ctypes.windll.user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
        stats["active_window_pid"] = pid.value
    except Exception:
        stats["active_window"] = "Unknown"
        stats["active_window_pid"] = 0

    # Network
    try:
        net = psutil.net_io_counters()
        stats["net_bytes_sent"] = net.bytes_sent
        stats["net_bytes_recv"] = net.bytes_recv
    except Exception:
        stats["net_bytes_sent"] = 0
        stats["net_bytes_recv"] = 0

    # GPU (cached — nvidia-smi is slow)
    gpu = _get_gpu_stats()
    stats.update(gpu)

    return stats


def _read_body(handler) -> str:
    """Read and decode POST body."""
    content_length = int(handler.headers.get("Content-Length", 0))
    return handler.rfile.read(content_length).decode("utf-8")


def _parse_json(body: str) -> dict:
    """Parse JSON body, return empty dict on failure."""
    try:
        return json.loads(body)
    except (json.JSONDecodeError, AttributeError):
        return {}


class EchoAPIHandler(BaseHTTPRequestHandler):
    """HTTP request handler for Echo API."""

    def log_message(self, format, *args):
        # Suppress noisy polling endpoints from terminal
        msg = args[0] if args else ""
        # Extract the path from the log message (e.g. "GET /api/stats HTTP/1.1" -> "/api/stats")
        parts = msg.split()
        path = parts[1].split("?")[0] if len(parts) >= 2 else ""  # Strip query params
        quiet_paths = {"/api/stats", "/api/command/log", "/api/camera/frame", "/favicon.ico"}
        if path in quiet_paths:
            return
        log.info(f"API: {msg}")

    def do_OPTIONS(self):
        """Handle CORS preflight requests — required for mobile commands via Cloudflare tunnel."""
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Accept, User-Agent")
        self.send_header("Access-Control-Max-Age", "86400")
        self.end_headers()

    def _send_json(self, data: dict, status: int = 200):
        try:
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
            self.send_header("Access-Control-Allow-Headers", "Content-Type")
            self.end_headers()
            self.wfile.write(json.dumps(data).encode("utf-8"))
        except (ConnectionAbortedError, ConnectionResetError, BrokenPipeError):
            pass

    def _send_image(self, filepath: str):
        self.send_response(200)
        self.send_header("Content-Type", "image/png")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        with open(filepath, "rb") as f:
            self.wfile.write(f.read())

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path
        params = parse_qs(parsed.query)

        # ─── Dashboard ────────────────────────────────────────────
        if path == "/" or path == "/dashboard":
            try:
                dashboard_path = os.path.join(os.path.dirname(__file__), "dashboard.html")
                with open(dashboard_path, "r", encoding="utf-8") as f:
                    html = f.read()
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Cache-Control", "no-cache")
                self.end_headers()
                self.wfile.write(html.encode("utf-8"))
            except Exception as e:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(f"Dashboard error: {e}".encode())
            return

        # ─── Tunnel URL ───────────────────────────────────────────
        if path == "/api/tunnel":
            self._send_json({"url": _ngrok_url or ""})
            return

        # ─── System ──────────────────────────────────────────────
        if path == "/api/stats":
            try:
                stats = _get_system_stats()
                self._send_json(stats)
            except Exception as e:
                log.error(f"Stats collection failed: {e}")
                self._send_json({"error": str(e)}, 500)

        elif path == "/api/status":
            status = {
                "status": "online",
                "name": config.AGENT_NAME,
                "mode": _current_mode,
                "model": _brain.model_name if _brain else config.NVIDIA_MODEL,
                "provider": _brain.provider if _brain else config.MODEL_PROVIDER,
            }
            if _ngrok_url:
                status["ngrok_url"] = _ngrok_url
            self._send_json(status)

        elif path == "/api/screenshot":
            from tools import take_screenshot
            result = take_screenshot()
            filepath = result.replace("Screenshot saved to: ", "").strip()
            if os.path.exists(filepath):
                try:
                    from PIL import Image
                    import io
                    img = Image.open(filepath)
                    if img.width > 1280:
                        ratio = 1280 / img.width
                        img = img.resize((1280, int(img.height * ratio)), Image.LANCZOS)
                    buf = io.BytesIO()
                    img.convert("RGB").save(buf, format="JPEG", quality=60)
                    img_b64 = base64.b64encode(buf.getvalue()).decode("utf-8")
                except Exception:
                    with open(filepath, "rb") as f:
                        img_b64 = base64.b64encode(f.read()).decode("utf-8")
                self._send_json({"image": img_b64})
                try:
                    os.remove(filepath)
                except OSError:
                    pass
            else:
                self._send_json({"error": "Screenshot failed"}, 500)

        elif path == "/api/camera/capture":
            try:
                import cv2
                cap = cv2.VideoCapture(0, cv2.CAP_DSHOW)
                if not cap.isOpened():
                    self._send_json({"error": "Could not open webcam"}, 500)
                    return
                cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
                cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)
                ret, frame = cap.read()
                cap.release()
                if not ret or frame is None:
                    self._send_json({"error": "Failed to capture frame"}, 500)
                    return
                _, buf = cv2.imencode('.jpg', frame, [cv2.IMWRITE_JPEG_QUALITY, 75])
                img_b64 = base64.b64encode(buf.tobytes()).decode('utf-8')
                self._send_json({"image": img_b64})
            except ImportError:
                self._send_json({"error": "opencv-python not installed"}, 500)
            except Exception as e:
                self._send_json({"error": str(e)}, 500)

        elif path == "/api/camera/analyze":
            # Camera analyze is now POST-based (browser sends base64 image)
            self._send_json({"error": "Use POST with {image: base64} body"}, 400)

        elif path.startswith("/api/processes"):
            sort_by = params.get("sort", ["ram"])[0]
            procs = []
            for p in psutil.process_iter(['pid', 'name', 'cpu_percent', 'memory_info', 'io_counters']):
                try:
                    info = p.info
                    io = info.get('io_counters')
                    disk_bytes = ((io.read_bytes + io.write_bytes) if io else 0)
                    procs.append({
                        "pid": info['pid'],
                        "name": info['name'] or "Unknown",
                        "cpu": round(info.get('cpu_percent') or 0, 1),
                        "ram_mb": round((info.get('memory_info').rss if info.get('memory_info') else 0) / (1024*1024), 1),
                        "disk_mb": round(disk_bytes / (1024*1024), 1)
                    })
                except (psutil.NoSuchProcess, psutil.AccessDenied):
                    pass
            sort_keys = {"cpu": "cpu", "ram": "ram_mb", "disk": "disk_mb", "name": "name"}
            key = sort_keys.get(sort_by, "ram_mb")
            reverse = sort_by != "name"
            if sort_by == "name":
                procs.sort(key=lambda x: x['name'].lower())
            else:
                procs.sort(key=lambda x: x[key], reverse=reverse)
            self._send_json({"processes": procs[:50]})

        elif path == "/api/volume":
            try:
                from tools import get_volume
                vol = get_volume()
                self._send_json({"volume": vol})
            except Exception as e:
                self._send_json({"error": str(e)}, 500)

        elif path == "/api/clipboard":
            try:
                import pyperclip
                text = pyperclip.paste()
                self._send_json({"text": text[:2000] if text else ""})
            except Exception as e:
                self._send_json({"error": str(e)}, 500)

        # ─── Mode & Models ───────────────────────────────────────
        elif path == "/api/mode":
            self._send_json({
                "mode": _current_mode,
                "available": ["pc_control", "assistant"],
            })

        elif path == "/api/models":
            self._send_json({
                "active_provider": _brain.provider if _brain else config.MODEL_PROVIDER,
                "active_model": _brain.model_name if _brain else config.NVIDIA_MODEL,
                "available": [
                    {"provider": "nvidia", "model": config.NVIDIA_MODEL, "base_url": config.NVIDIA_BASE_URL},
                    {"provider": "ollama", "model": config.OLLAMA_MODEL, "base_url": config.OLLAMA_BASE_URL},
                ],
            })

        # ─── Assistant Config (for offline mode) ─────────────────
        elif path == "/api/assistant/config":
            self._send_json({
                "api_key": config.NVIDIA_API_KEY,
                "base_url": config.NVIDIA_BASE_URL,
                "model": config.NVIDIA_MODEL,
            })

        # ─── Conversations ───────────────────────────────────────
        elif path == "/api/conversations":
            limit = int(params.get("limit", [30])[0])
            convos = db.list_conversations(limit)
            self._send_json({"conversations": convos})

        elif path.startswith("/api/conversations/"):
            conv_id = path.split("/api/conversations/")[1].strip("/")
            if conv_id:
                messages = db.get_conversation_messages(conv_id)
                self._send_json({"conversation_id": conv_id, "messages": messages})
            else:
                self._send_json({"error": "Missing conversation ID"}, 400)

        # ─── Calendar ────────────────────────────────────────────
        elif path == "/api/calendar":
            date_filter = params.get("date", [None])[0]
            include_done = params.get("include_done", ["false"])[0].lower() == "true"
            events = db.list_calendar_events(date_filter, include_done)
            self._send_json({"events": events})

        # ─── Task Queue ──────────────────────────────────────────
        elif path == "/api/tasks/queue":
            tasks = db.list_tasks(50)
            self._send_json({"tasks": tasks})

        # ─── Command Log (from task_queue DB) ─────────────────────
        elif path == "/api/command/log":
            since_id = int(params.get("since", ["0"])[0])
            tasks = db.list_tasks(50)
            # Convert to command log format, filter by since_id
            entries = []
            for t in reversed(tasks):  # oldest first
                if t["id"] > since_id:
                    entries.append({
                        "id": t["id"],
                        "source": t.get("source", "mobile"),
                        "command": t["command"],
                        "response": t.get("result", "") or "",
                        "timestamp": t.get("created_at", ""),
                        "status": t["status"],
                    })
            self._send_json({"commands": entries})

        else:
            self._send_json({"error": "Not found"}, 404)

    def do_POST(self):
        parsed = urlparse(self.path)
        path = parsed.path
        body = _read_body(self)
        data = _parse_json(body)

        # ─── AI Chat (PC Control) — queues task in DB ─────────────
        if path == "/api/command":
            command = data.get("command", "") or body.strip()
            source = data.get("source", "mobile")
            if not command:
                self._send_json({"error": "No command provided"}, 400)
                return

            # Queue the task in the database — processed by background worker
            task_id = db.add_task(command, source=source)
            log.info(f"Queued command #{task_id} from {source}: {command[:80]}")
            self._send_json({
                "response": f"Task #{task_id} queued. Processing...",
                "task_id": task_id,
                "status": "pending",
            })

        # ─── Camera Frame Buffer (dashboard saves frames here) ───
        elif path == "/api/camera/frame":
            frame = data.get("frame", "")
            if frame:
                set_camera_frame(frame)
                self._send_json({"ok": True})
            else:
                self._send_json({"error": "No frame data"}, 400)

        # ─── Camera Analyze (POST with base64 image) ─────────────
        elif path == "/api/camera/analyze":
            img_b64 = data.get("image", "")
            question = data.get("question", "Describe what you see in this image in detail. Be specific about people, objects, and the setting.")
            if not img_b64:
                self._send_json({"error": "No image provided"}, 400)
                return
            try:
                from openai import OpenAI
                import config
                vision_client = OpenAI(
                    api_key=config.NVIDIA_VISION_API_KEY,
                    base_url=config.NVIDIA_BASE_URL,
                )
                response = vision_client.chat.completions.create(
                    model=config.NVIDIA_VISION_MODEL,
                    messages=[
                        {"role": "user", "content": [
                            {"type": "text", "text": question},
                            {"type": "image_url", "image_url": {
                                "url": f"data:image/jpeg;base64,{img_b64}"
                            }}
                        ]}
                    ],
                    max_tokens=1024,
                    temperature=0.3,
                )
                description = response.choices[0].message.content or "I could not describe what I see."
                self._send_json({"description": description})
            except Exception as e:
                log.error(f"Camera analysis error: {e}")
                self._send_json({"error": str(e)}, 500)

        # ─── AI Chat (Assistant Mode) ────────────────────────────
        elif path == "/api/assistant/chat":
            message = data.get("message", "") or data.get("command", "")
            if not message:
                self._send_json({"error": "No message provided"}, 400)
                return
            if _assistant:
                try:
                    response = _assistant.chat(message)
                    self._send_json({"response": response})
                except Exception as e:
                    self._send_json({"error": str(e)}, 500)
            else:
                self._send_json({"error": "Assistant not initialized"}, 503)

        # ─── Mode Switching ──────────────────────────────────────
        elif path == "/api/mode":
            global _current_mode
            mode = data.get("mode", "")
            if mode in ("pc_control", "assistant"):
                _current_mode = mode
                self._send_json({"mode": _current_mode, "message": f"Switched to {mode} mode"})
            else:
                self._send_json({"error": f"Unknown mode: {mode}. Use 'pc_control' or 'assistant'."}, 400)

        # ─── Model Switching ─────────────────────────────────────
        elif path == "/api/models":
            provider = data.get("provider", "")
            if provider in ("nvidia", "ollama"):
                if _brain:
                    _brain.switch_model(provider)
                self._send_json({
                    "message": f"Switched to {provider}",
                    "model": _brain.model_name if _brain else "",
                    "provider": provider,
                })
            else:
                self._send_json({"error": f"Unknown provider: {provider}"}, 400)

        # ─── Power ───────────────────────────────────────────────
        elif path == "/api/power":
            action = data.get("action", "") or body.strip()
            from tools import lock_pc, shutdown_pc
            if action == "lock":
                result = lock_pc()
            elif action == "shutdown":
                result = shutdown_pc(restart=False)
            elif action == "restart":
                result = shutdown_pc(restart=True)
            elif action == "sleep":
                import subprocess
                subprocess.run("rundll32.exe powrprof.dll,SetSuspendState 0,1,0", shell=True)
                result = "PC entering sleep mode."
            else:
                self._send_json({"error": f"Unknown action: {action}"}, 400)
                return
            self._send_json({"result": result})

        # ─── File Upload ─────────────────────────────────────────
        elif path == "/api/upload":
            filename = data.get("filename", f"upload_{int(__import__('time').time())}")
            file_data = data.get("data", "")
            file_type = data.get("type", "image")
            if not file_data:
                self._send_json({"error": "No file data provided"}, 400)
                return
            try:
                desktop = os.path.join(os.path.expanduser("~"), "Desktop", "Echo_Uploads")
                os.makedirs(desktop, exist_ok=True)
                safe_name = "".join(c for c in filename if c.isalnum() or c in "._- ")
                if not safe_name:
                    safe_name = f"upload_{int(__import__('time').time())}"
                if file_type == "image" and not safe_name.lower().endswith((".png", ".jpg", ".jpeg", ".gif", ".webp")):
                    safe_name += ".png"
                filepath = os.path.join(desktop, safe_name)
                file_bytes = base64.b64decode(file_data)
                with open(filepath, "wb") as f:
                    f.write(file_bytes)
                log.info(f"File saved: {filepath} ({len(file_bytes)} bytes)")
                self._send_json({"result": f"File saved to: {filepath}"})
            except Exception as e:
                self._send_json({"error": f"Upload failed: {str(e)}"}, 500)

        # ─── Process Kill ────────────────────────────────────────
        elif path == "/api/kill":
            try:
                pid = int(data.get("pid", 0))
                p = psutil.Process(pid)
                name = p.name()
                p.terminate()
                self._send_json({"result": f"Killed {name} (PID {pid})"})
            except psutil.NoSuchProcess:
                self._send_json({"error": "Process not found"}, 404)
            except Exception as e:
                self._send_json({"error": str(e)}, 500)

        # ─── Volume ──────────────────────────────────────────────
        elif path == "/api/volume":
            try:
                level = int(data.get("level", 50))
                from tools import set_volume
                result = set_volume(level)
                self._send_json({"result": result, "volume": level})
            except Exception as e:
                self._send_json({"error": str(e)}, 500)

        # ─── App Launch ──────────────────────────────────────────
        elif path == "/api/launch":
            try:
                app_name = data.get("name", "")
                from tools import open_application
                result = open_application(app_name)
                self._send_json({"result": result})
            except Exception as e:
                self._send_json({"error": str(e)}, 500)

        # ─── Clipboard ───────────────────────────────────────────
        elif path == "/api/clipboard":
            try:
                text = data.get("text", "")
                import pyperclip
                pyperclip.copy(text)
                self._send_json({"result": "Clipboard updated"})
            except Exception as e:
                self._send_json({"error": str(e)}, 500)

        # ─── Notifications ───────────────────────────────────────
        elif path == "/api/notify":
            try:
                title = data.get("title", "Echo")
                message = data.get("message", "")
                from tools import show_notification
                result = show_notification(title, message)
                self._send_json({"result": result})
            except Exception as e:
                self._send_json({"error": str(e)}, 500)

        # ─── Media Control ───────────────────────────────────────
        elif path == "/api/media":
            try:
                action = data.get("action", "")
                import pyautogui
                key_map = {
                    "play_pause": "playpause",
                    "next": "nexttrack",
                    "prev": "prevtrack",
                    "mute": "volumemute",
                    "vol_up": "volumeup",
                    "vol_down": "volumedown",
                }
                key = key_map.get(action)
                if key:
                    pyautogui.press(key)
                    self._send_json({"result": f"Media: {action}"})
                else:
                    self._send_json({"error": f"Unknown media action: {action}"}, 400)
            except Exception as e:
                self._send_json({"error": str(e)}, 500)

        # ─── Calendar ────────────────────────────────────────────
        elif path == "/api/calendar":
            title = data.get("title", "")
            event_date = data.get("date", "")
            if not title or not event_date:
                self._send_json({"error": "title and date are required"}, 400)
                return
            try:
                event_id = db.add_calendar_event(
                    title=title,
                    event_date=event_date,
                    event_time=data.get("time", ""),
                    description=data.get("description", ""),
                    remind_at=data.get("remind_at"),
                )
                self._send_json({"event_id": event_id, "message": f"Event '{title}' created"})
            except Exception as e:
                self._send_json({"error": str(e)}, 500)

        elif path.startswith("/api/calendar/") and path.endswith("/done"):
            try:
                event_id = int(path.split("/api/calendar/")[1].replace("/done", ""))
                if db.complete_calendar_event(event_id):
                    self._send_json({"message": f"Event {event_id} marked as done"})
                else:
                    self._send_json({"error": "Event not found"}, 404)
            except Exception as e:
                self._send_json({"error": str(e)}, 500)

        # ─── Task Queue ──────────────────────────────────────────
        elif path == "/api/tasks/queue":
            command = data.get("command", "")
            if not command:
                self._send_json({"error": "command is required"}, 400)
                return
            try:
                task_id = db.add_task(command, source=data.get("source", "api"))
                self._send_json({"task_id": task_id, "message": f"Task queued: {command}"})
            except Exception as e:
                self._send_json({"error": str(e)}, 500)

        else:
            self._send_json({"error": "Not found"}, 404)

    def do_DELETE(self):
        parsed = urlparse(self.path)
        path = parsed.path

        # ─── Delete Calendar Event ───────────────────────────────
        if path.startswith("/api/calendar/"):
            try:
                event_id = int(path.split("/api/calendar/")[1].strip("/"))
                if db.delete_calendar_event(event_id):
                    self._send_json({"message": f"Event {event_id} deleted"})
                else:
                    self._send_json({"error": "Event not found"}, 404)
            except Exception as e:
                self._send_json({"error": str(e)}, 500)

        # ─── Delete Conversation ─────────────────────────────────
        elif path.startswith("/api/conversations/"):
            try:
                conv_id = path.split("/api/conversations/")[1].strip("/")
                db.delete_conversation(conv_id)
                self._send_json({"message": f"Conversation {conv_id} deleted"})
            except Exception as e:
                self._send_json({"error": str(e)}, 500)

        else:
            self._send_json({"error": "Not found"}, 404)


def start_api_server(port: int = 5000):
    """Start the API server in a background thread."""
    try:
        server = ThreadingHTTPServer(("0.0.0.0", port), EchoAPIHandler)
        server.daemon_threads = True
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        log.info(f"API server running on http://0.0.0.0:{port}")
        return server
    except Exception as e:
        log.error(f"Failed to start API server: {e}")
        return None
