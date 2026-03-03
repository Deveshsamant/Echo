"""
Echo AI Agent — Personal AI Assistant Brain ("Echo Lite")
A lightweight LLM brain with NO PC control tools.
Works as a standalone AI assistant — can function even when PC tools are unavailable.
Supports queuing PC tasks for later execution.
"""

import json
import logging
from datetime import datetime
from openai import OpenAI

import config
import database as db

log = logging.getLogger("echo.assistant")


ASSISTANT_SYSTEM_PROMPT = f"""You are {config.AGENT_NAME}, a smart and friendly personal AI assistant.

PERSONALITY:
- You are warm, helpful, and concise — like a personal secretary.
- You give direct, useful answers without unnecessary filler.
- You format responses with clear structure when needed.

CAPABILITIES:
- Answer questions on any topic using your knowledge
- Help with writing, brainstorming, planning, coding, and problem-solving
- Manage your calendar: add events, list upcoming tasks, mark tasks as done
- Queue SPECIFIC PC tasks to be executed later when the PC is online

IMPORTANT — WHEN TO USE TOOLS vs JUST ANSWER:
- Questions like "brainstorm ideas", "help me write", "explain something", "what is..." → Just ANSWER directly in text. Do NOT call any tool.
- "Add a reminder for X" or "What's on my calendar?" → Use calendar tools.
- "Open Chrome", "Download Python", "Run the backup script" → These are REAL PC commands. Use add_task_for_pc() to queue them.
- When in doubt, JUST ANSWER. Most requests are conversational — only use add_task_for_pc() for commands that literally need to run on the PC.

CALENDAR & TASKS:
- Use add_calendar_event() to schedule events or reminders
- Date format: YYYY-MM-DD (e.g. "2026-03-01")
- Time format: HH:MM (24-hour, e.g. "14:30")
- For reminders, set remind_at to when the user should be notified
- Use list_calendar_events() to show upcoming events
- Use complete_calendar_event() to mark a task as done

PC TASK QUEUING — ONLY for REAL PC actions:
- ONLY use add_task_for_pc() when the user explicitly asks to DO something on the PC:
  Examples: "Open Chrome and go to YouTube", "Download VLC", "Run my Python script", "Lock the PC"
- Do NOT queue conversational requests like "brainstorm ideas" or "help me write" — those are things YOU should answer directly.
- The queued task will automatically execute on the PC when it is online.

You do NOT have access to PC control tools (no mouse, keyboard, screenshot, etc.)
If the user asks for PC actions, QUEUE them using add_task_for_pc().
Current date/time: Use get_current_time() if the user asks.
"""


# ─── Assistant-Mode Tools ────────────────────────────────────────────

def _add_calendar_event(title: str, date: str, time: str = "",
                        description: str = "", remind_at: str = "") -> str:
    """Add a calendar event or reminder."""
    try:
        remind = remind_at if remind_at else None
        event_id = db.add_calendar_event(title, date, time, description, remind)
        parts = [f"📅 Event added (ID: {event_id}): {title} on {date}"]
        if time:
            parts.append(f"at {time}")
        if remind_at:
            parts.append(f"⏰ Reminder set for {remind_at}")
        return " ".join(parts)
    except Exception as e:
        return f"Error adding event: {e}"


def _list_calendar_events(date: str = "") -> str:
    """List calendar events."""
    try:
        events = db.list_calendar_events(date if date else None)
        if not events:
            return "No upcoming events found." if not date else f"No events on {date}."

        lines = [f"📅 {'Events on ' + date if date else 'Upcoming events'} ({len(events)}):"]
        for e in events:
            status = "✅" if e["is_done"] else "⬜"
            time_str = f" at {e['event_time']}" if e.get("event_time") else ""
            lines.append(f"  {status} [{e['id']}] {e['title']} — {e['event_date']}{time_str}")
            if e.get("description"):
                lines.append(f"       {e['description']}")
        return "\n".join(lines)
    except Exception as e:
        return f"Error listing events: {e}"


def _complete_calendar_event(event_id: int) -> str:
    """Mark a calendar event as done."""
    try:
        if db.complete_calendar_event(int(event_id)):
            return f"✅ Event {event_id} marked as done."
        return f"Event {event_id} not found."
    except Exception as e:
        return f"Error completing event: {e}"


def _add_task_for_pc(command: str) -> str:
    """Queue a PC command to run when the PC is online."""
    try:
        task_id = db.add_task(command, source="assistant")
        return f"🖥️ PC task queued (ID: {task_id}): \"{command}\"\nThis will execute automatically when the PC is online."
    except Exception as e:
        return f"Error queuing task: {e}"


def _get_current_time() -> str:
    """Get the current date and time."""
    now = datetime.now()
    return now.strftime("It is %I:%M %p on %A, %B %d, %Y.")


ASSISTANT_TOOLS = {
    "add_calendar_event": _add_calendar_event,
    "list_calendar_events": _list_calendar_events,
    "complete_calendar_event": _complete_calendar_event,
    "add_task_for_pc": _add_task_for_pc,
    "get_current_time": _get_current_time,
}

ASSISTANT_TOOL_SCHEMAS = [
    {"type": "function", "function": {"name": "add_calendar_event", "description": "Add a calendar event or reminder. Set remind_at to get a notification at a specific time.", "parameters": {"type": "object", "properties": {"title": {"type": "string", "description": "Event title"}, "date": {"type": "string", "description": "Event date (YYYY-MM-DD)"}, "time": {"type": "string", "description": "Event time (HH:MM, 24-hour, optional)"}, "description": {"type": "string", "description": "Event description (optional)"}, "remind_at": {"type": "string", "description": "When to remind (YYYY-MM-DD HH:MM, optional)"}}, "required": ["title", "date"]}}},
    {"type": "function", "function": {"name": "list_calendar_events", "description": "List upcoming calendar events. Optionally filter by date.", "parameters": {"type": "object", "properties": {"date": {"type": "string", "description": "Filter by date (YYYY-MM-DD, optional)"}}, "required": []}}},
    {"type": "function", "function": {"name": "complete_calendar_event", "description": "Mark a calendar event as done by its ID.", "parameters": {"type": "object", "properties": {"event_id": {"type": "integer", "description": "Event ID to mark as done"}}, "required": ["event_id"]}}},
    {"type": "function", "function": {"name": "add_task_for_pc", "description": "Queue a command to be executed on the PC when it comes online. Use this when the user asks for PC actions like opening apps, downloading files, running commands, etc.", "parameters": {"type": "object", "properties": {"command": {"type": "string", "description": "The PC command to queue (natural language, e.g. 'Open Chrome and go to youtube.com')"}}, "required": ["command"]}}},
    {"type": "function", "function": {"name": "get_current_time", "description": "Get the current date and time.", "parameters": {"type": "object", "properties": {}, "required": []}}},
]


class AssistantBrain:
    """Lightweight AI assistant without PC control tools."""

    def __init__(self, provider: str | None = None):
        self._provider = provider or config.MODEL_PROVIDER
        api_key, base_url, model = self._get_config()
        self.client = OpenAI(api_key=api_key, base_url=base_url)
        self._model = model
        self.conversation_history = [
            {"role": "system", "content": ASSISTANT_SYSTEM_PROMPT}
        ]
        self.max_history = 30
        self._conversation_id = db.create_conversation("Assistant Chat")

    def _get_config(self):
        p = self._provider
        if p == "ollama":
            return "ollama", config.OLLAMA_BASE_URL, config.OLLAMA_MODEL
        return config.NVIDIA_API_KEY, config.NVIDIA_BASE_URL, config.NVIDIA_MODEL

    @property
    def model_name(self) -> str:
        return self._model

    @property
    def conversation_id(self) -> str:
        return self._conversation_id

    def _trim_history(self):
        if len(self.conversation_history) > self.max_history:
            self.conversation_history = (
                [self.conversation_history[0]]
                + self.conversation_history[-(self.max_history - 1):]
            )

    def _execute_tool(self, name: str, arguments: dict) -> str:
        func = ASSISTANT_TOOLS.get(name)
        if not func:
            return f"Error: Unknown tool '{name}'"
        try:
            result = func(**arguments)
            return str(result)
        except Exception as e:
            return f"Error executing {name}: {e}"

    def chat(self, user_message: str) -> str:
        """Chat with the assistant (no PC tools)."""
        self.conversation_history.append({"role": "user", "content": user_message})
        self._trim_history()
        db.save_message(self._conversation_id, "user", user_message)

        if len([m for m in self.conversation_history if m["role"] == "user"]) == 1:
            title = user_message[:60].strip()
            if title:
                db.update_conversation_title(self._conversation_id, title)

        max_iterations = 8
        iteration = 0

        while iteration < max_iterations:
            iteration += 1
            try:
                response = self.client.chat.completions.create(
                    model=self._model,
                    messages=self.conversation_history,
                    tools=ASSISTANT_TOOL_SCHEMAS,
                    tool_choice="auto",
                    temperature=0.5,
                    max_tokens=2048,
                )
            except Exception as e:
                log.error(f"Assistant API error: {e}")
                return f"Sorry, I encountered an error: {e}"

            message = response.choices[0].message

            if not message.tool_calls:
                assistant_text = message.content or "Done."
                self.conversation_history.append({"role": "assistant", "content": assistant_text})
                db.save_message(self._conversation_id, "assistant", assistant_text)
                return assistant_text

            # Process tool calls
            self.conversation_history.append({
                "role": "assistant",
                "content": message.content or "",
                "tool_calls": [
                    {"id": tc.id, "type": "function", "function": {"name": tc.function.name, "arguments": tc.function.arguments}}
                    for tc in message.tool_calls
                ],
            })

            for tool_call in message.tool_calls:
                func_name = tool_call.function.name
                try:
                    func_args = json.loads(tool_call.function.arguments)
                except json.JSONDecodeError:
                    func_args = {}

                log.info(f"Assistant tool: {func_name}({func_args})")
                result = self._execute_tool(func_name, func_args)
                log.info(f"Assistant result: {result[:200]}")

                self.conversation_history.append({
                    "role": "tool",
                    "tool_call_id": tool_call.id,
                    "content": result,
                })
                db.save_message(self._conversation_id, "tool", result, tool_call_id=tool_call.id)

        return "Done."

    def reset(self):
        """Start a new assistant conversation."""
        self.conversation_history = [
            {"role": "system", "content": ASSISTANT_SYSTEM_PROMPT}
        ]
        self._conversation_id = db.create_conversation("Assistant Chat")
