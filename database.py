"""
Echo AI Agent — Database Layer (SQLite)
Stores conversations, calendar events, and queued PC tasks.
"""

import json
import logging
import sqlite3
import threading
import uuid
from datetime import datetime

import config

log = logging.getLogger("echo.db")

# Thread-local storage for connections (SQLite is not thread-safe)
_local = threading.local()


def _get_conn() -> sqlite3.Connection:
    """Get a thread-local SQLite connection."""
    if not hasattr(_local, "conn") or _local.conn is None:
        _local.conn = sqlite3.connect(config.DB_PATH, check_same_thread=False)
        _local.conn.row_factory = sqlite3.Row
        _local.conn.execute("PRAGMA journal_mode=WAL")
        _local.conn.execute("PRAGMA foreign_keys=ON")
    return _local.conn


def init_db():
    """Create all tables if they don't exist."""
    conn = _get_conn()
    conn.executescript("""
        CREATE TABLE IF NOT EXISTS conversations (
            id          TEXT PRIMARY KEY,
            title       TEXT NOT NULL DEFAULT 'New Chat',
            created_at  TEXT NOT NULL DEFAULT (datetime('now')),
            updated_at  TEXT NOT NULL DEFAULT (datetime('now'))
        );

        CREATE TABLE IF NOT EXISTS messages (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            conversation_id TEXT NOT NULL,
            role            TEXT NOT NULL,
            content         TEXT,
            tool_calls_json TEXT,
            tool_call_id    TEXT,
            created_at      TEXT NOT NULL DEFAULT (datetime('now')),
            FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS calendar_events (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            title       TEXT NOT NULL,
            description TEXT DEFAULT '',
            event_date  TEXT NOT NULL,
            event_time  TEXT DEFAULT '',
            remind_at   TEXT,
            is_done     INTEGER NOT NULL DEFAULT 0,
            reminded    INTEGER NOT NULL DEFAULT 0,
            created_at  TEXT NOT NULL DEFAULT (datetime('now'))
        );

        CREATE TABLE IF NOT EXISTS task_queue (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            command     TEXT NOT NULL,
            source      TEXT DEFAULT 'assistant',
            status      TEXT NOT NULL DEFAULT 'pending',
            result      TEXT,
            created_at  TEXT NOT NULL DEFAULT (datetime('now')),
            executed_at TEXT
        );

        CREATE INDEX IF NOT EXISTS idx_messages_conv ON messages(conversation_id);
        CREATE INDEX IF NOT EXISTS idx_calendar_date ON calendar_events(event_date);
        CREATE INDEX IF NOT EXISTS idx_task_status ON task_queue(status);
    """)
    conn.commit()
    log.info(f"Database initialized at {config.DB_PATH}")


# ─── Conversations ───────────────────────────────────────────────────

def create_conversation(title: str = "New Chat") -> str:
    """Create a new conversation and return its ID."""
    conv_id = str(uuid.uuid4())
    conn = _get_conn()
    conn.execute(
        "INSERT INTO conversations (id, title) VALUES (?, ?)",
        (conv_id, title),
    )
    conn.commit()
    return conv_id


def list_conversations(limit: int = 30) -> list[dict]:
    """List recent conversations, newest first."""
    conn = _get_conn()
    rows = conn.execute(
        "SELECT id, title, created_at, updated_at FROM conversations ORDER BY updated_at DESC LIMIT ?",
        (limit,),
    ).fetchall()
    return [dict(r) for r in rows]


def save_message(conversation_id: str, role: str, content: str | None,
                 tool_calls_json: str | None = None, tool_call_id: str | None = None):
    """Save a single message to a conversation."""
    conn = _get_conn()
    conn.execute(
        "INSERT INTO messages (conversation_id, role, content, tool_calls_json, tool_call_id) VALUES (?, ?, ?, ?, ?)",
        (conversation_id, role, content, tool_calls_json, tool_call_id),
    )
    conn.execute(
        "UPDATE conversations SET updated_at = datetime('now') WHERE id = ?",
        (conversation_id,),
    )
    conn.commit()


def get_conversation_messages(conversation_id: str) -> list[dict]:
    """Load all messages for a conversation, oldest first."""
    conn = _get_conn()
    rows = conn.execute(
        "SELECT role, content, tool_calls_json, tool_call_id FROM messages WHERE conversation_id = ? ORDER BY id",
        (conversation_id,),
    ).fetchall()

    messages = []
    for r in rows:
        msg = {"role": r["role"]}
        if r["content"] is not None:
            msg["content"] = r["content"]
        if r["tool_calls_json"]:
            msg["tool_calls"] = json.loads(r["tool_calls_json"])
        if r["tool_call_id"]:
            msg["tool_call_id"] = r["tool_call_id"]
        messages.append(msg)
    return messages


def update_conversation_title(conversation_id: str, title: str):
    """Update the title of a conversation."""
    conn = _get_conn()
    conn.execute(
        "UPDATE conversations SET title = ?, updated_at = datetime('now') WHERE id = ?",
        (title, conversation_id),
    )
    conn.commit()


def delete_conversation(conversation_id: str):
    """Delete a conversation and all its messages."""
    conn = _get_conn()
    conn.execute("DELETE FROM conversations WHERE id = ?", (conversation_id,))
    conn.commit()


# ─── Calendar Events ────────────────────────────────────────────────

def add_calendar_event(title: str, event_date: str, event_time: str = "",
                       description: str = "", remind_at: str | None = None) -> int:
    """Add a calendar event. Returns the event ID."""
    conn = _get_conn()
    cur = conn.execute(
        "INSERT INTO calendar_events (title, description, event_date, event_time, remind_at) VALUES (?, ?, ?, ?, ?)",
        (title, description, event_date, event_time, remind_at),
    )
    conn.commit()
    return cur.lastrowid


def list_calendar_events(date: str | None = None, include_done: bool = False) -> list[dict]:
    """List calendar events, optionally filtered by date."""
    conn = _get_conn()
    if date:
        if include_done:
            rows = conn.execute(
                "SELECT * FROM calendar_events WHERE event_date = ? ORDER BY event_time",
                (date,),
            ).fetchall()
        else:
            rows = conn.execute(
                "SELECT * FROM calendar_events WHERE event_date = ? AND is_done = 0 ORDER BY event_time",
                (date,),
            ).fetchall()
    else:
        if include_done:
            rows = conn.execute(
                "SELECT * FROM calendar_events ORDER BY event_date, event_time",
            ).fetchall()
        else:
            rows = conn.execute(
                "SELECT * FROM calendar_events WHERE is_done = 0 ORDER BY event_date, event_time",
            ).fetchall()
    return [dict(r) for r in rows]


def complete_calendar_event(event_id: int) -> bool:
    """Mark a calendar event as done. Returns True if found."""
    conn = _get_conn()
    cur = conn.execute(
        "UPDATE calendar_events SET is_done = 1 WHERE id = ?", (event_id,)
    )
    conn.commit()
    return cur.rowcount > 0


def delete_calendar_event(event_id: int) -> bool:
    """Delete a calendar event. Returns True if found."""
    conn = _get_conn()
    cur = conn.execute("DELETE FROM calendar_events WHERE id = ?", (event_id,))
    conn.commit()
    return cur.rowcount > 0


def get_pending_reminders() -> list[dict]:
    """Get events that need reminding now (remind_at <= now, not yet reminded)."""
    conn = _get_conn()
    now = datetime.now().strftime("%Y-%m-%d %H:%M")
    rows = conn.execute(
        "SELECT * FROM calendar_events WHERE remind_at IS NOT NULL AND remind_at <= ? AND reminded = 0 AND is_done = 0",
        (now,),
    ).fetchall()
    return [dict(r) for r in rows]


def mark_reminded(event_id: int):
    """Mark an event's reminder as sent."""
    conn = _get_conn()
    conn.execute("UPDATE calendar_events SET reminded = 1 WHERE id = ?", (event_id,))
    conn.commit()


# ─── Task Queue ──────────────────────────────────────────────────────

def clear_stale_tasks():
    """Cancel any leftover pending/running tasks from a previous session.
    Called once on startup to prevent old commands from auto-executing."""
    conn = _get_conn()
    cur = conn.execute(
        "UPDATE task_queue SET status = 'cancelled', result = 'Cancelled: app restarted' "
        "WHERE status IN ('pending', 'running')"
    )
    conn.commit()
    count = cur.rowcount
    if count > 0:
        log.info(f"Cleared {count} stale task(s) from previous session")


def add_task(command: str, source: str = "assistant") -> int:
    """Queue a PC command. Returns the task ID."""
    conn = _get_conn()
    cur = conn.execute(
        "INSERT INTO task_queue (command, source) VALUES (?, ?)",
        (command, source),
    )
    conn.commit()
    return cur.lastrowid


def get_pending_tasks() -> list[dict]:
    """Get all pending tasks."""
    conn = _get_conn()
    rows = conn.execute(
        "SELECT * FROM task_queue WHERE status = 'pending' ORDER BY id",
    ).fetchall()
    return [dict(r) for r in rows]


def update_task_status(task_id: int, status: str, result: str | None = None):
    """Update a task's status and optionally its result."""
    conn = _get_conn()
    if result is not None:
        conn.execute(
            "UPDATE task_queue SET status = ?, result = ?, executed_at = datetime('now') WHERE id = ?",
            (status, result, task_id),
        )
    else:
        conn.execute(
            "UPDATE task_queue SET status = ? WHERE id = ?",
            (status, task_id),
        )
    conn.commit()


def list_tasks(limit: int = 50) -> list[dict]:
    """List recent tasks, newest first."""
    conn = _get_conn()
    rows = conn.execute(
        "SELECT * FROM task_queue ORDER BY id DESC LIMIT ?", (limit,)
    ).fetchall()
    return [dict(r) for r in rows]
