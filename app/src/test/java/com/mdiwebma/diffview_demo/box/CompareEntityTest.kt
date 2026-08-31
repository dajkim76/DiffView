package com.mdiwebma.diffview_demo.box

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CompareEntityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dbDir: File

    @Before
    fun setUp() {
        dbDir = tempFolder.newFolder("objectbox_entity_test")
        AppBoxStore.close()
    }

    @After
    fun tearDown() {
        AppBoxStore.close()
    }

    @Test
    fun testCompareEntity_titleGetterDefault() {
        val store = MyObjectBox.builder()
            .directory(dbDir)
            .build()

        val box = store.boxFor(CompareEntity::class.java)

        // 1. Without specifying title (null/empty passed to constructor)
        val entity1 = CompareEntity(
            beforeText = "before",
            afterText = "after"
        )
        val id1 = box.put(entity1)

        val fetched1 = box.get(id1)
        assertNotNull(fetched1)
        assertEquals("No Title ^^", fetched1.title)

        // 2. With empty title -> should fallback to "No Title ^^"
        val entity2 = CompareEntity(
            title = "",
            beforeText = "before2",
            afterText = "after2"
        )
        val id2 = box.put(entity2)

        val fetched2 = box.get(id2)
        assertEquals("No Title ^^", fetched2.title)

        // 3. With explicit title
        val entity3 = CompareEntity(
            title = "My Custom Compare",
            beforeText = "before3",
            afterText = "after3"
        )
        val id3 = box.put(entity3)

        val fetched3 = box.get(id3)
        assertEquals("My Custom Compare", fetched3.title)

        store.close()
    }
}
