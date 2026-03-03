"""
Echo AI Agent — Brain (LLM + Tool-Calling Engine)
Uses NVIDIA NIM or Ollama (OpenAI-compatible) with a ReAct-style tool loop.
Supports multi-model switching, conversation persistence, and parallel tool calls.
"""

import json
import logging
import os
from concurrent.futures import ThreadPoolExecutor, as_completed
from openai import OpenAI

import config
import database as db
from tools import TOOLS, TOOL_SCHEMAS

log = logging.getLogger("echo.brain")

_USERNAME = os.environ.get("USERNAME", "User")
_USER_HOME = os.path.expanduser("~")
_DESKTOP = os.path.join(_USER_HOME, "Desktop")
_DOWNLOADS = os.path.join(_USER_HOME, "Downloads")

# Tools that interact with the screen/UI — must run sequentially
_UI_TOOLS = {
    "move_mouse", "click", "double_click", "right_click", "scroll",
    "type_text", "press_key", "find_and_click", "screen_read",
    "take_screenshot", "open_application", "close_application",
    "open_url", "web_search", "snap_window", "resize_window",
    "switch_desktop", "browser_tabs", "switch_browser_tab", "close_browser_tab",
}

SYSTEM_PROMPT = f"""You are {config.AGENT_NAME}, an AI assistant with FULL autonomous control over the user's Windows PC. Be concise, fast, and proactive — like JARVIS.

CORE RULES:
- COMPLETE every task end-to-end. Never ask the user to do intermediate steps.
- SPEED is #1 priority. Use the FEWEST tool calls possible.
- For writing tasks (stories, code, essays): use write_file() directly. ONE call. NEVER open Notepad to type.
- For info tasks (time, system info): call the tool and respond immediately.
- Keep responses SHORT: "Done! File saved at X" or "Playing Y on YouTube."

YOUTUBE / VIDEO PLAYBACK:
- BEST approach: open_url("https://youtube.com/results?search_query=X") → wait_seconds(5) → vision_screen_read("find the first video result thumbnail and title, give me the y coordinate to click") → click(x, y) on the first video.
- If vision_screen_read is unavailable: after wait_seconds(5), use find_and_click() with a SHORT keyword from the video title (e.g. "7 Years" → find_and_click("Years")).
- NEVER click on the URL bar, search bar, or sidebar. Video results start around y=300+.
- After clicking a video, wait 2 seconds for it to load. Done.

OPENING FILES:
- When user says "open that file" or refers to a previously created file, check the conversation context for the file path. Then use open_file(path) — NOT open_application("notepad").
- open_file(path) opens any file with its default app (Notepad for .txt, browser for .html, etc.)
- If you don't know the path, use find_my_file(description) to locate it first.

SCREEN INTERACTION (for web/UI tasks only):
- find_and_click("text") is your fastest tool — it reads the screen AND clicks in one call.
- If find_and_click fails OR the page has lots of images/thumbnails, use vision_screen_read() instead of screen_read(). Vision understands images and layout; OCR only reads text.
- Max 2 screen_read() calls per task. Don't over-verify.
- After opening a URL, ALWAYS wait_seconds(5) before interacting with the page.

ERROR HANDLING:
- Max 2 retries per failing tool, then switch to alternative or report error.
- If screen_read() gives too few results → use vision_screen_read() instead.
- If find_and_click() fails → use vision_screen_read() to get coordinates, then click(x, y).
- Never press the same key more than 3 times.

FILE PATHS:
- Desktop: {_DESKTOP} | Downloads: {_DOWNLOADS}
- DEFAULT save folder: D:\\\\Echo_Operations (use this for all created files)
- Always use REAL paths, never placeholders.
- For code: ensure all brackets are properly closed. Try to run after writing.
- For text/stories: write FULL content (300-500+ words), never truncate.

CALENDAR: add_calendar_event(title, date="YYYY-MM-DD", time="HH:MM"), list_calendar_events(), complete_calendar_event(id)
When user says "remind me" → create calendar event with remind_at.

BROWSER: use browser_tabs() to see tabs, close_browser_tab("title") to close specific tabs. Never ctrl+w blindly.

GENERAL RULES:
- Always respond in natural conversational English
- When opening apps, use common names (chrome, notepad, vscode, etc.)
- For key combos, use format like 'ctrl+c', 'alt+tab', 'win+d'
- Confirm at the end with a brief summary of what you did
- REMEMBER previous tool results in the conversation — reference file paths, URLs, etc. from earlier in the chat.
"""


def _get_client_config(provider: str | None = None) -> tuple[str, str, str]:
    """Return (api_key, base_url, model) for the given provider."""
    provider = provider or config.MODEL_PROVIDER
    if provider == "ollama":
        return "ollama", config.OLLAMA_BASE_URL, config.OLLAMA_MODEL
    else:  # default: nvidia
        return config.NVIDIA_API_KEY, config.NVIDIA_BASE_URL, config.NVIDIA_MODEL


class EchoBrain:
    """Core AI reasoning engine with tool-calling capabilities."""

    def __init__(self, provider: str | None = None):
        self._provider = provider or config.MODEL_PROVIDER
        api_key, base_url, model = _get_client_config(self._provider)
        self.client = OpenAI(api_key=api_key, base_url=base_url)
        self._model = model
        self.conversation_history = [
            {"role": "system", "content": SYSTEM_PROMPT}
        ]
        self.max_history = 20  # Keep last N messages — smaller = faster LLM responses

        # Conversation persistence
        self._conversation_id = db.create_conversation("New Chat")
        log.info(f"New conversation: {self._conversation_id}")

    @property
    def model_name(self) -> str:
        return self._model

    @property
    def provider(self) -> str:
        return self._provider

    @property
    def conversation_id(self) -> str:
        return self._conversation_id

    def switch_model(self, provider: str):
        """Switch to a different model provider at runtime."""
        api_key, base_url, model = _get_client_config(provider)
        self.client = OpenAI(api_key=api_key, base_url=base_url)
        self._model = model
        self._provider = provider
        log.info(f"Switched to model: {model} (provider: {provider})")

    def _trim_history(self):
        """Keep conversation history manageable."""
        if len(self.conversation_history) > self.max_history:
            # Always keep system prompt + last N messages
            self.conversation_history = (
                [self.conversation_history[0]]
                + self.conversation_history[-(self.max_history - 1):]
            )

    def _execute_tool(self, name: str, arguments: dict) -> str:
        """Execute a tool by name with given arguments."""
        func = TOOLS.get(name)
        if not func:
            return f"Error: Unknown tool '{name}'"
        try:
            result = func(**arguments)
            return str(result)
        except Exception as e:
            return f"Error executing {name}: {e}"

    def _should_run_parallel(self, tool_calls) -> bool:
        """Check if tool calls can run in parallel (no UI tools)."""
        if len(tool_calls) <= 1:
            return False
        return not any(tc.function.name in _UI_TOOLS for tc in tool_calls)

    def chat(self, user_message: str) -> str:
        """Send a message to the AI and get a response, with tool execution."""
        self.conversation_history.append({"role": "user", "content": user_message})
        self._trim_history()

        # Persist user message
        db.save_message(self._conversation_id, "user", user_message)

        # Auto-title the conversation from the first user message
        if len([m for m in self.conversation_history if m["role"] == "user"]) == 1:
            title = user_message[:60].strip()
            if title:
                db.update_conversation_title(self._conversation_id, title)

        max_iterations = 8  # Safety cap — keeps tasks fast
        iteration = 0
        last_error = None
        error_repeat_count = 0

        while iteration < max_iterations:
            iteration += 1
            try:
                # Build API call kwargs
                api_kwargs = dict(
                    model=self._model,
                    messages=self.conversation_history,
                    tools=TOOL_SCHEMAS,
                    tool_choice="auto",
                    temperature=0.2,
                    max_tokens=4096,
                )
                # NVIDIA NIM does NOT support parallel tool calls — only enable for Ollama
                if self._provider == "ollama":
                    api_kwargs["parallel_tool_calls"] = True
                else:
                    api_kwargs["parallel_tool_calls"] = False

                response = self.client.chat.completions.create(**api_kwargs, timeout=60)
            except Exception as e:
                error_msg = f"API error: {e}"
                log.error(error_msg)
                return f"Sorry, I encountered an error: {e}"

            message = response.choices[0].message

            # If no tool calls, we have the final response
            if not message.tool_calls:
                assistant_text = message.content or "Done."
                self.conversation_history.append(
                    {"role": "assistant", "content": assistant_text}
                )
                db.save_message(self._conversation_id, "assistant", assistant_text)
                return assistant_text

            # Determine which tool calls to process
            tool_calls_to_process = message.tool_calls
            run_parallel = self._should_run_parallel(tool_calls_to_process)

            # NVIDIA only supports single tool calls — always process one at a time
            if self._provider != "ollama" and len(tool_calls_to_process) > 1:
                tool_calls_to_process = tool_calls_to_process[:1]
                run_parallel = False
            elif not run_parallel and len(tool_calls_to_process) > 1:
                tool_calls_to_process = tool_calls_to_process[:1]

            self.conversation_history.append({
                "role": "assistant",
                "content": message.content or "",
                "tool_calls": [
                    {
                        "id": tc.id,
                        "type": "function",
                        "function": {
                            "name": tc.function.name,
                            "arguments": tc.function.arguments,
                        },
                    }
                    for tc in tool_calls_to_process
                ],
            })

            # Persist assistant message with tool calls
            db.save_message(
                self._conversation_id, "assistant", message.content or "",
                tool_calls_json=json.dumps([
                    {"id": tc.id, "type": "function", "function": {"name": tc.function.name, "arguments": tc.function.arguments}}
                    for tc in tool_calls_to_process
                ]),
            )

            def _parse_and_execute(tool_call):
                """Parse arguments and execute a single tool call. Returns (tool_call, result)."""
                func_name = tool_call.function.name
                raw_args = tool_call.function.arguments
                try:
                    func_args = json.loads(raw_args)
                except json.JSONDecodeError:
                    # Try to repair truncated JSON
                    repaired = raw_args
                    if repaired.count('"') % 2 != 0:
                        repaired += '"'
                    for open_c, close_c in [('[', ']'), ('{', '}')]:
                        diff = repaired.count(open_c) - repaired.count(close_c)
                        repaired += close_c * max(0, diff)
                    try:
                        func_args = json.loads(repaired)
                        log.warning(f"Repaired truncated JSON for {func_name}")
                    except json.JSONDecodeError:
                        func_args = {}
                        log.error(f"Could not parse tool arguments for {func_name}: {raw_args[:100]}")

                log.info(f"Tool call [{iteration}]: {func_name}({func_args})")
                result = self._execute_tool(func_name, func_args)
                log.info(f"Tool result: {result[:200]}")
                return tool_call, result

            # Execute tools — parallel or sequential
            if run_parallel and len(tool_calls_to_process) > 1:
                log.info(f"Running {len(tool_calls_to_process)} tool calls in PARALLEL")
                results_map = {}
                with ThreadPoolExecutor(max_workers=4) as executor:
                    futures = {executor.submit(_parse_and_execute, tc): tc for tc in tool_calls_to_process}
                    for future in as_completed(futures):
                        tc, result = future.result()
                        results_map[tc.id] = (tc, result)

                # Add results in original order
                for tc in tool_calls_to_process:
                    _, result = results_map[tc.id]
                    self._handle_tool_result(tc, result, iteration, last_error, error_repeat_count)
            else:
                for tool_call in tool_calls_to_process:
                    _, result = _parse_and_execute(tool_call)
                    bail = self._handle_tool_result(tool_call, result, iteration, last_error, error_repeat_count)
                    if bail is not None:
                        return bail

            # Loop continues — model will see the result and decide next step

        return "I completed the maximum number of actions for this request. Let me know if you need more."

    def _handle_tool_result(self, tool_call, result, iteration, last_error, error_repeat_count):
        """Process a tool result, append to history, handle error loops.
        Returns a string to bail out with, or None to continue."""
        # Detect repeated failures
        if "failed" in result.lower() or "error" in result.lower() or "syntaxerror" in result.lower():
            error_key = result[:100]
            if error_key == last_error:
                error_repeat_count += 1
            else:
                last_error = error_key
                error_repeat_count = 1

            if error_repeat_count >= 3:
                log.warning(f"Same error repeated {error_repeat_count} times, breaking loop.")
                self.conversation_history.append({
                    "role": "tool",
                    "tool_call_id": tool_call.id,
                    "content": result + "\n\n[SYSTEM: This error has repeated 3 times. STOP retrying the same approach. Either try a completely different method or report the issue to the user.]",
                })
                db.save_message(self._conversation_id, "tool", result, tool_call_id=tool_call.id)
                try:
                    final_resp = self.client.chat.completions.create(
                        model=self._model,
                        messages=self.conversation_history,
                        temperature=0.3,
                        max_tokens=512,
                    )
                    final_text = final_resp.choices[0].message.content or "I wasn't able to complete that task after several attempts."
                    self.conversation_history.append({"role": "assistant", "content": final_text})
                    db.save_message(self._conversation_id, "assistant", final_text)
                    return final_text
                except Exception:
                    return "I ran into repeated errors and couldn't complete the task. Please try rephrasing your request."
        else:
            last_error = None
            error_repeat_count = 0

        self.conversation_history.append({
            "role": "tool",
            "tool_call_id": tool_call.id,
            "content": result,
        })
        db.save_message(self._conversation_id, "tool", result, tool_call_id=tool_call.id)
        return None

    def load_conversation(self, conversation_id: str):
        """Load a previous conversation from the database."""
        messages = db.get_conversation_messages(conversation_id)
        if not messages:
            log.warning(f"No messages found for conversation {conversation_id}")
            return
        self._conversation_id = conversation_id
        self.conversation_history = [{"role": "system", "content": SYSTEM_PROMPT}] + messages
        log.info(f"Loaded conversation {conversation_id} with {len(messages)} messages")

    def reset(self):
        """Clear conversation history and start a new conversation."""
        self.conversation_history = [
            {"role": "system", "content": SYSTEM_PROMPT}
        ]
        self._conversation_id = db.create_conversation("New Chat")
        log.info(f"Reset — new conversation: {self._conversation_id}")

    def describe_camera(self, question: str = "What do you see in this image?") -> str:
        """Capture a webcam frame and analyze it with the vision model."""
        try:
            import cv2
            import base64

            cap = cv2.VideoCapture(0, cv2.CAP_DSHOW)
            if not cap.isOpened():
                return "Error: Could not open webcam."
            cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
            cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)
            ret, frame = cap.read()
            cap.release()
            if not ret or frame is None:
                return "Error: Failed to capture frame."

            # Encode frame to base64 JPEG
            _, buf = cv2.imencode('.jpg', frame, [cv2.IMWRITE_JPEG_QUALITY, 80])
            img_b64 = base64.b64encode(buf.tobytes()).decode('utf-8')

            # Send to vision model
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
            return response.choices[0].message.content or "I could not describe what I see."
        except Exception as e:
            log.error(f"Camera vision error: {e}")
            return f"Error analyzing camera: {e}"
