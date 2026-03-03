"""
Echo AI Agent — Configuration
"""

import os

# ──────────────────────────────────────────────
# Model Provider  ("nvidia" or "ollama")
# ──────────────────────────────────────────────
MODEL_PROVIDER = os.environ.get("MODEL_PROVIDER", "nvidia")

# ──────────────────────────────────────────────
# NVIDIA NIM API
# ──────────────────────────────────────────────
NVIDIA_API_KEY = os.environ.get(
    "NVIDIA_API_KEY",
    "",
)
NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"
NVIDIA_MODEL = "meta/llama-3.3-70b-instruct"
NVIDIA_VISION_MODEL = "meta/llama-3.2-90b-vision-instruct"
NVIDIA_VISION_API_KEY = os.environ.get(
    "NVIDIA_VISION_API_KEY",
    "",
)

# ──────────────────────────────────────────────
# Ollama (Local LLM)
# ──────────────────────────────────────────────
OLLAMA_BASE_URL = os.environ.get("OLLAMA_BASE_URL", "http://localhost:11434/v1")
OLLAMA_MODEL = os.environ.get("OLLAMA_MODEL", "llama3.1")

# ──────────────────────────────────────────────
# Telegram Bot
# ──────────────────────────────────────────────
TELEGRAM_BOT_TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "")
# Comma-separated list of allowed Telegram user IDs (empty = allow all)
TELEGRAM_ALLOWED_USERS = os.environ.get("TELEGRAM_ALLOWED_USERS", "")

# ──────────────────────────────────────────────
# Voice Settings
# ──────────────────────────────────────────────
WAKE_WORD = "echo"
TTS_RATE = 180          # Words per minute
TTS_VOLUME = 1.0        # 0.0 to 1.0
VOICE_HOTKEY = "ctrl+shift+e"  # Push-to-talk hotkey
VOICE_CONVERSATION_TIMEOUT = 8  # Seconds of silence before exiting conversation mode

# ──────────────────────────────────────────────
# General
# ──────────────────────────────────────────────
AGENT_NAME = "Echo"
SCREENSHOT_DIR = os.path.join(os.environ.get("TEMP", "."), "echo_screenshots")
os.makedirs(SCREENSHOT_DIR, exist_ok=True)

# ──────────────────────────────────────────────
# Database & Data
# ──────────────────────────────────────────────
DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")
os.makedirs(DATA_DIR, exist_ok=True)
DB_PATH = os.path.join(DATA_DIR, "echo_data.db")

# ──────────────────────────────────────────────
# Cloudflare Tunnel (Remote Access)
# ──────────────────────────────────────────────
# No account or auth needed! Just set TUNNEL_ENABLED=true
# This creates a free *.trycloudflare.com URL so you can control PC from anywhere
TUNNEL_ENABLED = os.environ.get("TUNNEL_ENABLED", "true").lower() == "true"
API_PORT = 5000
