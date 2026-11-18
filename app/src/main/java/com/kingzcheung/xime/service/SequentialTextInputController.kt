package com.kingzcheung.xime.service

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.annotation.MainThread
import com.kingzcheung.xime.wentao.WentaoTypingPacing
import com.kingzcheung.xime.wentao.WentaoTypingProfile
import com.kingzcheung.xime.wentao.WentaoTypingText
import kotlin.random.Random

data class SequentialInputProgress(
    val committed: Int = 0,
    val total: Int = 0,
    val running: Boolean = false
) {
    val percent: Int get() = if (total <= 0) 0 else (committed * 100 / total).coerceIn(0, 100)
}

/**
 * 把剪贴板文本逐个用户感知字符提交到当前编辑器。
 *
 * 每次提交前重新核对 EditorInfo 与目标快照；输入框切换、连接失效或密码框均不会继续写入。
 * 任务文本、位置和目标身份保存在私有 SharedPreferences，只有同一目标重新获得焦点时才允许恢复。
 */
@MainThread
internal class SequentialTextInputController(
    private val inputConnection: () -> InputConnection?,
    private val currentEditor: () -> EditorInfo?,
    private val checkpoint: SharedPreferences,
    private val onProgress: (committed: Int, total: Int, running: Boolean) -> Unit = { _, _, _ -> },
    private val onFinished: (success: Boolean) -> Unit = {},
) {
    private val handler = Handler(Looper.getMainLooper())
    private var units: List<String> = emptyList()
    private var sourceText = ""
    private var nextIndex = 0
    private var target: EditorTarget? = null
    private var active = false
    private var paused = false

    var profile: WentaoTypingProfile = WentaoTypingProfile.preset(WentaoTypingProfile.Preset.LEVEL_3)
        private set
    var randomIntervals: Boolean = true
        private set

    init {
        val savedSpeed = checkpoint.getLong(KEY_CONFIG_SPEED, profile.charactersPerHour).coerceAtLeast(1L)
        profile = if (checkpoint.getBoolean(KEY_CONFIG_CUSTOM, false)) {
            WentaoTypingProfile.custom(savedSpeed)
        } else {
            WentaoTypingProfile.Preset.entries.firstOrNull { it.charactersPerHour == savedSpeed }
                ?.let(WentaoTypingProfile::preset) ?: WentaoTypingProfile.custom(savedSpeed)
        }
        randomIntervals = checkpoint.getBoolean(KEY_CONFIG_RANDOM, true)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!active || paused) return
            val expected = target
            val editor = currentEditor()
            if (expected == null || editor == null || !expected.matches(editor) || isSensitive(editor.inputType)) {
                pause()
                onFinished(false)
                return
            }
            val connection = inputConnection()
            if (connection == null) {
                pause()
                onFinished(false)
                return
            }
            val unit = units.getOrNull(nextIndex)
            if (unit == null) {
                finish(true)
                return
            }
            if (!connection.commitText(unit, 1)) {
                pause()
                onFinished(false)
                return
            }

            nextIndex++
            saveCheckpoint()
            onProgress(nextIndex, units.size, active && !paused)
            if (nextIndex >= units.size) {
                finish(true)
            } else {
                val jitter = if (randomIntervals) Random.nextDouble(-0.25, 0.25) else 0.0
                val delayNanos = WentaoTypingPacing.nextDelayNanos(unit, profile, jitter)
                val delayMs = ((delayNanos + NANOS_PER_MILLI - 1L) / NANOS_PER_MILLI).coerceAtLeast(1L)
                handler.postDelayed(this, delayMs)
            }
        }
    }

    val isRunning: Boolean get() = active && !paused
    val isPaused: Boolean get() = active && paused
    val committedCount: Int get() = nextIndex
    val totalCount: Int get() = units.size

    fun setProfile(value: WentaoTypingProfile) {
        profile = value
        checkpoint.edit()
            .putLong(KEY_CONFIG_SPEED, value.charactersPerHour)
            .putBoolean(KEY_CONFIG_CUSTOM, value.isCustom)
            .apply()
        saveCheckpoint()
    }

    fun setRandomIntervals(enabled: Boolean) {
        randomIntervals = enabled
        checkpoint.edit().putBoolean(KEY_CONFIG_RANDOM, enabled).apply()
        saveCheckpoint()
    }

    fun start(text: String) {
        stop(notify = false)
        val editor = currentEditor()
        val connection = inputConnection()
        if (text.isEmpty() || text.length > MAX_TEXT_LENGTH || editor == null || connection == null ||
            editor.packageName.isNullOrBlank() || editor.inputType == 0 || isSensitive(editor.inputType)) {
            onProgress(0, 0, false)
            onFinished(false)
            return
        }
        val snapshot = EditorTarget.from(editor)
        if (snapshot == null) {
            onProgress(0, 0, false)
            onFinished(false)
            return
        }
        sourceText = text
        units = WentaoTypingText.splitCharacters(text)
        nextIndex = 0
        target = snapshot
        if (units.isEmpty()) {
            clearCheckpoint()
            onProgress(0, 0, false)
            onFinished(true)
            return
        }
        active = true
        paused = false
        saveCheckpoint()
        onProgress(0, units.size, true)
        handler.post(tick)
    }

    fun pause() {
        if (!active || paused) return
        paused = true
        handler.removeCallbacks(tick)
        saveCheckpoint()
        onProgress(nextIndex, units.size, false)
    }

    fun resume() {
        if (!active || !paused) return
        val expected = target
        val editor = currentEditor()
        if (expected == null || editor == null || !expected.matches(editor) || isSensitive(editor.inputType)) {
            pause()
            onFinished(false)
            return
        }
        paused = false
        saveCheckpoint()
        onProgress(nextIndex, units.size, true)
        handler.post(tick)
    }

    fun stop() {
        stop(notify = true)
    }

    /**
     * Called after InputMethodService.onStartInput. A same-field task remains resumable;
     * a different field invalidates it. A persisted task is restored paused, never auto-typed.
     */
    fun onEditorStarted(editor: EditorInfo?, restarting: Boolean): Boolean {
        if (editor == null || isSensitive(editor.inputType)) {
            if (active) stop()
            else clearCheckpoint()
            return false
        }
        val currentTarget = target
        if (active) {
            // EditorInfo fields can collide across different views in the same app.
            // Keep an in-memory task only when Android explicitly reports a restart
            // of the same editor; a normal new input session always invalidates it.
            if (restarting && currentTarget != null && currentTarget.matches(editor)) {
                return true
            }
            stop()
        }
        return restoreFor(editor)
    }

    private fun restoreFor(editor: EditorInfo): Boolean {
        val text = checkpoint.getString(KEY_TEXT, null).orEmpty()
        val savedTarget = EditorTarget(
            packageName = checkpoint.getString(KEY_PACKAGE, "").orEmpty(),
            fieldId = checkpoint.getInt(KEY_FIELD_ID, 0),
            inputType = checkpoint.getInt(KEY_INPUT_TYPE, 0),
            fieldName = checkpoint.getString(KEY_FIELD_NAME, null),
            imeOptions = checkpoint.getInt(KEY_IME_OPTIONS, 0),
            privateImeOptions = checkpoint.getString(KEY_PRIVATE_IME_OPTIONS, null),
            actionId = checkpoint.getInt(KEY_ACTION_ID, 0),
            actionLabel = checkpoint.getString(KEY_ACTION_LABEL, null),
            hintText = checkpoint.getString(KEY_HINT_TEXT, null),
            label = checkpoint.getString(KEY_LABEL, null)
        )
        val index = checkpoint.getInt(KEY_INDEX, 0)
        val split = if (text.isNotEmpty()) WentaoTypingText.splitCharacters(text) else emptyList()
        if (text.isEmpty() || text.length > MAX_TEXT_LENGTH || split.isEmpty() ||
            index !in 0 until split.size || !savedTarget.isStableForRecovery ||
            !savedTarget.matches(editor)) {
            clearCheckpoint()
            return false
        }
        sourceText = text
        units = split
        nextIndex = index
        target = savedTarget
        val savedSpeed = checkpoint.getLong(KEY_SPEED, 12_000L).coerceAtLeast(1L)
        val savedCustom = checkpoint.getBoolean(KEY_CUSTOM_SPEED, false)
        profile = if (savedCustom) WentaoTypingProfile.custom(savedSpeed) else {
            WentaoTypingProfile.Preset.entries
                .firstOrNull { it.charactersPerHour == savedSpeed }
                ?.let(WentaoTypingProfile::preset)
                ?: WentaoTypingProfile.custom(savedSpeed)
        }
        randomIntervals = checkpoint.getBoolean(KEY_RANDOM, true)
        active = true
        paused = true
        onProgress(nextIndex, units.size, false)
        return true
    }

    private fun saveCheckpoint() {
        val editorTarget = target ?: return
        if (sourceText.isEmpty() || !active) return
        checkpoint.edit()
            .putString(KEY_TEXT, sourceText)
            .putInt(KEY_INDEX, nextIndex)
            .putString(KEY_PACKAGE, editorTarget.packageName)
            .putInt(KEY_FIELD_ID, editorTarget.fieldId)
            .putInt(KEY_INPUT_TYPE, editorTarget.inputType)
            .putString(KEY_FIELD_NAME, editorTarget.fieldName)
            .putInt(KEY_IME_OPTIONS, editorTarget.imeOptions)
            .putString(KEY_PRIVATE_IME_OPTIONS, editorTarget.privateImeOptions)
            .putInt(KEY_ACTION_ID, editorTarget.actionId)
            .putString(KEY_ACTION_LABEL, editorTarget.actionLabel)
            .putString(KEY_HINT_TEXT, editorTarget.hintText)
            .putString(KEY_LABEL, editorTarget.label)
            .putLong(KEY_SPEED, profile.charactersPerHour)
            .putBoolean(KEY_CUSTOM_SPEED, profile.isCustom)
            .putBoolean(KEY_RANDOM, randomIntervals)
            .apply()
    }

    private fun clearCheckpoint() {
        checkpoint.edit().remove(KEY_TEXT).remove(KEY_INDEX)
            .remove(KEY_PACKAGE).remove(KEY_FIELD_ID).remove(KEY_INPUT_TYPE)
            .remove(KEY_FIELD_NAME).remove(KEY_IME_OPTIONS).remove(KEY_PRIVATE_IME_OPTIONS)
            .remove(KEY_ACTION_ID).remove(KEY_ACTION_LABEL).remove(KEY_HINT_TEXT).remove(KEY_LABEL)
            .remove(KEY_SPEED).remove(KEY_CUSTOM_SPEED)
            .remove(KEY_RANDOM).apply()
    }

    private fun stop(notify: Boolean) {
        val hadWork = active
        handler.removeCallbacks(tick)
        active = false
        paused = false
        units = emptyList()
        sourceText = ""
        nextIndex = 0
        target = null
        clearCheckpoint()
        if (notify && hadWork) {
            onProgress(0, 0, false)
            onFinished(false)
        }
    }

    private fun finish(success: Boolean) {
        handler.removeCallbacks(tick)
        active = false
        paused = false
        val total = units.size
        val committed = nextIndex
        units = emptyList()
        sourceText = ""
        nextIndex = 0
        target = null
        clearCheckpoint()
        onProgress(committed, total, false)
        onFinished(success)
    }

    private fun isSensitive(inputType: Int): Boolean {
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return (inputClass == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )) || (inputClass == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }

    private data class EditorTarget(
        val packageName: String,
        val fieldId: Int,
        val inputType: Int,
        val fieldName: String?,
        val imeOptions: Int,
        val privateImeOptions: String?,
        val actionId: Int,
        val actionLabel: String?,
        val hintText: String?,
        val label: String?
    ) {
        // Persistent recovery is refused when Android did not provide a real View ID.
        // A package/type-only match is not enough to prove that this is the same field.
        val isStableForRecovery: Boolean get() = fieldId > 0

        fun matches(editor: EditorInfo): Boolean =
            packageName.isNotBlank() && inputType != 0 &&
                packageName == editor.packageName &&
                fieldId == editor.fieldId &&
                inputType == editor.inputType &&
                fieldName == editor.fieldName &&
                imeOptions == editor.imeOptions &&
                privateImeOptions == editor.privateImeOptions &&
                actionId == editor.actionId &&
                actionLabel == editor.actionLabel?.toString() &&
                hintText == editor.hintText?.toString() &&
                label == editor.label?.toString()

        companion object {
            fun from(editor: EditorInfo): EditorTarget? {
                val packageName = editor.packageName?.takeIf(String::isNotBlank) ?: return null
                if (editor.inputType == 0) return null
                return EditorTarget(
                    packageName = packageName,
                    fieldId = editor.fieldId,
                    inputType = editor.inputType,
                    fieldName = editor.fieldName,
                    imeOptions = editor.imeOptions,
                    privateImeOptions = editor.privateImeOptions,
                    actionId = editor.actionId,
                    actionLabel = editor.actionLabel?.toString(),
                    hintText = editor.hintText?.toString(),
                    label = editor.label?.toString()
                )
            }
        }
    }

    companion object {
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val MAX_TEXT_LENGTH = 262_144
        private const val KEY_TEXT = "text"
        private const val KEY_INDEX = "index"
        private const val KEY_PACKAGE = "package"
        private const val KEY_FIELD_ID = "field_id"
        private const val KEY_INPUT_TYPE = "input_type"
        private const val KEY_FIELD_NAME = "field_name"
        private const val KEY_IME_OPTIONS = "ime_options"
        private const val KEY_PRIVATE_IME_OPTIONS = "private_ime_options"
        private const val KEY_ACTION_ID = "action_id"
        private const val KEY_ACTION_LABEL = "action_label"
        private const val KEY_HINT_TEXT = "hint_text"
        private const val KEY_LABEL = "label"
        private const val KEY_SPEED = "speed"
        private const val KEY_CUSTOM_SPEED = "custom_speed"
        private const val KEY_RANDOM = "random"
        private const val KEY_CONFIG_SPEED = "config_speed"
        private const val KEY_CONFIG_CUSTOM = "config_custom"
        private const val KEY_CONFIG_RANDOM = "config_random"
    }
}
