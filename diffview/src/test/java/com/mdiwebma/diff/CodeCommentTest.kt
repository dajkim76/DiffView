package com.mdiwebma.diff

import com.mdiwebma.diffview.comment.CodeComment
import com.mdiwebma.diffview.comment.CodeCommentManager
import com.mdiwebma.diffview.comment.FileCommentsPayload
import com.mdiwebma.diffview.comment.LineKey
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CodeCommentTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storageDir: File
    private lateinit var commentManager: CodeCommentManager

    @Before
    fun setUp() {
        storageDir = tempFolder.newFolder("code_comments")
        commentManager = CodeCommentManager(storageDir)
    }

    @Test
    fun testLineKey_SerializationAndDeserialization() {
        val key1 = LineKey(leftLine = 10, rightLine = null)
        assertEquals("10_-1", key1.toKeyString())
        assertEquals(key1, LineKey.fromKeyString("10_-1"))

        val key2 = LineKey(leftLine = null, rightLine = 25)
        assertEquals("-1_25", key2.toKeyString())
        assertEquals(key2, LineKey.fromKeyString("-1_25"))

        val key3 = LineKey(leftLine = 5, rightLine = 6)
        assertEquals("5_6", key3.toKeyString())
        assertEquals(key3, LineKey.fromKeyString("5_6"))
    }

    @Test
    fun testFileCommentsPayload_JsonSerialization() {
        val gson = Gson()
        val payload = FileCommentsPayload(
            commitHash = "abc123456789",
            filePath = "app/src/main/MyClass.kt",
            comments = mutableMapOf(
                "10_-1" to CodeComment("Left line comment", 1000L),
                "-1_20" to CodeComment("Right line comment", 2000L)
            )
        )

        val json = gson.toJson(payload)
        val deserialized = gson.fromJson(json, FileCommentsPayload::class.java)

        assertEquals(payload.commitHash, deserialized.commitHash)
        assertEquals(payload.filePath, deserialized.filePath)
        assertEquals(2, deserialized.comments.size)
        assertEquals("Left line comment", deserialized.comments["10_-1"]?.text)
        assertEquals("Right line comment", deserialized.comments["-1_20"]?.text)
    }

    @Test
    fun testCodeCommentManager_SaveLoadAndDelete() = runTest {
        val commit = "a1b2c3d4e5f6"
        val filePath = "src/main/java/Foo.kt"
        val lineKey = LineKey(leftLine = 42, rightLine = null)

        // 1. Initial load should be empty
        val initialComments = commentManager.loadComments(commit, filePath)
        assertTrue(initialComments.isEmpty())

        // 2. Save a comment
        val saved = commentManager.saveComment(commit, filePath, lineKey, "Review note on line 42")
        assertEquals("Review note on line 42", saved.text)

        // 3. Load again (from cache and from disk)
        val loaded = commentManager.loadComments(commit, filePath)
        assertEquals(1, loaded.size)
        assertEquals("Review note on line 42", loaded[lineKey]?.text)

        val directGet = commentManager.getComment(commit, filePath, lineKey)
        assertNotNull(directGet)
        assertEquals("Review note on line 42", directGet?.text)

        // 4. Update comment
        commentManager.saveComment(commit, filePath, lineKey, "Updated note on line 42")
        val updated = commentManager.loadComments(commit, filePath)
        assertEquals("Updated note on line 42", updated[lineKey]?.text)

        // 5. Delete comment
        commentManager.deleteComment(commit, filePath, lineKey)
        val afterDelete = commentManager.loadComments(commit, filePath)
        assertTrue(afterDelete.isEmpty())
        assertNull(commentManager.getComment(commit, filePath, lineKey))
    }

    @Test
    fun testCodeCommentManager_PersistenceAcrossInstances() = runTest {
        val commit = "deadbeef1234"
        val filePath = "core/Engine.kt"
        val key1 = LineKey(leftLine = 1, rightLine = null)
        val key2 = LineKey(leftLine = null, rightLine = 5)

        // Manager 1 saves comments
        commentManager.saveComment(commit, filePath, key1, "Comment 1")
        commentManager.saveComment(commit, filePath, key2, "Comment 2")

        // Manager 2 (new instance with same filesDir) loads from disk
        val newManager = CodeCommentManager(storageDir)
        val loaded = newManager.loadComments(commit, filePath)

        assertEquals(2, loaded.size)
        assertEquals("Comment 1", loaded[key1]?.text)
        assertEquals("Comment 2", loaded[key2]?.text)
    }

    @Test
    fun testUnchangedLineKey_FallbackAndSync() = runTest {
        val commit = "commit1234"
        val filePath = "src/main/Test.kt"
        val unchangedKey = LineKey(leftLine = 10, rightLine = 10)
        val leftOnlyKey = LineKey(leftLine = 10, rightLine = null)
        val rightOnlyKey = LineKey(leftLine = null, rightLine = 10)

        // 1. Save with unchanged key (e.g. from Unified mode or SideBySide unchanged row)
        commentManager.saveComment(commit, filePath, unchangedKey, "Unchanged line comment")

        // 2. Query via direct get and fallback
        val getDirect = commentManager.getComment(commit, filePath, unchangedKey)
        val getLeft = commentManager.getComment(commit, filePath, leftOnlyKey)
        val getRight = commentManager.getComment(commit, filePath, rightOnlyKey)

        assertEquals("Unchanged line comment", getDirect?.text)
        assertEquals("Unchanged line comment", getLeft?.text)
        assertEquals("Unchanged line comment", getRight?.text)

        // 3. Delete via left-only key cleans up
        commentManager.deleteComment(commit, filePath, leftOnlyKey)
        assertNull(commentManager.getComment(commit, filePath, unchangedKey))
        assertNull(commentManager.getComment(commit, filePath, leftOnlyKey))
        assertNull(commentManager.getComment(commit, filePath, rightOnlyKey))
    }

    @Test
    fun testDiffLongTabAction_EnumValues() {
        val none = com.mdiwebma.diffview.model.DiffLongTabAction.NONE
        val textSelectable = com.mdiwebma.diffview.model.DiffLongTabAction.TEXT_SELECTABLE
        val comment = com.mdiwebma.diffview.model.DiffLongTabAction.COMMENT

        assertEquals("NONE", none.name)
        assertEquals("TEXT_SELECTABLE", textSelectable.name)
        assertEquals("COMMENT", comment.name)
        assertEquals(3, com.mdiwebma.diffview.model.DiffLongTabAction.entries.size)
    }

    @Test
    fun testDiffCommentLabels_DefaultValues() {
        val labels = com.mdiwebma.diffview.comment.DiffCommentLabels.Default
        assertEquals("Add Comment", labels.addTitle)
        assertEquals("Edit Comment", labels.editTitle)
        assertEquals("Delete Comment", labels.deleteTitle)
        assertEquals("Save", labels.actionSave)
        assertEquals("Cancel", labels.actionCancel)
        assertEquals("Delete", labels.actionDelete)
    }

    @Test
    fun testDiffSettingLabels_DefaultValues() {
        val labels = com.mdiwebma.diffview.DiffSettingLabels.Default
        assertEquals("DiffView Settings", labels.dialogTitle)
        assertEquals("Diff Mode", labels.diffModeTitle)
        assertEquals("Side-by-Side", labels.modeSideBySide)
        assertEquals("Unified", labels.modeUnified)
        assertEquals("Theme", labels.themeTitle)
        assertEquals("Auto", labels.themeAuto)
        assertEquals("Light", labels.themeLight)
        assertEquals("Dark", labels.themeDark)
        assertEquals("Code Folding", labels.foldingTitle)
        assertEquals("Close", labels.closeButton)
        assertEquals("Save Visible Viewport Image", labels.menuSaveVisibleImage)
        assertEquals("Save Full Diff Image", labels.menuSaveFullImage)
        assertEquals("Image Saved", labels.imageSavedTitle)
        assertEquals("View", labels.actionView)
        assertEquals("Share", labels.actionShare)
        assertEquals("All code blocks expanded.", labels.expandAllSuccess)
        assertEquals("All code blocks collapsed.", labels.collapseAllSuccess)
    }

    @Test
    fun testDiffThemeMode_EnumValues() {
        val auto = com.mdiwebma.diffview.model.DiffThemeMode.AUTO
        val light = com.mdiwebma.diffview.model.DiffThemeMode.LIGHT
        val dark = com.mdiwebma.diffview.model.DiffThemeMode.DARK

        assertEquals("AUTO", auto.name)
        assertEquals("LIGHT", light.name)
        assertEquals("DARK", dark.name)
        assertEquals(3, com.mdiwebma.diffview.model.DiffThemeMode.entries.size)
    }
}
