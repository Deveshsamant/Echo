"""
Echo AI Agent — Main Entry Point
Orchestrates Brain, Assistant, Voice, Telegram, Scheduler, Task Queue, and System Tray.
Run:  python main.py
"""

import logging
import os
import signal
import sys
import threading
import time

# ─── Logging Setup ───────────────────────────────────────────────────

LOG_FORMAT = "%(asctime)s │ %(name)-14s │ %(levelname)-7s │ %(message)s"
LOG_DATE = "%H:%M:%S"

logging.basicConfig(
    level=logging.INFO,
    format=LOG_FORMAT,
    datefmt=LOG_DATE,
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler(os.path.join(os.path.dirname(__file__), "echo.log"), encoding="utf-8"),
    ],
)
log = logging.getLogger("echo.main")

logging.getLogger("pywebview").setLevel(logging.CRITICAL)
logging.getLogger("comtypes").setLevel(logging.WARNING)
logging.getLogger("httpx").setLevel(logging.WARNING)

# Increase recursion limit to prevent pywebview accessibility crashes
sys.setrecursionlimit(500)

# ─── Imports ─────────────────────────────────────────────────────────

import config
import database as db
from brain import EchoBrain
from assistant_brain import AssistantBrain
from voice import VoiceEngine
from telegram_bot import TelegramBot
from api_server import start_api_server, set_brain, set_assistant
from scheduler import start_scheduler, set_voice_engine, set_brain as set_scheduler_brain

# ─── Globals ─────────────────────────────────────────────────────────

brain: EchoBrain = None
assistant: AssistantBrain = None
voice: VoiceEngine = None
telegram: TelegramBot = None
api_server = None
tray_icon = None
shutdown_event = threading.Event()


# ─── Command Handler ────────────────────────────────────────────────

def handle_command(text: str, source: str = "voice") -> str:
    """Central command handler used by both Voice and Telegram."""
    global brain
    from api_server import _command_lock, add_command_log

    if text.strip().lower() == "/reset":
        brain.reset()
        return "Conversation reset. How can I help?"

    # Log the incoming command
    log_entry = add_command_log(source, text, status="running")

    try:
        with _command_lock:
            response = brain.chat(text)
        log_entry["status"] = "done"
        log_entry["response"] = response
        return response
    except Exception as e:
        log.error(f"Brain error: {e}")
        log_entry["status"] = "error"
        log_entry["response"] = str(e)
        return f"Sorry, I ran into an error: {e}"


# ─── Task Queue Processor ───────────────────────────────────────────

def _task_queue_processor():
    """Background thread that processes pending PC tasks from the queue."""
    log.info("Task queue processor started (checking every 2s)")
    while True:
        try:
            pending = db.get_pending_tasks()
            if not pending:
                time.sleep(2)  # Only sleep when idle
                continue
            for task in pending:
                task_id = task["id"]
                command = task["command"]
                log.info(f"Processing queued task #{task_id}: {command}")
                db.update_task_status(task_id, "running")
                try:
                    is_assistant = command.strip().upper().startswith("[ASSISTANT]")
                    
                    if is_assistant:
                        actual_command = command.split("]", 1)[-1].strip()
                        if assistant:
                            result = assistant.chat(actual_command)
                            db.update_task_status(task_id, "done", result)
                            log.info(f"Task #{task_id} completed via Assistant: {result[:100]}")
                        else:
                            db.update_task_status(task_id, "failed", "Assistant not initialized")
                    else:
                        if brain:
                            result = brain.chat(command)
                            db.update_task_status(task_id, "done", result)
                            log.info(f"Task #{task_id} completed via Brain: {result[:100]}")
                        else:
                            db.update_task_status(task_id, "failed", "Brain not available")

                except Exception as e:
                    db.update_task_status(task_id, "failed", str(e))
                    log.error(f"Task #{task_id} failed: {e}")
            # No sleep between tasks — process next immediately
        except Exception as e:
            log.error(f"Task queue error: {e}")
            time.sleep(2)


# ─── System Tray ─────────────────────────────────────────────────────

def create_tray_icon():
    """Create and display the system tray icon."""
    global tray_icon

    try:
        import pystray
        from PIL import Image, ImageDraw, ImageFont
    except ImportError:
        log.warning("pystray or PIL not available. System tray disabled.")
        return

    # Generate tray icon programmatically
    size = 64
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Dark circle background
    draw.ellipse([2, 2, size - 2, size - 2], fill=(30, 30, 40, 255))

    # Cyan "E" letter
    try:
        font = ImageFont.truetype("arial.ttf", 36)
    except Exception:
        font = ImageFont.load_default()
    draw.text((size // 2, size // 2), "E", fill=(0, 220, 255, 255), font=font, anchor="mm")

    # Glow ring
    draw.ellipse([4, 4, size - 4, size - 4], outline=(0, 180, 255, 120), width=2)

    def on_mute_toggle(icon, item):
        if voice:
            if voice.is_muted:
                voice.unmute()
                log.info("Voice unmuted")
            else:
                voice.mute()
                log.info("Voice muted")

    def on_reset(icon, item):
        brain.reset()
        log.info("Conversation reset via tray")

    def on_quit(icon, item):
        log.info("Quit requested from tray")
        shutdown_event.set()
        icon.stop()

    def mute_text(item):
        if voice and voice.is_muted:
            return "🔇 Unmute Voice"
        return "🔊 Mute Voice"

    menu = pystray.Menu(
        pystray.MenuItem(f"🤖 {config.AGENT_NAME} — Online", lambda: None, enabled=False),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem(mute_text, on_mute_toggle),
        pystray.MenuItem("🔄 Reset Conversation", on_reset),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("❌ Quit", on_quit),
    )

    tray_icon = pystray.Icon(config.AGENT_NAME, img, f"{config.AGENT_NAME} AI", menu)

    # Run tray in its own thread
    tray_thread = threading.Thread(target=tray_icon.run, daemon=True)
    tray_thread.start()
    log.info("System tray icon created.")


# ─── Banner ──────────────────────────────────────────────────────────

BANNER = r"""
╔══════════════════════════════════════════════════════════╗
║                                                          ║
║     ███████╗ ██████╗██╗  ██╗ ██████╗                     ║
║     ██╔════╝██╔════╝██║  ██║██╔═══██╗                    ║
║     █████╗  ██║     ███████║██║   ██║                    ║
║     ██╔══╝  ██║     ██╔══██║██║   ██║                    ║
║     ███████╗╚██████╗██║  ██║╚██████╔╝                    ║
║     ╚══════╝ ╚═════╝╚═╝  ╚═╝ ╚═════╝                    ║
║                                                          ║
║            🤖 AI Agent — Full PC Control                 ║
║       Voice  •  Telegram  •  Personal AI                 ║
║                                                          ║
╚══════════════════════════════════════════════════════════╝
"""


# ─── Main ────────────────────────────────────────────────────────────

tunnel_url = None  # Global for Cloudflare tunnel public URL
_tunnel_process = None  # Cloudflared subprocess

def start_cloudflare_tunnel(port: int) -> str:
    """Start a Cloudflare quick tunnel and return the public URL. No account needed."""
    global tunnel_url, _tunnel_process
    import subprocess
    import re

    cloudflared_path = os.path.join(os.path.dirname(__file__), "cloudflared.exe")
    if not os.path.exists(cloudflared_path):
        log.warning("  cloudflared.exe not found in Echo directory.")
        return None

    try:
        _tunnel_process = subprocess.Popen(
            [cloudflared_path, "tunnel", "--url", f"http://localhost:{port}"],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
        )

        import select
        start_time = time.time()
        # Real tunnel URLs have long multi-word subdomains like "flowers-repeat-had-intelligence"
        # Skip generic ones like "api.trycloudflare.com"
        url_pattern = re.compile(r"https://([a-zA-Z0-9\-]+)\.trycloudflare\.com")

        while time.time() - start_time < 30:
            line = _tunnel_process.stdout.readline()
            if not line:
                if _tunnel_process.poll() is not None:
                    log.warning("  Cloudflared process exited unexpectedly.")
                    return None
                continue

            line = line.strip()
            if line:
                log.debug(f"  cloudflared: {line}")

            match = url_pattern.search(line)
            if match:
                subdomain = match.group(1)
                # Skip generic Cloudflare API URLs — real tunnel subdomains are long
                if subdomain in ("api", "www", "dash", "developers") or len(subdomain) < 8:
                    log.debug(f"  Skipping non-tunnel URL: {match.group(0)}")
                    continue
                tunnel_url = match.group(0)
                log.info(f"  Cloudflare tunnel: {tunnel_url}")

                def _drain():
                    try:
                        for ln in _tunnel_process.stdout:
                            pass
                    except Exception:
                        pass
                drain_thread = threading.Thread(target=_drain, daemon=True)
                drain_thread.start()

                return tunnel_url

        log.warning("  Cloudflare tunnel: Timed out waiting for URL.")
        return None
    except Exception as e:
        log.warning(f"  Cloudflare tunnel failed: {e}")
        return None


def main():
    global brain, assistant, voice, telegram, tunnel_url

    print(BANNER)
    log.info(f"Starting {config.AGENT_NAME}...")

    # 0. Initialize Database
    log.info("Initializing database...")
    db.init_db()
    db.clear_stale_tasks()  # Cancel leftover pending tasks from previous session

    # 1. Initialize Brain (PC Control)
    log.info(f"Initializing AI Brain ({config.MODEL_PROVIDER})...")
    brain = EchoBrain()
    set_brain(brain)
    log.info(f"  Model: {brain.model_name}")
    log.info(f"  Provider: {brain.provider}")

    # 1b. Initialize Personal AI Assistant
    log.info("Initializing Personal AI Assistant...")
    assistant = AssistantBrain()
    set_assistant(assistant)
    log.info(f"  Assistant ready (model: {assistant.model_name})")

    # 2. Start API Server (for mobile app)
    log.info("Starting API Server...")
    api_server = start_api_server(port=config.API_PORT)
    if api_server:
        log.info(f"  API server: http://0.0.0.0:{config.API_PORT}")
    else:
        log.warning("  API server: FAILED")

    # 2b. Start Cloudflare tunnel for remote access
    if config.TUNNEL_ENABLED:
        log.info("Starting Cloudflare tunnel (remote access)...")
        public_url = start_cloudflare_tunnel(config.API_PORT)
        if public_url:
            import api_server as api_mod
            api_mod._ngrok_url = public_url

            if config.TELEGRAM_BOT_TOKEN:
                try:
                    import requests
                    desc = f"🤖 Echo AI Agent — JARVIS-like PC Controller\n🔗 {public_url}"
                    resp = requests.post(
                        f"https://api.telegram.org/bot{config.TELEGRAM_BOT_TOKEN}/setMyDescription",
                        json={"description": desc},
                        timeout=10,
                    )
                    if resp.ok:
                        log.info("  Published tunnel URL to Telegram bot description ✓")
                    else:
                        log.warning(f"  Could not publish URL to bot description: {resp.text}")

                    # Also send the tunnel URL directly to allowed users
                    if config.TELEGRAM_ALLOWED_USERS:
                        for uid in config.TELEGRAM_ALLOWED_USERS.split(","):
                            uid = uid.strip()
                            if uid.isdigit():
                                try:
                                    requests.post(
                                        f"https://api.telegram.org/bot{config.TELEGRAM_BOT_TOKEN}/sendMessage",
                                        json={"chat_id": int(uid), "text": f"🔗 Echo tunnel URL updated:\n{public_url}\n\nPaste this in your Echo mobile app to connect."},
                                        timeout=10,
                                    )
                                    log.info(f"  Sent tunnel URL to Telegram user {uid} ✓")
                                except Exception:
                                    pass
                except Exception as e:
                    log.warning(f"  Could not publish URL to bot description: {e}")
    else:
        log.info("  Tunnel: DISABLED (set TUNNEL_ENABLED=true to enable)")

    # 3. Initialize Voice
    log.info("Initializing Voice Engine...")
    voice = VoiceEngine(on_command=handle_command)
    voice.start_listening()
    log.info(f"  Wake word: \"{config.WAKE_WORD}\"")

    # 3b. Set voice engine for scheduler (spoken reminders)
    set_voice_engine(voice)
    # 3c. Set brain for scheduler (auto-execute calendar tasks)
    set_scheduler_brain(brain)

    # 4. Initialize Telegram Bot
    log.info("Initializing Telegram Bot...")
    telegram = TelegramBot(on_command=handle_command)
    telegram.start()
    if config.TELEGRAM_BOT_TOKEN:
        log.info("  Telegram bot: ENABLED")
    else:
        log.info("  Telegram bot: DISABLED (no token configured)")

    # 5. System Tray
    log.info("Creating system tray icon...")
    create_tray_icon()

    # 6. Start Reminder Scheduler
    log.info("Starting reminder scheduler...")
    start_scheduler()

    # 7. Start Task Queue Processor
    log.info("Starting task queue processor...")
    tq_thread = threading.Thread(target=_task_queue_processor, daemon=True)
    tq_thread.start()

    # 8. Startup summary
    log.info("=" * 50)
    log.info(f"{config.AGENT_NAME} is ONLINE and ready!")
    log.info(f"  Say \"{config.WAKE_WORD}\" to activate voice commands")
    log.info(f"  Send messages via Telegram bot")
    log.info(f"  Local API: http://0.0.0.0:{config.API_PORT}")
    if tunnel_url:
        log.info(f"  🌐 REMOTE URL: {tunnel_url}")
        log.info(f"  (Use this URL in the app to connect from ANYWHERE)")
    log.info(f"  📅 Calendar & reminders active")
    log.info(f"  🤖 Personal AI assistant mode available")
    log.info(f"  Right-click tray icon for options")
    log.info("=" * 50)

    voice.speak(f"{config.AGENT_NAME} is online. How can I help you?")

    # 9. Keep alive
    try:
        while not shutdown_event.is_set():
            shutdown_event.wait(timeout=1)
    except KeyboardInterrupt:
        log.info("Keyboard interrupt received.")

    # Shutdown
    log.info("Shutting down...")
    if voice:
        voice.shutdown()
    if telegram:
        telegram.stop()
    if tray_icon:
        try:
            tray_icon.stop()
        except Exception:
            pass
    # Kill cloudflared tunnel
    if _tunnel_process:
        try:
            _tunnel_process.terminate()
            _tunnel_process.wait(timeout=5)
        except Exception:
            try:
                _tunnel_process.kill()
            except Exception:
                pass
    log.info(f"{config.AGENT_NAME} offline. Goodbye!")


if __name__ == "__main__":
    main()
