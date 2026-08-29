package com.example.splitdiff

import com.example.splitdiff.diffui.DiffColors
import com.example.splitdiff.diffui.FoldingManager
import com.example.splitdiff.diffui.PlainTextSyntaxHighlighter
import com.example.splitdiff.engine.InlineDiffCalculator
import com.example.splitdiff.engine.KotlinDiffEngine
import com.example.splitdiff.model.DiffDisplayItem
import com.example.splitdiff.model.DiffLine
import com.example.splitdiff.model.DiffResult
import com.example.splitdiff.model.DiffRow
import com.example.splitdiff.model.DiffRowType
import com.example.splitdiff.model.TextSpan
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.system.measureTimeMillis

class DiffEngineTest {

    private lateinit var engine: KotlinDiffEngine

    @Before
    fun setUp() {
        engine = KotlinDiffEngine()
    }

    @Test
    fun testCase1_Identical() = runTest {
        val oldText = "A\nB\nC"
        val newText = "A\nB\nC"

        val result = engine.calculateDiff(oldText, newText)

        assertEquals(3, result.rows.size)
        assertEquals(0, result.addedCount)
        assertEquals(0, result.deletedCount)
        assertEquals(0, result.modifiedCount)
        assertEquals(3, result.unchangedCount)

        assertTrue(result.rows.all { it.type == DiffRowType.UNCHANGED })
        assertEquals(1, result.rows[0].left?.lineNumber)
        assertEquals(1, result.rows[0].right?.lineNumber)
        assertEquals("A", result.rows[0].left?.content)
        assertEquals("A", result.rows[0].right?.content)
    }

    @Test
    fun testCase2_SingleLineModified() = runTest {
        val oldText = "A\nB\nC"
        val newText = "A\nX\nC"

        val result = engine.calculateDiff(oldText, newText)

        assertEquals(3, result.rows.size)
        assertEquals(DiffRowType.UNCHANGED, result.rows[0].type)
        assertEquals(DiffRowType.MODIFIED, result.rows[1].type)
        assertEquals(DiffRowType.UNCHANGED, result.rows[2].type)

        assertEquals("B", result.rows[1].left?.content)
        assertEquals("X", result.rows[1].right?.content)
        assertEquals(2, result.rows[1].left?.lineNumber)
        assertEquals(2, result.rows[1].right?.lineNumber)
    }

    @Test
    fun testCase3_MiddleInsertion() = runTest {
        val oldText = "A\nB\nC"
        val newText = "A\nB\nX\nC"

        val result = engine.calculateDiff(oldText, newText)

        assertEquals(4, result.rows.size)
        assertEquals("A", result.rows[0].left?.content)
        assertEquals("B", result.rows[1].left?.content)

        // Inserted row
        assertEquals(DiffRowType.INSERTED, result.rows[2].type)
        assertNull(result.rows[2].left)
        assertEquals("X", result.rows[2].right?.content)
        assertEquals(3, result.rows[2].right?.lineNumber)

        // Row after insertion (C is aligned)
        assertEquals(DiffRowType.UNCHANGED, result.rows[3].type)
        assertEquals("C", result.rows[3].left?.content)
        assertEquals("C", result.rows[3].right?.content)
        assertEquals(3, result.rows[3].left?.lineNumber)
        assertEquals(4, result.rows[3].right?.lineNumber)
    }

    @Test
    fun testCase4_MiddleDeletion() = runTest {
        val oldText = "A\nB\nX\nC"
        val newText = "A\nB\nC"

        val result = engine.calculateDiff(oldText, newText)

        assertEquals(4, result.rows.size)
        assertEquals("A", result.rows[0].left?.content)
        assertEquals("B", result.rows[1].left?.content)

        // Deleted row
        assertEquals(DiffRowType.DELETED, result.rows[2].type)
        assertEquals("X", result.rows[2].left?.content)
        assertEquals(3, result.rows[2].left?.lineNumber)
        assertNull(result.rows[2].right)

        // Row after deletion (C is aligned)
        assertEquals(DiffRowType.UNCHANGED, result.rows[3].type)
        assertEquals("C", result.rows[3].left?.content)
        assertEquals("C", result.rows[3].right?.content)
        assertEquals(4, result.rows[3].left?.lineNumber)
        assertEquals(3, result.rows[3].right?.lineNumber)
    }

    @Test
    fun testCase5_MultipleAddAndRemove() = runTest {
        val oldText = "Line1\nLine2\nDeleteMe1\nDeleteMe2\nLine3"
        val newText = "Line1\nLine2\nInsertMe1\nInsertMe2\nInsertMe3\nLine3"

        val result = engine.calculateDiff(oldText, newText)

        assertEquals(6, result.rows.size)
        assertEquals(DiffRowType.UNCHANGED, result.rows[0].type)
        assertEquals(DiffRowType.UNCHANGED, result.rows[1].type)

        // ChangeDelta with 2 source lines and 3 target lines
        assertEquals(DiffRowType.MODIFIED, result.rows[2].type)
        assertEquals("DeleteMe1", result.rows[2].left?.content)
        assertEquals("InsertMe1", result.rows[2].right?.content)

        assertEquals(DiffRowType.MODIFIED, result.rows[3].type)
        assertEquals("DeleteMe2", result.rows[3].left?.content)
        assertEquals("InsertMe2", result.rows[3].right?.content)

        assertEquals(DiffRowType.INSERTED, result.rows[4].type)
        assertNull(result.rows[4].left)
        assertEquals("InsertMe3", result.rows[4].right?.content)

        assertEquals(DiffRowType.UNCHANGED, result.rows[5].type)
        assertEquals("Line3", result.rows[5].left?.content)
        assertEquals("Line3", result.rows[5].right?.content)
    }

    @Test
    fun testCase6_EmptyStrings() = runTest {
        val result1 = engine.calculateDiff("", "")
        assertEquals(0, result1.rows.size)

        val result2 = engine.calculateDiff("A\nB", "")
        assertEquals(2, result2.rows.size)
        assertTrue(result2.rows.all { it.type == DiffRowType.DELETED })

        val result3 = engine.calculateDiff("", "A\nB")
        assertEquals(2, result3.rows.size)
        assertTrue(result3.rows.all { it.type == DiffRowType.INSERTED })
    }

    @Test
    fun testCase7_LargeFilePerformance() = runTest {
        val lineCount = 10000
        val oldLines = (1..lineCount).map { "val item_$it = \"Value_$it\"" }
        val newLines = oldLines.toMutableList().apply {
            this[500] = "val item_501 = \"Modified_Value_501\""
            this.add(2000, "val inserted_item = \"New\"")
            this.removeAt(8000)
        }

        val oldText = oldLines.joinToString("\n")
        val newText = newLines.joinToString("\n")

        val elapsed = measureTimeMillis {
            val result = engine.calculateDiff(oldText, newText)
            assertNotNull(result)
            assertTrue(result.rows.size >= lineCount)
        }

        println("Diff calculation for $lineCount lines took $elapsed ms")
        assertTrue("Large file diff should complete in reasonable time (< 3000ms)", elapsed < 3000)
    }

    @Test
    fun testCase8_LongSingleLineInlineDiff() = runTest {
        val prefix = "A".repeat(1000)
        val suffix = "Z".repeat(1000)
        val oldLine = prefix + "MIDDLE_OLD" + suffix
        val newLine = prefix + "MIDDLE_NEW" + suffix

        val (leftSpans, rightSpans) = InlineDiffCalculator.calculateInlineDiff(oldLine, newLine)

        assertTrue(leftSpans.isNotEmpty())
        assertTrue(rightSpans.isNotEmpty())

        val highlightedLeft = leftSpans.filter { it.isHighlighted }
        val highlightedRight = rightSpans.filter { it.isHighlighted }

        assertEquals("OLD", highlightedLeft.joinToString("") { it.text })
        assertEquals("NEW", highlightedRight.joinToString("") { it.text })
    }

    @Test
    fun testInlineDiffTokenHighlight() {
        val left = "val name = \"Kim\""
        val right = "val name = \"Park\""

        val (leftSpans, rightSpans) = InlineDiffCalculator.calculateInlineDiff(left, right)

        val leftHighlighted = leftSpans.filter { it.isHighlighted }.joinToString("") { it.text }
        val rightHighlighted = rightSpans.filter { it.isHighlighted }.joinToString("") { it.text }

        assertEquals("Kim", leftHighlighted)
        assertEquals("Park", rightHighlighted)
    }

    @Test
    fun testFoldingManager_ThresholdAndToggle() {
        // 10줄의 UNCHANGED 행 생성
        val rows = (1..10).map { i ->
            DiffRow(
                id = i.toLong(),
                left = DiffLine(i, "Line $i"),
                right = DiffLine(i, "Line $i"),
                type = DiffRowType.UNCHANGED
            )
        }
        val diffResult = DiffResult(rows = rows, unchangedCount = 10)

        // Threshold = 5일 때, 10줄 블록은 1개의 FoldedHeader로 접혀야 함
        val itemsCollapsed = FoldingManager.createDisplayItems(
            diffResult = diffResult,
            isFoldingEnabled = true,
            foldingThreshold = 5,
            expandedFoldIds = emptySet()
        )

        assertEquals(1, itemsCollapsed.size)
        assertTrue(itemsCollapsed[0] is DiffDisplayItem.FoldedHeader)
        val header = itemsCollapsed[0] as DiffDisplayItem.FoldedHeader
        assertEquals(10, header.lineCount)
        assertEquals(1, header.startLineLeft)
        assertEquals(10, header.endLineLeft)

        // 접힌 헤더 ID를 expandedFoldIds에 넣으면 10개의 LineRow로 펼쳐져야 함
        val itemsExpanded = FoldingManager.createDisplayItems(
            diffResult = diffResult,
            isFoldingEnabled = true,
            foldingThreshold = 5,
            expandedFoldIds = setOf(header.id)
        )
        assertEquals(10, itemsExpanded.size)
        assertTrue(itemsExpanded.all { it is DiffDisplayItem.LineRow })
    }
}
