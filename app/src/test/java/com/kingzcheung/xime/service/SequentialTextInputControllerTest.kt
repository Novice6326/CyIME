package com.kingzcheung.xime.service

import android.os.Looper
import android.view.inputmethod.InputConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], application = android.app.Application::class)
class SequentialTextInputControllerTest {
    private fun controller(
        connection: InputConnection,
        onFinished: (Boolean) -> Unit = {},
        onProgress: (Int, Int, Boolean) -> Unit = { _, _, _ -> },
    ) = SequentialTextInputController(
        inputConnection = { connection },
        intervalMs = { 20L },
        punctuationPauseMs = { 40L },
        onProgress = onProgress,
        onFinished = onFinished,
    )

    @Test
    fun `emoji flags and combining marks are committed as whole graphemes`() {
        val connection = mock<InputConnection>()
        whenever(connection.commitText(any<CharSequence>(), eq(1))).thenReturn(true)
        val captor = argumentCaptor<CharSequence>()
        val finished = mutableListOf<Boolean>()
        val input = "👨‍👩‍👧‍👦🇺🇸e\u0301"

        controller(connection, onFinished = { finished += it }).start(input)
        shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)

        verify(connection, times(3)).commitText(captor.capture(), eq(1))
        assertEquals(listOf("👨‍👩‍👧‍👦", "🇺🇸", "e\u0301"), captor.allValues.map { it.toString() })
        assertEquals(listOf(true), finished)
    }

    @Test
    fun `pause keeps the committed offset and resume continues without replay`() {
        val connection = mock<InputConnection>()
        whenever(connection.commitText(any<CharSequence>(), eq(1))).thenReturn(true)
        val progress = mutableListOf<Triple<Int, Int, Boolean>>()
        val input = controller(connection, onProgress = { done, total, running ->
            progress += Triple(done, total, running)
        })

        input.start("abcd")
        shadowOf(Looper.getMainLooper()).idle()
        input.pause()
        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)

        verify(connection, times(1)).commitText(any<CharSequence>(), eq(1))
        assertTrue(input.isPaused)
        assertEquals(1, input.committedCount)
        assertEquals(4, input.totalCount)

        input.resume()
        shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)

        verify(connection, times(4)).commitText(any<CharSequence>(), eq(1))
        assertFalse(input.isRunning)
        assertFalse(input.isPaused)
        assertEquals(Triple(1, 4, false), progress[2])
        assertEquals(Triple(1, 4, true), progress[3])
        assertEquals(Triple(4, 4, false), progress.last())
    }

    @Test
    fun `empty clipboard text completes without sending text`() {
        val connection = mock<InputConnection>()
        val finished = mutableListOf<Boolean>()
        val progress = mutableListOf<Triple<Int, Int, Boolean>>()

        controller(connection, { finished += it }) { done, total, running ->
            progress += Triple(done, total, running)
        }.start("")

        verify(connection, times(0)).commitText(any<CharSequence>(), eq(1))
        assertEquals(listOf(true), finished)
        assertEquals(listOf(Triple(0, 0, false)), progress)
    }
}
