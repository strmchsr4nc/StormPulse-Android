package com.carolinasvrwx.stormpulse

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Android TextToSpeech adapter for route-weather announcements.
 *
 * Construct from an Activity/Service context and call close() when its owner
 * is destroyed. This adapter is deliberately separate from route monitoring
 * so callers can decide when a navigation session is active.
 */
class RouteAlertSpeaker(
    context: Context,
    private val onReady: ((Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private var engine: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var closed = false
    private val pendingMessages = mutableListOf<String>()

    @Synchronized
    override fun onInit(status: Int) {
        if (closed) return
        val tts = engine ?: return
        ready = status == TextToSpeech.SUCCESS &&
            tts.setLanguage(Locale.US) != TextToSpeech.LANG_MISSING_DATA &&
            tts.setLanguage(Locale.US) != TextToSpeech.LANG_NOT_SUPPORTED
        onReady?.invoke(ready)
        if (ready) {
            pendingMessages.toList().forEachIndexed { index, message ->
                speakNow(tts, message, flush = index == 0)
            }
            pendingMessages.clear()
        } else {
            pendingMessages.clear()
        }
    }

    /** Speaks messages in order, flushing older speech for the first urgent alert. */
    @Synchronized
    fun speak(messages: List<String>) {
        if (closed || messages.isEmpty()) return
        val clean = messages.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return
        val tts = engine
        if (!ready || tts == null) {
            pendingMessages.clear()
            pendingMessages.addAll(clean.take(3))
            return
        }
        clean.take(3).forEachIndexed { index, message ->
            speakNow(tts, message, flush = index == 0)
        }
    }

    private fun speakNow(tts: TextToSpeech, message: String, flush: Boolean) {
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts.speak(message, queueMode, null, "stormpulse-route-alert-${System.nanoTime()}")
    }

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        pendingMessages.clear()
        ready = false
        engine?.stop()
        engine?.shutdown()
        engine = null
    }
}
