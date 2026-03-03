"""
Echo AI Agent — Calendar Reminder Scheduler
Background thread that checks for pending reminders, triggers notifications,
and AUTO-EXECUTES calendar event titles as PC commands.
"""

import logging
import threading
import time
from datetime import datetime

import database as db

log = logging.getLogger("echo.scheduler")

# Optional references
_voice_engine = None
_brain = None  # Reference to EchoBrain for auto-executing calendar tasks


def set_voice_engine(voice):
    """Set the voice engine reference for spoken reminders."""
    global _voice_engine
    _voice_engine = voice


def set_brain(brain):
    """Set the brain reference for auto-executing calendar tasks."""
    global _brain
    _brain = brain


def _check_reminders():
    """Check for pending reminders, fire notifications, and auto-execute commands."""
    try:
        pending = db.get_pending_reminders()
        for event in pending:
            title = event["title"]
            desc = event.get("description", "")
            event_time = event.get("event_time", "")

            log.info(f"Reminder firing: {title}")

            # Windows toast notification
            try:
                from tools import show_notification
                time_str = f" at {event_time}" if event_time else ""
                show_notification(
                    f"🔔 Reminder: {title}",
                    f"{desc}{time_str}" if desc else f"You have a task{time_str}",
                )
            except Exception as e:
                log.error(f"Notification failed: {e}")

            # Voice announcement
            if _voice_engine and not _voice_engine.is_muted:
                try:
                    _voice_engine.speak(f"Reminder: {title}")
                except Exception:
                    pass

            # AUTO-EXECUTE: Send the title as a command to the brain
            if _brain:
                try:
                    log.info(f"Auto-executing calendar task: {title}")
                    # Use the title as the PC command
                    command_text = title
                    # If there's a description, it might contain more specific instructions
                    if desc:
                        command_text = f"{title}: {desc}"

                    result = _brain.chat(command_text)
                    log.info(f"Calendar task result: {result[:150]}")

                    # Show completion notification
                    try:
                        from tools import show_notification
                        show_notification(
                            f"✅ Task Done: {title}",
                            result[:200] if result else "Completed",
                        )
                    except Exception:
                        pass
                except Exception as e:
                    log.error(f"Calendar auto-execute failed for '{title}': {e}")
                    try:
                        from tools import show_notification
                        show_notification(
                            f"❌ Task Failed: {title}",
                            str(e)[:200],
                        )
                    except Exception:
                        pass

            # Mark as reminded (and effectively done since we executed it)
            db.mark_reminded(event["id"])
            # Also mark the event as completed since we executed the task
            try:
                db.complete_calendar_event(event["id"])
            except Exception:
                pass

    except Exception as e:
        log.error(f"Reminder check failed: {e}")


def _scheduler_loop():
    """Background loop that checks reminders every 30 seconds."""
    log.info("Reminder scheduler started (checking every 30s)")
    while True:
        _check_reminders()
        time.sleep(30)


def start_scheduler():
    """Start the reminder scheduler in a background thread."""
    thread = threading.Thread(target=_scheduler_loop, daemon=True)
    thread.start()
    log.info("Reminder scheduler thread started")
