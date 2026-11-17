package com.kingzcheung.xime.service

import android.os.Handler
import android.os.Looper
import android.view.inputmethod.InputConnection
import androidx.annotation.MainThread

/**
 * Sends clipboard text to the active editor one Unicode code point at a time.
 *
 * All methods are main-thread only. The controller deliberately owns no clipboard access or UI;
 * the IME service supplies the selected text and current InputConnection.
 */
@MainThread
internal class SequentialTextInputController(
    private val inputConnection: () -> InputConnection?,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
    private val onProgress: (committed: Int, total: Int, running: Boolean) -> Unit = { _, _, _ -> },
    private val onFinished: (success: Boolean) -> Unit = {},
) {
    private val handler = Handler(Looper.getMainLooper())
    private var codePoints: IntArray = intArrayOf()
    private var nextIndex = 0
    private var active = false
    private var paused = false

    private val tick = object : Runnable {
        override fun run() {
            if (!active || paused) return
            if (nextIndex >= codePoints.size) {
                finish(true)
                return
            }

            val connection = inputConnection()
            if (connection == null) {
                finish(false)
                return
            }

            val text = String(Character.toChars(codePoints[nextIndex]))
            if (!connection.commitText(text, 1)) {
                finish(success = false)
                return
            }

            nextIndex++
            onProgress(nextIndex, codePoints.size, active && !paused)
            if (nextIndex >= codePoints.size) {
                finish(success = true)
            } else {
                handler.postDelayed(this, intervalMs.coerceAtLeast(20L))
            }
        }
    }

    val isRunning: Boolean get() = active && !paused
    val isPaused: Boolean get() = active && paused
    val committedCount: Int get() = nextIndex
    val totalCount: Int get() = codePoints.size

    fun start(text: String) {
        stop(notify = false)
        codePoints = text.codePoints().toArray()
        nextIndex = 0
        if (codePoints.isEmpty()) {
            onProgress(0, 0, false)
            onFinished(true)
            return
        }
        active = true
        paused = false
        onProgress(0, codePoints.size, true)
        handler.post(tick)
    }

    fun pause() {
        if (!active || paused) return
        paused = true
        handler.removeCallbacks(tick)
        onProgress(nextIndex, codePoints.size, false)
    }

    fun resume() {
        if (!active || !paused) return
        paused = false
        onProgress(nextIndex, codePoints.size, true)
        handler.post(tick)
    }

    fun stop() {
        stop(notify = true)
    }

    private fun stop(notify: Boolean) {
        val hadWork = active
        handler.removeCallbacks(tick)
        active = false
        paused = false
        codePoints = intArrayOf()
        nextIndex = 0
        if (notify && hadWork) {
            onProgress(0, 0, false)
            onFinished(false)
        }
    }

    private fun finish(success: Boolean) {
        handler.removeCallbacks(tick)
        active = false
        paused = false
        val total = codePoints.size
        val committed = nextIndex
        codePoints = intArrayOf()
        nextIndex = 0
        onProgress(committed, total, false)
        onFinished(success)
    }

    companion object {
        const val DEFAULT_INTERVAL_MS = 120L
    }
}
