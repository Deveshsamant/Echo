"""
Echo AI Agent — Telegram Bot Interface
Accepts commands via Telegram chat, routes to AI Brain, returns responses.
"""

import asyncio
import logging
import os
import threading

from telegram import Update
from telegram.ext import (
    Application,
    CommandHandler,
    MessageHandler,
    ContextTypes,
    filters,
)

import config
from tools import take_screenshot, get_system_info

log = logging.getLogger("echo.telegram")


class TelegramBot:
    """Telegram bot that interfaces with Echo AI Brain."""

    def __init__(self, on_command=None):
        """
        Args:
            on_command: callback function(text: str) → str, processes a command and returns response.
        """
        self.on_command = on_command
        self._thread = None
        self._loop = None
        self._app = None
        self._running = False

        # Parse allowed user IDs
        self._allowed_users = set()
        if config.TELEGRAM_ALLOWED_USERS:
            for uid in config.TELEGRAM_ALLOWED_USERS.split(","):
                uid = uid.strip()
                if uid.isdigit():
                    self._allowed_users.add(int(uid))

    def _is_authorized(self, user_id: int) -> bool:
        """Check if a user is authorized."""
        if not self._allowed_users:
            return True  # No restrictions
        return user_id in self._allowed_users

    async def _start_cmd(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle /start command."""
        if not self._is_authorized(update.effective_user.id):
            await update.message.reply_text("⛔ Unauthorized.")
            return

        await update.message.reply_text(
            f"🤖 **{config.AGENT_NAME}** is online.\n\n"
            "Send me any command and I'll execute it on your PC.\n\n"
            "**Commands:**\n"
            "/screenshot — Get a screenshot\n"
            "/status — System info\n"
            "/reset — Reset conversation\n"
            "/help — Show this message\n\n"
            "Or just type naturally: _\"Open Chrome\"_, _\"What time is it?\"_",
            parse_mode="Markdown",
        )
        log.info(f"Telegram /start from user {update.effective_user.id}")

    async def _help_cmd(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle /help command."""
        await self._start_cmd(update, context)

    async def _screenshot_cmd(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle /screenshot command — takes and sends a screenshot."""
        if not self._is_authorized(update.effective_user.id):
            await update.message.reply_text("⛔ Unauthorized.")
            return

        await update.message.reply_text("📸 Taking screenshot...")
        try:
            result = take_screenshot()
            # Extract file path from result string
            path = result.replace("Screenshot saved to: ", "").strip()
            if os.path.exists(path):
                with open(path, "rb") as photo:
                    await update.message.reply_photo(photo=photo, caption="🖥️ Current screen")
                # Cleanup
                try:
                    os.remove(path)
                except OSError:
                    pass
            else:
                await update.message.reply_text(f"❌ Screenshot failed: {result}")
        except Exception as e:
            await update.message.reply_text(f"❌ Error: {e}")

    async def _status_cmd(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle /status command — returns system info."""
        if not self._is_authorized(update.effective_user.id):
            await update.message.reply_text("⛔ Unauthorized.")
            return

        try:
            info = get_system_info()
            await update.message.reply_text(f"💻 **System Status:**\n```\n{info}\n```", parse_mode="Markdown")
        except Exception as e:
            await update.message.reply_text(f"❌ Error: {e}")

    async def _reset_cmd(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle /reset command — resets conversation."""
        if not self._is_authorized(update.effective_user.id):
            await update.message.reply_text("⛔ Unauthorized.")
            return

        # Signal brain reset (handled by main.py)
        if self.on_command:
            self.on_command("/reset")
        await update.message.reply_text("🔄 Conversation reset.")

    async def _handle_message(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        """Handle any text message as a command."""
        if not self._is_authorized(update.effective_user.id):
            await update.message.reply_text("⛔ Unauthorized.")
            return

        text = update.message.text.strip()
        if not text:
            return

        log.info(f"Telegram message from {update.effective_user.id}: {text}")

        # Send typing action
        await context.bot.send_chat_action(chat_id=update.effective_chat.id, action="typing")

        if self.on_command:
            try:
                # Run the AI command (blocking, but in thread via run_in_executor)
                loop = asyncio.get_event_loop()
                response = await loop.run_in_executor(None, lambda: self.on_command(text, source="telegram"))
                if response:
                    # Telegram message limit is 4096 chars
                    if len(response) > 4000:
                        for i in range(0, len(response), 4000):
                            await update.message.reply_text(response[i:i+4000])
                    else:
                        await update.message.reply_text(response)
                else:
                    await update.message.reply_text("✅ Done.")
            except Exception as e:
                log.error(f"Telegram command error: {e}")
                await update.message.reply_text(f"❌ Error: {e}")
        else:
            await update.message.reply_text("⚙️ Brain not connected.")

    def start(self):
        """Start the Telegram bot in a background thread."""
        if not config.TELEGRAM_BOT_TOKEN:
            log.warning("Telegram bot token not configured. Telegram interface disabled.")
            log.warning("Set TELEGRAM_BOT_TOKEN in config.py or as an environment variable.")
            return

        self._running = True
        self._thread = threading.Thread(target=self._run_bot, daemon=True)
        self._thread.start()
        log.info("Telegram bot starting...")

    def _run_bot(self):
        """Run the bot event loop in a thread."""
        try:
            self._loop = asyncio.new_event_loop()
            asyncio.set_event_loop(self._loop)

            self._app = (
                Application.builder()
                .token(config.TELEGRAM_BOT_TOKEN)
                .build()
            )

            # Register handlers
            self._app.add_handler(CommandHandler("start", self._start_cmd))
            self._app.add_handler(CommandHandler("help", self._help_cmd))
            self._app.add_handler(CommandHandler("screenshot", self._screenshot_cmd))
            self._app.add_handler(CommandHandler("status", self._status_cmd))
            self._app.add_handler(CommandHandler("reset", self._reset_cmd))
            self._app.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, self._handle_message))

            async def _error_handler(update: object, context: ContextTypes.DEFAULT_TYPE):
                log.warning(f"Telegram API warning/error: {type(context.error).__name__} - {context.error}")

            self._app.add_error_handler(_error_handler)

            log.info("Telegram bot is online!")
            self._app.run_polling(drop_pending_updates=True)

        except Exception as e:
            log.error(f"Telegram bot error: {e}")

    def stop(self):
        """Stop the Telegram bot."""
        self._running = False
        if self._app:
            try:
                self._app.stop()
            except Exception:
                pass
