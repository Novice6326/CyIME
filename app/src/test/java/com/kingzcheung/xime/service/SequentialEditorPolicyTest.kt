package com.kingzcheung.xime.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SequentialEditorPolicyTest {
    @Test
    fun doesNotKeepTaskForNormalNewInputSessionEvenWhenEditorInfoMatches() {
        assertFalse(
            SequentialEditorPolicy.shouldKeepActiveTask(
                restarting = false,
                editorMatches = true
            )
        )
    }

    @Test
    fun keepsTaskOnlyForMatchingEditorRestart() {
        assertTrue(
            SequentialEditorPolicy.shouldKeepActiveTask(
                restarting = true,
                editorMatches = true
            )
        )
        assertFalse(
            SequentialEditorPolicy.shouldKeepActiveTask(
                restarting = true,
                editorMatches = false
            )
        )
    }

    @Test
    fun restoresCheckpointOnlyForExplicitMatchingEditorRestart() {
        assertTrue(
            SequentialEditorPolicy.shouldRestoreCheckpoint(
                restarting = true,
                fieldIdStable = true,
                editorMatches = true
            )
        )
        assertFalse(
            SequentialEditorPolicy.shouldRestoreCheckpoint(
                restarting = false,
                fieldIdStable = true,
                editorMatches = true
            )
        )
        assertFalse(
            SequentialEditorPolicy.shouldRestoreCheckpoint(
                restarting = true,
                fieldIdStable = false,
                editorMatches = true
            )
        )
        assertFalse(
            SequentialEditorPolicy.shouldRestoreCheckpoint(
                restarting = true,
                fieldIdStable = true,
                editorMatches = false
            )
        )
    }

    @Test
    fun requiresPositiveViewIdForPersistentRecovery() {
        assertFalse(SequentialEditorPolicy.hasStableFieldId(0))
        assertFalse(SequentialEditorPolicy.hasStableFieldId(-1))
        assertTrue(SequentialEditorPolicy.hasStableFieldId(1))
    }
}
