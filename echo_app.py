"""
Echo AI Agent — Native Desktop App
Launches the Echo backend + a native desktop window with the 3D dashboard.
Run:  pythonw echo_app.py   (or python echo_app.py)
"""

import multiprocessing
import os
import sys
import time
import threading

# ═══ Hide the console window on Windows ═══
if sys.platform == "win32":
    import ctypes
    # 0 = SW_HIDE — completely hides the console window
    ctypes.windll.user32.ShowWindow(ctypes.windll.kernel32.GetConsoleWindow(), 0)

ECHO_DIR = os.path.dirname(os.path.abspath(__file__))

# ═══ Enable GPU hardware acceleration for the dashboard (Safe flags) ═══
os.environ["WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS"] = (
    "--enable-gpu --enable-gpu-rasterization"
)


def _start_backend():
    """Start the Echo backend in a subprocess."""
    os.chdir(ECHO_DIR)
    sys.path.insert(0, ECHO_DIR)
    import main
    main.main()


def _wait_for_api(port=5000, timeout=30):
    """Block until the API server responds."""
    import urllib.request
    start = time.time()
    while time.time() - start < timeout:
        try:
            urllib.request.urlopen(f"http://localhost:{port}/api/stats", timeout=2)
            return True
        except Exception:
            time.sleep(0.5)
    return False


def run_app():
    """Main entry point — start backend, then open native window."""
    import webview

    # Start backend in a background thread
    backend_thread = threading.Thread(target=_start_backend, daemon=True)
    backend_thread.start()

    # Wait for API to be ready
    print("⏳ Starting Echo backend...")
    if not _wait_for_api():
        print("❌ Backend failed to start within 30 seconds.")
        return

    print("✅ Backend ready — launching Echo Command Center...")

    class WinAPI:
        def __init__(self):
            self.window = None
            self.is_maximized = False
        def minimize(self):
            if self.window: self.window.minimize()
        def toggle_maximize(self):
            if not self.window: return
            if self.is_maximized:
                self.window.restore()
                self.is_maximized = False
            else:
                self.window.maximize()
                self.is_maximized = True
        def close(self):
            if self.window: self.window.destroy()

    api = WinAPI()

    # Create native frameless window (custom title bar in HTML)
    window = webview.create_window(
        title="Echo — AI Command Center",
        url="http://localhost:5000",
        js_api=api,
        width=1400,
        height=860,
        min_size=(900, 500),
        background_color="#080c14",
        text_select=True,
        frameless=True,
        easy_drag=False,
    )
    api.window = window

    # Start the GUI event loop (blocks until window is closed)
    webview.start(
        gui="edgechromium",   # Use Edge WebView2 on Windows for best rendering
        debug=False,
    )

    print("👋 Echo window closed. Shutting down...")
    os._exit(0)


if __name__ == "__main__":
    # On Windows, multiprocessing needs this guard
    multiprocessing.freeze_support()
    run_app()
