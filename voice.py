"""
Echo AI Agent — Voice Interface
Speech recognition (input) + Text-to-Speech (output).
"""

import logging
import threading
import queue
import time

import pyttsx3
import speech_recognition as sr

import config

log = logging.getLogger("echo.voice")


class VoiceEngine:
    """Handles speech recognition and text-to-speech."""

    def __init__(self, on_command=None):
        """
        Args:
            on_command: callback function(text: str) → str, called when a voice command is received.
                        Should return the response text to speak aloud.
        """
        self.on_command = on_command
        self._running = False
        self._muted = False
        self._speak_queue = queue.Queue()
        self._recognizer = sr.Recognizer()
        self._recognizer.energy_threshold = 300
        self._recognizer.dynamic_energy_threshold = True
        self._recognizer.pause_threshold = 1.0

        # TTS engine (initialized in thread)
        self._tts_engine = None
        self._tts_thread = threading.Thread(target=self._tts_worker, daemon=True)
        self._tts_thread.start()

        # Listening thread
        self._listen_thread = None

    def _init_tts(self):
        """Initialize TTS engine."""
        engine = pyttsx3.init()
        engine.setProperty("rate", config.TTS_RATE)
        engine.setProperty("volume", config.TTS_VOLUME)

        # Try to pick a good voice
        voices = engine.getProperty("voices")
        for v in voices:
            if "david" in v.name.lower() or "male" in v.name.lower():
                engine.setProperty("voice", v.id)
                break
        return engine

    def _tts_worker(self):
        """Background worker that processes TTS queue."""
        self._tts_engine = self._init_tts()
        while True:
            text = self._speak_queue.get()
            if text is None:
                break
            if self._muted:
                continue
            try:
                self._tts_engine.say(text)
                self._tts_engine.runAndWait()
            except Exception as e:
                log.error(f"TTS error: {e}")
                # Reinitialize TTS engine on error
                try:
                    self._tts_engine = self._init_tts()
                except Exception:
                    pass

    def speak(self, text: str):
        """Queue text to be spoken aloud."""
        if text:
            log.info(f"Speaking: {text[:100]}")
            self._speak_queue.put(text)

    def mute(self):
        self._muted = True

    def unmute(self):
        self._muted = False

    @property
    def is_muted(self):
        return self._muted

    def start_listening(self):
        """Start the continuous voice listening loop in a background thread."""
        if self._listen_thread and self._listen_thread.is_alive():
            return
        self._running = True
        self._listen_thread = threading.Thread(target=self._listen_loop, daemon=True)
        self._listen_thread.start()
        log.info("Voice listening started.")

    def stop_listening(self):
        """Stop the voice listening loop."""
        self._running = False

    def _listen_loop(self):
        """Continuously listen for the wake word, then enter conversation mode."""
        mic = None
        try:
            mic = sr.Microphone()
        except Exception as e:
            log.error(f"Microphone not available: {e}")
            log.info("Voice input disabled. Use Telegram or system tray instead.")
            return

        log.info(f"Listening for wake word: '{config.WAKE_WORD}'...")

        with mic as source:
            self._recognizer.adjust_for_ambient_noise(source, duration=1)

        exit_phrases = {"stop", "goodbye", "bye", "that's all", "thats all",
                        "thanks echo", "thank you echo", "exit", "quit", "done"}

        while self._running:
            try:
                with mic as source:
                    audio = self._recognizer.listen(source, timeout=5, phrase_time_limit=15)

                try:
                    text = self._recognizer.recognize_google(audio).lower().strip()
                    log.info(f"Heard: {text}")
                except sr.UnknownValueError:
                    continue
                except sr.RequestError as e:
                    log.warning(f"Speech recognition service error: {e}")
                    time.sleep(2)
                    continue

                # Check for wake word
                wake = config.WAKE_WORD.lower()
                if text.startswith(wake):
                    command = text[len(wake):].strip().lstrip(",").strip()
                    if not command:
                        self.speak("Yes?")
                        try:
                            with mic as source:
                                follow_audio = self._recognizer.listen(source, timeout=8, phrase_time_limit=20)
                            command = self._recognizer.recognize_google(follow_audio).strip()
                        except (sr.UnknownValueError, sr.WaitTimeoutError):
                            continue
                        except sr.RequestError:
                            continue

                    if command and self.on_command:
                        log.info(f"Command: {command}")
                        self.speak("On it.")
                        try:
                            response = self.on_command(command)
                            if response:
                                self.speak(response)
                        except Exception as e:
                            log.error(f"Command error: {e}")
                            self.speak(f"Sorry, I ran into an error.")

                        # ─── Enter Conversation Mode ─────────────
                        self.speak("I'm listening for follow-ups.")
                        log.info("Entering conversation mode...")
                        conv_timeout = getattr(config, 'VOICE_CONVERSATION_TIMEOUT', 8)
                        silence_count = 0

                        while self._running and silence_count < 2:
                            try:
                                with mic as source:
                                    conv_audio = self._recognizer.listen(
                                        source, timeout=conv_timeout, phrase_time_limit=20
                                    )
                                try:
                                    follow_text = self._recognizer.recognize_google(conv_audio).strip()
                                    log.info(f"Conversation: {follow_text}")
                                    silence_count = 0  # Reset on speech

                                    # Check for exit phrases
                                    if follow_text.lower() in exit_phrases:
                                        self.speak("Okay, talk to you later.")
                                        log.info("Exiting conversation mode (user exit).")
                                        break

                                    # Process the follow-up command
                                    if self.on_command:
                                        try:
                                            response = self.on_command(follow_text)
                                            if response:
                                                self.speak(response)
                                        except Exception as e:
                                            log.error(f"Conv command error: {e}")
                                            self.speak("Sorry, error on that one.")

                                except sr.UnknownValueError:
                                    silence_count += 1
                                    continue
                                except sr.RequestError:
                                    silence_count += 1
                                    continue

                            except sr.WaitTimeoutError:
                                silence_count += 1
                                continue
                            except Exception as e:
                                log.error(f"Conv listen error: {e}")
                                break

                        log.info("Conversation mode ended. Listening for wake word...")

            except sr.WaitTimeoutError:
                continue
            except Exception as e:
                log.error(f"Listen error: {e}")
                time.sleep(1)

    def shutdown(self):
        """Clean shutdown."""
        self._running = False
        self._speak_queue.put(None)  # Signal TTS worker to stop
