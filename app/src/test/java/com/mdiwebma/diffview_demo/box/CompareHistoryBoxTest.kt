package com.mdiwebma.diffview_demo.box

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CompareHistoryBoxTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dbDir: File

    @Before
    fun setUp() {
        dbDir = tempFolder.newFolder("objectbox_app_test")
        AppBoxStore.close()
    }

    @After
    fun tearDown() {
        AppBoxStore.close()
    }

    @Test
    fun testCompareHistoryEntity_CRUD() {
        val store = MyObjectBox.builder()
            .directory(dbDir)
            .build()

        val box = store.boxFor(CompareHistoryEntity::class.java)
        assertTrue(box.isEmpty)

        val history = CompareHistoryEntity(
            commitUrl = "https://github.com/dajkim76/DiffView/commit/abc1234",
            owner = "dajkim76",
            repo = "DiffView",
            commitSha = "abc1234",
            commitMessage = "Initial commit",
            author = "daejeong"
        )
        val id = box.put(history)
        assertTrue(id > 0)

        val fetched = box.get(id)
        assertNotNull(fetched)
        assertEquals("dajkim76", fetched.owner)
        assertEquals("DiffView", fetched.repo)
        assertEquals("abc1234", fetched.commitSha)

        // Update
        fetched.selectedFileName = "MainActivity.kt"
        fetched.isFavorite = true
        box.put(fetched)

        val updated = box.get(id)
        assertEquals("MainActivity.kt", updated.selectedFileName)
        assertTrue(updated.isFavorite)

        // Query by commitSha
        val queryResult = box.query(CompareHistoryEntity_.commitSha.equal("abc1234")).build().find()
        assertEquals(1, queryResult.size)
        assertEquals(id, queryResult[0].id)

        // Delete
        box.remove(id)
        assertTrue(box.isEmpty)

        store.close()
    }
}
