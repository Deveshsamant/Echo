"""
Echo AI Agent — Tool Definitions
All tools that the AI can call to control the PC.
Exports: TOOLS (dict of name->func), TOOL_SCHEMAS (list of OpenAI function schemas)
"""

import json
import logging
import os
import subprocess
import time

log = logging.getLogger("echo.tools")

# Disable pyautogui fail-safe (mouse corner = crash)
try:
    import pyautogui
    pyautogui.FAILSAFE = False
    pyautogui.PAUSE = 0.1
except ImportError:
    pass

# Configure Tesseract OCR path
try:
    import pytesseract
    pytesseract.pytesseract.tesseract_cmd = r"C:\Program Files\Tesseract-OCR\tesseract.exe"
except ImportError:
    pass

# ─── Mouse / Keyboard / Screen ─────────────────────────────────────

def move_mouse(x: int, y: int) -> str:
    """Move the mouse to (x, y)."""
    import pyautogui
    x, y = int(x), int(y)
    pyautogui.moveTo(x, y, duration=0.2)
    return f"Moved mouse to ({x}, {y})."


def click(x: int, y: int) -> str:
    """Left-click at (x, y)."""
    import pyautogui
    x, y = int(x), int(y)
    pyautogui.click(x, y)
    return f"Left-clicked at ({x}, {y})."


def double_click(x: int, y: int) -> str:
    """Double-click at (x, y)."""
    import pyautogui
    pyautogui.doubleClick(x, y)
    return f"Double-clicked at ({x}, {y})."


def right_click(x: int, y: int) -> str:
    """Right-click at (x, y)."""
    import pyautogui
    pyautogui.rightClick(x, y)
    return f"Right-clicked at ({x}, {y})."


def scroll(clicks: int) -> str:
    """Scroll the mouse wheel. Positive=up, negative=down."""
    import pyautogui
    clicks = int(clicks)
    pyautogui.scroll(clicks)
    direction = "up" if clicks > 0 else "down"
    return f"Scrolled {direction} by {abs(clicks)} clicks."


def type_text(text: str) -> str:
    """Type text using the keyboard. Uses clipboard paste for reliability."""
    import pyautogui
    import pyperclip
    # Always use clipboard paste — it's faster, handles all characters, and doesn't break on long text
    pyperclip.copy(text)
    time.sleep(0.1)
    pyautogui.hotkey('ctrl', 'v')
    time.sleep(0.1)
    return f"Successfully typed all {len(text)} characters."


def press_key(keys: str) -> str:
    """Press a key or key combo (e.g. 'enter', 'ctrl+c', 'alt+tab')."""
    import pyautogui
    parts = keys.lower().split('+')
    if len(parts) > 1:
        pyautogui.hotkey(*parts)
    else:
        pyautogui.press(parts[0])
    return f"Pressed: {keys}"


def wait_seconds(seconds: int) -> str:
    """Wait for N seconds (for pages/apps to load)."""
    seconds = int(seconds)
    time.sleep(seconds)
    return f"Waited {seconds} seconds."


# ─── Screen Reading / OCR ───────────────────────────────────────────

def screen_read() -> str:
    """Read all text visible on screen with (x,y) coordinates using OCR."""
    try:
        import pyautogui
        from PIL import Image
        import pytesseract

        screenshot = pyautogui.screenshot()
        w, h = screenshot.size
        data = pytesseract.image_to_data(screenshot, output_type=pytesseract.Output.DICT)

        lines = []
        current_line = {"text": "", "x": 0, "y": 0, "line_num": -1, "block_num": -1}

        for i in range(len(data['text'])):
            txt = data['text'][i].strip()
            if not txt:
                continue
            conf = int(data['conf'][i])
            if conf < 30:
                continue
            block = data['block_num'][i]
            line = data['line_num'][i]
            x = data['left'][i]
            y = data['top'][i]

            if block != current_line["block_num"] or line != current_line["line_num"]:
                if current_line["text"]:
                    lines.append(current_line.copy())
                current_line = {"text": txt, "x": x, "y": y, "line_num": line, "block_num": block}
            else:
                current_line["text"] += " " + txt

        if current_line["text"]:
            lines.append(current_line)

        if not lines:
            return f"Screen size: {w}x{h}\nNo readable text found on screen."

        result = f"Screen size: {w}x{h}\nFound {len(lines)} text lines:\n"
        for ln in lines[:60]:
            result += f'  [{ln["x"]},{ln["y"]}] "{ln["text"][:60]}"\n'
        return result
    except Exception as e:
        # Fallback: get active window info via Windows API
        try:
            import pyautogui
            import subprocess
            w, h = pyautogui.size()
            # Get list of visible window titles
            result = subprocess.run(
                'powershell -Command "Get-Process | Where-Object {$_.MainWindowTitle} | Select-Object -ExpandProperty MainWindowTitle"',
                shell=True, capture_output=True, text=True, timeout=5
            )
            titles = [t.strip() for t in result.stdout.strip().split('\n') if t.strip()]
            info = f"Screen size: {w}x{h}\n"
            info += f"OCR failed ({type(e).__name__}), showing open windows instead:\n"
            for t in titles[:15]:
                info += f"  🪟 {t}\n"
            info += "\nTip: Use vision_screen_read() for AI-powered screen understanding, or use find_and_click('text') to interact."
            return info
        except Exception:
            return f"Error reading screen: {e}. Try vision_screen_read() instead — it uses AI vision and doesn't need OCR."


def find_and_click(text: str) -> str:
    """Find text on screen using OCR and click on it."""
    try:
        import pyautogui
        from PIL import Image
        import pytesseract

        screenshot = pyautogui.screenshot()
        try:
            data = pytesseract.image_to_data(screenshot, output_type=pytesseract.Output.DICT)
        except Exception as ocr_err:
            return f"OCR failed: {ocr_err}. Try using screen_read() to get coordinates, then click(x, y) directly."

        search = text.lower()
        search_words = set(search.split())
        best_match = None
        best_score = 0

        # Build lines from OCR data
        lines = []
        current = {"text": "", "x": 0, "y": 0, "w": 0, "h": 0, "line": -1, "block": -1}
        for i in range(len(data['text'])):
            txt = data['text'][i].strip()
            if not txt or int(data['conf'][i]) < 30:
                continue
            b, l = data['block_num'][i], data['line_num'][i]
            x, y, w, h = data['left'][i], data['top'][i], data['width'][i], data['height'][i]
            if b != current["block"] or l != current["line"]:
                if current["text"]:
                    lines.append(current.copy())
                current = {"text": txt, "x": x, "y": y, "w": w, "h": h, "line": l, "block": b}
            else:
                current["text"] += " " + txt
                current["w"] = (x + w) - current["x"]
        if current["text"]:
            lines.append(current)

        for ln in lines:
            lt = ln["text"].lower()
            # Exact substring match (best)
            if search in lt:
                score = len(search) / max(len(lt), 1) + 1.0  # boost exact matches
                if score > best_score:
                    best_score = score
                    best_match = ln
            else:
                # Fuzzy: count how many search words appear in this line
                line_words = set(lt.split())
                overlap = search_words & line_words
                if len(overlap) >= max(1, len(search_words) - 1):  # allow 1 missing word
                    score = len(overlap) / max(len(search_words), 1) * 0.8
                    if score > best_score:
                        best_score = score
                        best_match = ln

        if best_match:
            cx = best_match["x"] + best_match["w"] // 2
            cy = best_match["y"] + best_match["h"] // 2
            pyautogui.click(cx, cy)
            return f'Found "{text}" and clicked at ({cx}, {cy}).'
        return f'Could not find "{text}" on screen. Try using screen_read() first to see what\'s visible, then click using coordinates.'
    except Exception as e:
        return f"Error: {e}"


# ─── Screenshot ─────────────────────────────────────────────────────

def take_screenshot(filename: str = "") -> str:
    """Take a screenshot and save it."""
    try:
        import pyautogui
        import config
        os.makedirs(config.SCREENSHOT_DIR, exist_ok=True)
        if not filename:
            filename = f"screenshot_{int(time.time())}.png"
        path = os.path.join(config.SCREENSHOT_DIR, filename)
        pyautogui.screenshot(path)
        return f"Screenshot saved to: {path}"
    except Exception as e:
        return f"Error taking screenshot: {e}"


# ─── Application Management ────────────────────────────────────────

def _bring_window_to_front(title_keyword: str, max_wait: float = 3.0):
    """Find a window by title keyword and bring it to the foreground using Windows API."""
    try:
        import ctypes
        import ctypes.wintypes

        user32 = ctypes.windll.user32
        EnumWindows = user32.EnumWindows
        GetWindowTextW = user32.GetWindowTextW
        GetWindowTextLengthW = user32.GetWindowTextLengthW
        IsWindowVisible = user32.IsWindowVisible
        SetForegroundWindow = user32.SetForegroundWindow
        ShowWindow = user32.ShowWindow
        BringWindowToTop = user32.BringWindowToTop

        WNDENUMPROC = ctypes.WINFUNCTYPE(ctypes.wintypes.BOOL, ctypes.wintypes.HWND, ctypes.wintypes.LPARAM)
        target_hwnd = None
        keyword = title_keyword.lower()

        def enum_cb(hwnd, _):
            nonlocal target_hwnd
            if IsWindowVisible(hwnd):
                length = GetWindowTextLengthW(hwnd)
                if length > 0:
                    buf = ctypes.create_unicode_buffer(length + 1)
                    GetWindowTextW(hwnd, buf, length + 1)
                    if keyword in buf.value.lower():
                        target_hwnd = hwnd
                        return False  # stop enumerating
            return True

        # Poll for the window to appear
        start = time.time()
        while time.time() - start < max_wait:
            target_hwnd = None
            EnumWindows(WNDENUMPROC(enum_cb), 0)
            if target_hwnd:
                break
            time.sleep(0.3)

        if target_hwnd:
            ShowWindow(target_hwnd, 9)  # SW_RESTORE
            SetForegroundWindow(target_hwnd)
            BringWindowToTop(target_hwnd)
            return True
    except Exception:
        pass
    return False


def open_application(name: str) -> str:
    """Open an application by name and bring it to the foreground."""
    app_map = {
        "chrome": "chrome", "google chrome": "chrome",
        "firefox": "firefox", "edge": "msedge",
        "notepad": "notepad", "calculator": "calc",
        "paint": "mspaint", "explorer": "explorer",
        "cmd": "cmd", "terminal": "wt", "powershell": "powershell",
        "vscode": "code", "vs code": "code", "visual studio code": "code",
        "task manager": "taskmgr", "snipping tool": "snippingtool",
        "word": "winword", "excel": "excel", "powerpoint": "powerpnt",
        "spotify": "spotify", "discord": "discord",
        "vlc": "vlc", "steam": "steam",
        "obs": "obs64",
    }
    # Map for window title keyword to search after opening
    title_map = {
        "notepad": "Notepad", "calc": "Calculator", "mspaint": "Paint",
        "chrome": "Chrome", "msedge": "Edge", "firefox": "Firefox",
        "explorer": "Explorer", "code": "Visual Studio Code",
        "spotify": "Spotify", "discord": "Discord", "vlc": "VLC",
        "taskmgr": "Task Manager", "cmd": "Command Prompt",
        "wt": "Terminal", "powershell": "PowerShell",
        "winword": "Word", "excel": "Excel", "powerpnt": "PowerPoint",
    }
    cmd = app_map.get(name.lower(), name)
    try:
        subprocess.Popen(cmd, shell=True)
        # Find the window by title and bring it to the foreground
        title_keyword = title_map.get(cmd, name)
        focused = _bring_window_to_front(title_keyword, max_wait=3.0)
        status = "(focused)" if focused else "(may be in background — use find_and_click to interact)"
        return f"Opened {name} {status}. Use wait_seconds(2) then screen_read() to see it."
    except Exception as e:
        return f"Error opening {name}: {e}"


def close_application(name: str) -> str:
    """Close an application by name."""
    try:
        subprocess.run(f"taskkill /IM {name}.exe /F", shell=True, capture_output=True)
        return f"Closed application: {name}"
    except Exception as e:
        return f"Error closing {name}: {e}"


# ─── Browser / URL ──────────────────────────────────────────────────

def open_url(url: str) -> str:
    """Open a URL in the default browser and bring it to foreground."""
    import webbrowser
    webbrowser.open(url)
    return f"Opened URL: {url}. Page is loading — use wait_seconds(3) then screen_read() to see the page and continue interacting."


def web_search(query: str) -> str:
    """Search the web via Google."""
    import webbrowser
    url = f"https://www.google.com/search?q={query.replace(' ', '+')}"
    webbrowser.open(url)
    return f"Searching Google for: {query}. Use wait_seconds(3) then screen_read() to see results."


def browser_tabs() -> str:
    """List all open browser tabs (Chrome)."""
    try:
        import pyautogui
        # Use keyboard shortcut to show tab list
        pyautogui.hotkey('ctrl', 'shift', 'a')
        time.sleep(1)
        return "Opened Chrome tab search. Use screen_read() to see the tab list."
    except Exception as e:
        return f"Error: {e}"


def switch_browser_tab(title: str) -> str:
    """Switch to a browser tab by partial title match."""
    try:
        import pyautogui
        pyautogui.hotkey('ctrl', 'shift', 'a')
        time.sleep(0.5)
        pyautogui.typewrite(title[:30], interval=0.03)
        time.sleep(0.5)
        pyautogui.press('enter')
        return f'Switched to tab matching: "{title}"'
    except Exception as e:
        return f"Error: {e}"


def close_browser_tab(title: str = "") -> str:
    """Close the current browser tab."""
    import pyautogui
    if title:
        switch_browser_tab(title)
        time.sleep(0.5)
    pyautogui.hotkey('ctrl', 'w')
    return f"Closed browser tab{': ' + title if title else ''}."


# ─── Window Management ─────────────────────────────────────────────

def snap_window(direction: str) -> str:
    """Snap current window: left, right, maximize, minimize."""
    import pyautogui
    key_map = {"left": "left", "right": "right", "maximize": "up", "minimize": "down"}
    key = key_map.get(direction.lower())
    if key:
        pyautogui.hotkey('win', key)
        return f"Snapped window: {direction}"
    return f"Unknown direction: {direction}. Use: left, right, maximize, minimize"


def resize_window(width: int, height: int) -> str:
    """Resize the active window."""
    try:
        import pygetwindow as gw
        win = gw.getActiveWindow()
        if win:
            win.resizeTo(width, height)
            return f"Resized window to {width}x{height}."
        return "No active window found."
    except Exception as e:
        return f"Error: {e}"


def switch_desktop(direction: str = "right") -> str:
    """Switch virtual desktop (left or right)."""
    import pyautogui
    key = "right" if direction.lower() == "right" else "left"
    pyautogui.hotkey('ctrl', 'win', key)
    return f"Switched desktop: {direction}"


# ─── File Operations ────────────────────────────────────────────────

def read_file(path: str) -> str:
    """Read a file and return its contents."""
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            content = f.read()
        if len(content) > 5000:
            return f"File: {path}\n(Showing first 5000 chars)\n{content[:5000]}..."
        return f"File: {path}\n{content}"
    except Exception as e:
        return f"Error reading file: {e}"


def write_file(path: str, content: str) -> str:
    """Write content to a file. Creates directories if needed."""
    try:
        os.makedirs(os.path.dirname(path) if os.path.dirname(path) else ".", exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        # Check for truncation
        lines = content.strip().split('\n')
        last = lines[-1].strip() if lines else ""
        if last.endswith(('(', '[', '{')):
            return f"WARNING: File written to {path} but the last line ends with an unclosed bracket — code may be truncated! Please verify and fix."
        return f"File written to: {path}"
    except Exception as e:
        return f"Error writing file: {e}"


def list_directory(path: str = ".") -> str:
    """List files and folders in a directory."""
    try:
        entries = os.listdir(path)
        result = f"Directory: {path}\n"
        for e in sorted(entries)[:50]:
            full = os.path.join(path, e)
            if os.path.isdir(full):
                result += f"  📁 {e}/\n"
            else:
                size = os.path.getsize(full)
                result += f"  📄 {e} ({size:,} bytes)\n"
        if len(entries) > 50:
            result += f"  ... and {len(entries) - 50} more items"
        return result
    except Exception as e:
        return f"Error: {e}"


def search_in_files(query: str, directory: str = ".", extension: str = "") -> str:
    """Search for text inside files in a directory."""
    try:
        results = []
        for root, dirs, files in os.walk(directory):
            dirs[:] = [d for d in dirs if d not in {'.git', 'node_modules', '__pycache__', '.venv'}]
            for f in files:
                if extension and not f.endswith(extension):
                    continue
                fpath = os.path.join(root, f)
                try:
                    with open(fpath, 'r', encoding='utf-8', errors='ignore') as fh:
                        for i, line in enumerate(fh, 1):
                            if query.lower() in line.lower():
                                results.append(f"  {fpath}:{i}: {line.strip()[:100]}")
                                if len(results) >= 20:
                                    break
                except Exception:
                    pass
                if len(results) >= 20:
                    break
            if len(results) >= 20:
                break
        if results:
            return f"Found {len(results)} matches for '{query}':\n" + "\n".join(results)
        return f"No matches found for '{query}' in {directory}"
    except Exception as e:
        return f"Error: {e}"


def delete_file(path: str) -> str:
    """Delete a file."""
    try:
        os.remove(path)
        return f"Deleted: {path}"
    except Exception as e:
        return f"Error: {e}"


def open_file(path: str) -> str:
    """Open a file with its default application (e.g. .txt opens in Notepad, .pdf in reader)."""
    try:
        if not os.path.exists(path):
            return f"Error: File not found: {path}"
        os.startfile(path)
        return f"Opened: {path}"
    except Exception as e:
        return f"Error opening file: {e}"


# ─── Shell / Command ───────────────────────────────────────────────

def run_shell(command: str) -> str:
    """Run a shell command and return the output."""
    try:
        result = subprocess.run(
            command, shell=True, capture_output=True, text=True, timeout=30
        )
        output = result.stdout.strip()
        error = result.stderr.strip()
        combined = ""
        if output:
            combined += output[:3000]
        if error:
            combined += ("\n" if combined else "") + f"STDERR: {error[:1000]}"
        return combined or "Command completed (no output)."
    except subprocess.TimeoutExpired:
        return "Command timed out after 30 seconds."
    except Exception as e:
        return f"Error: {e}"


# ─── Clipboard ──────────────────────────────────────────────────────

def get_clipboard() -> str:
    """Get the current clipboard contents."""
    try:
        import pyperclip
        return f"Clipboard: {pyperclip.paste()[:2000]}"
    except Exception as e:
        return f"Error: {e}"


def set_clipboard(text: str) -> str:
    """Set clipboard contents."""
    try:
        import pyperclip
        pyperclip.copy(text)
        return "Text copied to clipboard."
    except Exception as e:
        return f"Error: {e}"


# ─── System Info ────────────────────────────────────────────────────

def get_system_info() -> str:
    """Get CPU, RAM, disk, and GPU info."""
    try:
        import psutil
        cpu = psutil.cpu_percent(interval=1)
        ram = psutil.virtual_memory()
        disk = psutil.disk_usage('C:\\')
        return (
            f"CPU: {cpu}% ({psutil.cpu_count()} cores)\n"
            f"RAM: {ram.used / 1e9:.1f} GB / {ram.total / 1e9:.1f} GB ({ram.percent}%)\n"
            f"Disk C: {disk.used / 1e9:.1f} GB / {disk.total / 1e9:.1f} GB ({disk.percent}%)"
        )
    except Exception as e:
        return f"Error: {e}"


def get_current_time() -> str:
    """Get the current date and time."""
    from datetime import datetime
    now = datetime.now()
    return f"Current time: {now.strftime('%Y-%m-%d %H:%M:%S')} ({now.strftime('%A')})"


# ─── Volume ─────────────────────────────────────────────────────────

def get_volume() -> int:
    """Get the current system volume (0-100)."""
    try:
        from ctypes import cast, POINTER
        from comtypes import CLSCTX_ALL
        from pycaw.pycaw import AudioUtilities, IAudioEndpointVolume
        devices = AudioUtilities.GetSpeakers()
        interface = devices.Activate(IAudioEndpointVolume._iid_, CLSCTX_ALL, None)
        volume = cast(interface, POINTER(IAudioEndpointVolume))
        return int(round(volume.GetMasterVolumeLevelScalar() * 100))
    except Exception:
        return 50


def set_volume(level: int) -> str:
    """Set system volume (0-100)."""
    try:
        from ctypes import cast, POINTER
        from comtypes import CLSCTX_ALL
        from pycaw.pycaw import AudioUtilities, IAudioEndpointVolume
        devices = AudioUtilities.GetSpeakers()
        interface = devices.Activate(IAudioEndpointVolume._iid_, CLSCTX_ALL, None)
        volume = cast(interface, POINTER(IAudioEndpointVolume))
        volume.SetMasterVolumeLevelScalar(max(0, min(100, level)) / 100, None)
        return f"Volume set to {level}%."
    except Exception as e:
        return f"Error setting volume: {e}"


# ─── Power ──────────────────────────────────────────────────────────

def lock_pc() -> str:
    """Lock the PC."""
    import ctypes
    ctypes.windll.user32.LockWorkStation()
    return "PC locked."


def shutdown_pc(restart: bool = False) -> str:
    """Shutdown or restart the PC."""
    flag = "/r" if restart else "/s"
    subprocess.run(f"shutdown {flag} /t 5", shell=True)
    return f"{'Restarting' if restart else 'Shutting down'} in 5 seconds."


# ─── Notifications ──────────────────────────────────────────────────

def show_notification(title: str, message: str = "") -> str:
    """Show a Windows notification."""
    try:
        from plyer import notification
        notification.notify(title=title, message=message or title, timeout=5)
        return f"Notification shown: {title}"
    except Exception:
        try:
            subprocess.run(
                f'powershell -Command "New-BurntToastNotification -Text \\"{title}\\", \\"{message}\\""',
                shell=True, capture_output=True
            )
            return f"Notification shown: {title}"
        except Exception as e:
            return f"Error: {e}"


# ─── Calendar & Tasks ──────────────────────────────────────────────

def add_calendar_event(title: str, date: str, time_str: str = "",
                       description: str = "", remind_at: str = "") -> str:
    """Add a calendar event. Date: YYYY-MM-DD, Time: HH:MM, remind_at: YYYY-MM-DD HH:MM."""
    try:
        import database as db
        event_id = db.add_calendar_event(
            title=title, event_date=date, event_time=time_str,
            description=description, remind_at=remind_at or None
        )
        result = f"Calendar event created (ID {event_id}): '{title}' on {date}"
        if time_str:
            result += f" at {time_str}"
        if remind_at:
            result += f" (reminder at {remind_at})"
        return result
    except Exception as e:
        return f"Error: {e}"


def list_calendar_events(date: str = "", include_done: bool = False) -> str:
    """List calendar events, optionally filtered by date."""
    try:
        import database as db
        events = db.list_calendar_events(date or None, include_done)
        if not events:
            return "No calendar events found."
        result = f"📅 Calendar events ({len(events)}):\n"
        for ev in events:
            status = "✅" if ev.get("done") else "⏳"
            result += f"  {status} [{ev['id']}] {ev['title']} — {ev['event_date']}"
            if ev.get("event_time"):
                result += f" {ev['event_time']}"
            result += "\n"
        return result
    except Exception as e:
        return f"Error: {e}"


def complete_calendar_event(event_id: int) -> str:
    """Mark a calendar event as done."""
    try:
        import database as db
        if db.complete_calendar_event(event_id):
            return f"Event {event_id} marked as done."
        return f"Event {event_id} not found."
    except Exception as e:
        return f"Error: {e}"


# ─── Camera ──────────────────────────────────────────────────────────

def capture_camera() -> str:
    """Capture a single frame from the webcam, analyze it with AI vision, and return the description."""
    try:
        import base64
        import config
        from openai import OpenAI

        img_b64 = None

        # First try: get the frame from the dashboard's video feed (shared buffer)
        try:
            from api_server import get_camera_frame
            shared_frame = get_camera_frame()
            if shared_frame:
                img_b64 = shared_frame
        except Exception:
            pass

        # Fallback: try OpenCV capture with warm-up frames
        if not img_b64:
            try:
                import cv2
                cap = cv2.VideoCapture(0, cv2.CAP_DSHOW)
                if not cap.isOpened():
                    return "Error: Could not open webcam. Make sure a camera is connected."
                cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
                cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)
                for _ in range(5):
                    cap.read()
                ret, frame = cap.read()
                cap.release()
                if not ret or frame is None:
                    return "Error: Failed to capture frame from webcam."
                _, buf = cv2.imencode('.jpg', frame, [cv2.IMWRITE_JPEG_QUALITY, 80])
                img_b64 = base64.b64encode(buf.tobytes()).decode('utf-8')
            except ImportError:
                return "Error: opencv-python not installed."

        if not img_b64:
            return "Error: No camera frame available. Make sure the camera is started on the dashboard."

        # Analyze with vision model
        vision_client = OpenAI(
            api_key=config.NVIDIA_VISION_API_KEY,
            base_url=config.NVIDIA_BASE_URL,
        )
        response = vision_client.chat.completions.create(
            model=config.NVIDIA_VISION_MODEL,
            messages=[
                {"role": "user", "content": [
                    {"type": "text", "text": "Describe what you see in this webcam image in detail. Be specific about people, objects, and the setting."},
                    {"type": "image_url", "image_url": {
                        "url": f"data:image/jpeg;base64,{img_b64}"
                    }}
                ]}
            ],
            max_tokens=1024,
            temperature=0.3,
        )
        description = response.choices[0].message.content or "I could not describe what I see."
        return f"What I see: {description}"
    except Exception as e:
        return f"Error capturing camera: {e}"


# ─── AI Vision Screen Understanding ─────────────────────────────────

def vision_screen_read(question: str = "Describe everything you see on this screen in detail. List all buttons, text fields, menus, and their approximate positions. What application is in focus?") -> str:
    """Take a screenshot and use AI vision to understand the screen — much smarter than OCR. Can identify UI elements, layouts, images, and context."""
    try:
        import pyautogui
        import base64
        import config
        from openai import OpenAI
        from io import BytesIO

        screenshot = pyautogui.screenshot()
        buf = BytesIO()
        screenshot.save(buf, format='JPEG', quality=75)
        img_b64 = base64.b64encode(buf.getvalue()).decode('utf-8')

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
            max_tokens=2048,
            temperature=0.3,
        )
        description = response.choices[0].message.content or "Could not analyze screen."
        w, h = screenshot.size
        return f"Screen ({w}x{h}) — AI Vision Analysis:\n{description}"
    except Exception as e:
        return f"Error in vision screen read: {e}"


# ─── File Intelligence ──────────────────────────────────────────────

def find_my_file(description: str, file_type: str = "", days: int = 30) -> str:
    """Search for files by name, type, or description across Desktop, Downloads, and Documents. Finds recently modified/accessed files."""
    try:
        from datetime import datetime, timedelta
        user_home = os.path.expanduser("~")
        search_dirs = [
            os.path.join(user_home, "Desktop"),
            os.path.join(user_home, "Downloads"),
            os.path.join(user_home, "Documents"),
            "D:\\Echo_Operations",
        ]
        cutoff = time.time() - (days * 86400)
        keywords = description.lower().split()
        matches = []

        for search_dir in search_dirs:
            if not os.path.exists(search_dir):
                continue
            for root, dirs, files in os.walk(search_dir):
                dirs[:] = [d for d in dirs if d not in {'.git', 'node_modules', '__pycache__', '.venv', '$RECYCLE.BIN'}]
                for f in files:
                    fpath = os.path.join(root, f)
                    try:
                        stat = os.stat(fpath)
                        mtime = stat.st_mtime
                        if mtime < cutoff:
                            continue
                        fname_lower = f.lower()
                        # Filter by file type if specified
                        if file_type:
                            ext = file_type.lower().lstrip('.')
                            if not fname_lower.endswith(f'.{ext}'):
                                continue
                        # Score by keyword match
                        score = sum(1 for kw in keywords if kw in fname_lower or kw in root.lower())
                        if score > 0 or not keywords:
                            from datetime import datetime as dt
                            mod_time = dt.fromtimestamp(mtime).strftime('%Y-%m-%d %H:%M')
                            size_kb = stat.st_size / 1024
                            matches.append((score, mtime, f, fpath, mod_time, size_kb))
                    except (PermissionError, OSError):
                        continue
                if len(matches) > 200:
                    break

        if not matches:
            return f"No files found matching '{description}'{' (type: ' + file_type + ')' if file_type else ''} in the last {days} days."

        # Sort by relevance score (desc), then by modification time (desc)
        matches.sort(key=lambda x: (-x[0], -x[1]))
        result = f"Found {len(matches)} file(s) matching '{description}':\n"
        for score, _, name, path, mod_time, size in matches[:15]:
            if size >= 1024:
                size_str = f"{size/1024:.1f} MB"
            else:
                size_str = f"{size:.0f} KB"
            result += f"  📄 {name} ({size_str}) — modified {mod_time}\n      Path: {path}\n"
        if len(matches) > 15:
            result += f"  ... and {len(matches) - 15} more files\n"
        return result
    except Exception as e:
        return f"Error searching files: {e}"


# ═══════════════════════════════════════════════════════════════════
#  TOOLS dict and TOOL_SCHEMAS list — used by brain.py
# ═══════════════════════════════════════════════════════════════════

TOOLS = {
    "move_mouse": move_mouse,
    "click": click,
    "double_click": double_click,
    "right_click": right_click,
    "scroll": scroll,
    "type_text": type_text,
    "press_key": press_key,
    "wait_seconds": wait_seconds,
    "screen_read": screen_read,
    "find_and_click": find_and_click,
    "take_screenshot": take_screenshot,
    "open_application": open_application,
    "close_application": close_application,
    "open_url": open_url,
    "web_search": web_search,
    "browser_tabs": browser_tabs,
    "switch_browser_tab": switch_browser_tab,
    "close_browser_tab": close_browser_tab,
    "snap_window": snap_window,
    "resize_window": resize_window,
    "switch_desktop": switch_desktop,
    "read_file": read_file,
    "write_file": write_file,
    "list_directory": list_directory,
    "search_in_files": search_in_files,
    "delete_file": delete_file,
    "run_shell": run_shell,
    "get_clipboard": get_clipboard,
    "set_clipboard": set_clipboard,
    "get_system_info": get_system_info,
    "get_current_time": get_current_time,
    "get_volume": get_volume,
    "set_volume": set_volume,
    "lock_pc": lock_pc,
    "shutdown_pc": shutdown_pc,
    "show_notification": show_notification,
    "add_calendar_event": add_calendar_event,
    "list_calendar_events": list_calendar_events,
    "complete_calendar_event": complete_calendar_event,
    "capture_camera": capture_camera,
    "vision_screen_read": vision_screen_read,
    "find_my_file": find_my_file,
    "open_file": open_file,
}


def _p(name, type_, desc, required=True, enum=None):
    """Helper to create a parameter definition."""
    p = {"type": type_, "description": desc}
    if enum:
        p["enum"] = enum
    return name, p, required


def _tool(name, desc, params=None):
    """Helper to create an OpenAI function schema."""
    schema = {
        "type": "function",
        "function": {
            "name": name,
            "description": desc,
            "parameters": {
                "type": "object",
                "properties": {},
                "required": [],
            }
        }
    }
    if params:
        for pname, pdef, req in params:
            schema["function"]["parameters"]["properties"][pname] = pdef
            if req:
                schema["function"]["parameters"]["required"].append(pname)
    return schema


TOOL_SCHEMAS = [
    _tool("move_mouse", "Move the mouse cursor to (x, y) coordinates.", [
        _p("x", "integer", "X coordinate"),
        _p("y", "integer", "Y coordinate"),
    ]),
    _tool("click", "Left-click at (x, y) coordinates.", [
        _p("x", "integer", "X coordinate"),
        _p("y", "integer", "Y coordinate"),
    ]),
    _tool("double_click", "Double-click at (x, y) coordinates.", [
        _p("x", "integer", "X coordinate"),
        _p("y", "integer", "Y coordinate"),
    ]),
    _tool("right_click", "Right-click at (x, y) coordinates.", [
        _p("x", "integer", "X coordinate"),
        _p("y", "integer", "Y coordinate"),
    ]),
    _tool("scroll", "Scroll the mouse wheel. Positive = up, negative = down.", [
        _p("clicks", "integer", "Number of scroll clicks (positive=up, negative=down)"),
    ]),
    _tool("type_text", "Type text using the keyboard. Supports any characters.", [
        _p("text", "string", "The text to type"),
    ]),
    _tool("press_key", "Press a key or key combo. Examples: 'enter', 'ctrl+c', 'alt+tab', 'win+d'.", [
        _p("keys", "string", "Key or combo to press (e.g. 'enter', 'ctrl+c', 'alt+f4')"),
    ]),
    _tool("wait_seconds", "Wait for N seconds. Use between steps to let apps/pages load.", [
        _p("seconds", "integer", "Number of seconds to wait"),
    ]),
    _tool("screen_read", "Read ALL text visible on screen with (x,y) coordinates. Use this to see what's on screen before clicking."),
    _tool("find_and_click", "Find text on screen using OCR and click on it.", [
        _p("text", "string", "The text to find and click on"),
    ]),
    _tool("take_screenshot", "Take a screenshot and save it.", [
        _p("filename", "string", "Optional filename for the screenshot", False),
    ]),
    _tool("open_application", "Open an application by name (e.g. 'chrome', 'notepad', 'vscode').", [
        _p("name", "string", "Application name"),
    ]),
    _tool("close_application", "Close an application by process name.", [
        _p("name", "string", "Application name to close"),
    ]),
    _tool("open_url", "Open a URL in the default browser.", [
        _p("url", "string", "The URL to open"),
    ]),
    _tool("web_search", "Search the web using Google.", [
        _p("query", "string", "Search query"),
    ]),
    _tool("browser_tabs", "List all open browser tabs."),
    _tool("switch_browser_tab", "Switch to a browser tab by partial title match.", [
        _p("title", "string", "Partial title of the tab to switch to"),
    ]),
    _tool("close_browser_tab", "Close a browser tab by partial title. Closes current tab if no title given.", [
        _p("title", "string", "Optional: partial title of tab to close", False),
    ]),
    _tool("snap_window", "Snap the current window: left, right, maximize, or minimize.", [
        _p("direction", "string", "Direction to snap", True, ["left", "right", "maximize", "minimize"]),
    ]),
    _tool("resize_window", "Resize the active window to given dimensions.", [
        _p("width", "integer", "Width in pixels"),
        _p("height", "integer", "Height in pixels"),
    ]),
    _tool("switch_desktop", "Switch to the next or previous virtual desktop.", [
        _p("direction", "string", "Direction: 'left' or 'right'", True, ["left", "right"]),
    ]),
    _tool("read_file", "Read the contents of a file.", [
        _p("path", "string", "Absolute file path"),
    ]),
    _tool("write_file", "Write content to a file. Creates directories if needed.", [
        _p("path", "string", "Absolute file path"),
        _p("content", "string", "Content to write"),
    ]),
    _tool("list_directory", "List files and folders in a directory.", [
        _p("path", "string", "Directory path", False),
    ]),
    _tool("search_in_files", "Search for text inside files recursively.", [
        _p("query", "string", "Text to search for"),
        _p("directory", "string", "Directory to search in", False),
        _p("extension", "string", "File extension filter (e.g. '.py')", False),
    ]),
    _tool("delete_file", "Delete a file.", [
        _p("path", "string", "File path to delete"),
    ]),
    _tool("run_shell", "Run a shell command and return its output.", [
        _p("command", "string", "Shell command to run"),
    ]),
    _tool("get_clipboard", "Get the current clipboard contents."),
    _tool("set_clipboard", "Copy text to the clipboard.", [
        _p("text", "string", "Text to copy to clipboard"),
    ]),
    _tool("get_system_info", "Get CPU, RAM, disk, and GPU information."),
    _tool("get_current_time", "Get the current date, time, and day of the week."),
    _tool("get_volume", "Get the current system volume level (0-100)."),
    _tool("set_volume", "Set the system volume level.", [
        _p("level", "integer", "Volume level 0-100"),
    ]),
    _tool("lock_pc", "Lock the PC screen."),
    _tool("shutdown_pc", "Shutdown or restart the PC.", [
        _p("restart", "boolean", "If true, restart instead of shutdown", False),
    ]),
    _tool("show_notification", "Show a Windows desktop notification.", [
        _p("title", "string", "Notification title"),
        _p("message", "string", "Notification message", False),
    ]),
    _tool("add_calendar_event", "Add a calendar event or reminder.", [
        _p("title", "string", "Event title"),
        _p("date", "string", "Date in YYYY-MM-DD format"),
        _p("time_str", "string", "Time in HH:MM format (24-hour)", False),
        _p("description", "string", "Event description", False),
        _p("remind_at", "string", "Reminder datetime: YYYY-MM-DD HH:MM", False),
    ]),
    _tool("list_calendar_events", "List calendar events, optionally filtered by date.", [
        _p("date", "string", "Optional date filter: YYYY-MM-DD", False),
        _p("include_done", "boolean", "Include completed events", False),
    ]),
    _tool("complete_calendar_event", "Mark a calendar event as done.", [
        _p("event_id", "integer", "The event ID to mark as done"),
    ]),
    _tool("capture_camera", "Capture a photo from the webcam and describe what the camera sees using AI vision."),
    _tool("vision_screen_read", "Use AI VISION to understand the screen — identifies UI elements, buttons, layouts, images, and context. Much smarter than OCR.", [
        _p("question", "string", "What to look for on screen. Default: describe everything visible.", False),
    ]),
    _tool("find_my_file", "Search for files by name, keyword, or type across Desktop, Downloads, and Documents. Great for 'find that PDF', 'where's my report', etc.", [
        _p("description", "string", "Keywords to search for (e.g. 'resume', 'project report', 'budget')"),
        _p("file_type", "string", "File extension filter (e.g. 'pdf', 'docx', 'py')", False),
        _p("days", "integer", "Search files modified in the last N days (default: 30)", False),
    ]),
    _tool("open_file", "Open a file with its default application. Use this to open previously saved files instead of opening blank apps.", [
        _p("path", "string", "Absolute file path to open"),
    ]),
]
