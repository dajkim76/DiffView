package com.mdiwebma.diff

import com.mdiwebma.diffview.FoldingManager
import com.mdiwebma.diffview.HorizontalScrollSyncGroup
import com.mdiwebma.diffview.engine.InlineDiffCalculator
import com.mdiwebma.diffview.engine.KotlinDiffEngine
import com.mdiwebma.diffview.model.DiffDisplayItem
import com.mdiwebma.diffview.model.DiffLine
import com.mdiwebma.diffview.model.DiffMode
import com.mdiwebma.diffview.model.DiffResult
import com.mdiwebma.diffview.model.DiffRow
import com.mdiwebma.diffview.model.DiffRowType
import com.mdiwebma.diffview.model.WhitespaceIgnoreMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // =========================================================================
    // 1. Basic Diff Calculation Tests
    // =========================================================================

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

        assertEquals(DiffRowType.INSERTED, result.rows[2].type)
        assertNull(result.rows[2].left)
        assertEquals("X", result.rows[2].right?.content)
        assertEquals(3, result.rows[2].right?.lineNumber)

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

        assertEquals(DiffRowType.DELETED, result.rows[2].type)
        assertEquals("X", result.rows[2].left?.content)
        assertEquals(3, result.rows[2].left?.lineNumber)
        assertNull(result.rows[2].right)

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

    // =========================================================================
    // 2. Inline Diff Option Tests (enableInlineDiff: true vs false)
    // =========================================================================

    @Test
    fun testEnableInlineDiff_TrueVsFalse() = runTest {
        val oldLine = "val count = 10"
        val newLine = "val count = 20"

        // enableInlineDiff = true
        val resultWithInline = engine.calculateDiff(oldLine, newLine, enableInlineDiff = true)
        val modifiedRowWithInline = resultWithInline.rows[0]
        val leftSpansWithInline = modifiedRowWithInline.left?.spans ?: emptyList()
        val rightSpansWithInline = modifiedRowWithInline.right?.spans ?: emptyList()

        assertTrue(leftSpansWithInline.any { it.isHighlighted })
        assertTrue(rightSpansWithInline.any { it.isHighlighted })
        assertEquals("1", leftSpansWithInline.filter { it.isHighlighted }.joinToString("") { it.text })
        assertEquals("2", rightSpansWithInline.filter { it.isHighlighted }.joinToString("") { it.text })

        // enableInlineDiff = false
        val resultWithoutInline = engine.calculateDiff(oldLine, newLine, enableInlineDiff = false)
        val modifiedRowWithoutInline = resultWithoutInline.rows[0]
        val leftSpansWithoutInline = modifiedRowWithoutInline.left?.spans ?: emptyList()
        val rightSpansWithoutInline = modifiedRowWithoutInline.right?.spans ?: emptyList()

        assertEquals(1, leftSpansWithoutInline.size)
        assertEquals(1, rightSpansWithoutInline.size)
        assertFalse(leftSpansWithoutInline[0].isHighlighted)
        assertFalse(rightSpansWithoutInline[0].isHighlighted)
        assertEquals(oldLine, leftSpansWithoutInline[0].text)
        assertEquals(newLine, rightSpansWithoutInline[0].text)
    }

    @Test
    fun testCase8_LongSingleLineInlineDiff() = runTest {
        val prefix = "A".repeat(100)
        val suffix = "Z".repeat(100)
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
    fun testInlineDiff_EdgeCases() {
        // 1. Identical strings
        val (lSame, rSame) = InlineDiffCalculator.calculateInlineDiff("same text", "same text")
        assertEquals(1, lSame.size)
        assertEquals(1, rSame.size)
        assertFalse(lSame[0].isHighlighted)
        assertFalse(rSame[0].isHighlighted)

        // 2. Empty left
        val (lEmpty, rFromEmpty) = InlineDiffCalculator.calculateInlineDiff("", "added text")
        assertTrue(lEmpty.isEmpty())
        assertEquals(1, rFromEmpty.size)
        assertTrue(rFromEmpty[0].isHighlighted)
        assertEquals("added text", rFromEmpty[0].text)

        // 3. Empty right
        val (lToEmpty, rEmpty) = InlineDiffCalculator.calculateInlineDiff("deleted text", "")
        assertEquals(1, lToEmpty.size)
        assertTrue(lToEmpty[0].isHighlighted)
        assertEquals("deleted text", lToEmpty[0].text)
        assertTrue(rEmpty.isEmpty())
    }

    // =========================================================================
    // 3. WhitespaceIgnoreMode Tests
    // =========================================================================

    @Test
    fun testWhitespace_None() = runTest {
        val oldText = "   val x = 1   \nval y   =   2"
        val newText = "val x = 1\nval y = 2"

        val result = engine.calculateDiff(
            oldText = oldText,
            newText = newText,
            whitespaceMode = WhitespaceIgnoreMode.NONE
        )

        assertEquals(2, result.rows.size)
        assertEquals(2, result.modifiedCount)
        assertEquals(DiffRowType.MODIFIED, result.rows[0].type)
        assertEquals(DiffRowType.MODIFIED, result.rows[1].type)
    }

    @Test
    fun testWhitespace_TrimLeadingTrailing() = runTest {
        val oldText = "   val x = 1   \nval y   =   2"
        val newText = "val x = 1\nval y = 2"

        val result = engine.calculateDiff(
            oldText = oldText,
            newText = newText,
            whitespaceMode = WhitespaceIgnoreMode.TRIM_LEADING_TRAILING
        )

        assertEquals(2, result.rows.size)
        assertEquals(DiffRowType.UNCHANGED, result.rows[0].type)
        assertEquals(DiffRowType.MODIFIED, result.rows[1].type)
    }

    @Test
    fun testWhitespace_CollapseWhitespace() = runTest {
        val oldText = "   val   x   =   1   \nval y = \"a b\""
        val newText = "val x = 1\nval y = \"a   b\""

        val result = engine.calculateDiff(
            oldText = oldText,
            newText = newText,
            whitespaceMode = WhitespaceIgnoreMode.COLLAPSE_WHITESPACE
        )

        assertEquals(2, result.rows.size)
        assertEquals(DiffRowType.UNCHANGED, result.rows[0].type)
        assertEquals(DiffRowType.UNCHANGED, result.rows[1].type)
    }

    @Test
    fun testWhitespace_IgnoreAll() = runTest {
        val oldText = "v a l   x = 1"
        val newText = "val x = 1"

        val result = engine.calculateDiff(
            oldText = oldText,
            newText = newText,
            whitespaceMode = WhitespaceIgnoreMode.IGNORE_ALL
        )

        assertEquals(1, result.rows.size)
        assertEquals(DiffRowType.UNCHANGED, result.rows[0].type)
    }

    // =========================================================================
    // 4. FoldingManager Tests (Options: isFoldingEnabled, contextLines, threshold, expandedFoldIds)
    // =========================================================================

    @Test
    fun testFolding_Disabled() {
        val rows = (1..20).map { i ->
            DiffRow(i.toLong(), DiffLine(i, "L$i"), DiffLine(i, "R$i"), DiffRowType.UNCHANGED)
        }
        val diffResult = DiffResult(rows = rows, unchangedCount = 20)

        val items = FoldingManager.createDisplayItems(
            diffResult = diffResult,
            mode = DiffMode.SIDE_BY_SIDE,
            isFoldingEnabled = false
        )

        assertEquals(20, items.size)
        assertTrue(items.all { it is DiffDisplayItem.SideBySideRow })
    }

    @Test
    fun testFolding_ContextLinesVariation() {
        val rows = mutableListOf<DiffRow>()
        for (i in 1..20) {
            rows.add(DiffRow(i.toLong(), DiffLine(i, "L$i"), DiffLine(i, "R$i"), DiffRowType.UNCHANGED))
        }
        rows.add(DiffRow(21L, DiffLine(21, "ModOld"), DiffLine(21, "ModNew"), DiffRowType.MODIFIED))
        for (i in 22..41) {
            rows.add(DiffRow(i.toLong(), DiffLine(i, "L$i"), DiffLine(i, "R$i"), DiffRowType.UNCHANGED))
        }
        val diffResult = DiffResult(rows = rows)

        val items = FoldingManager.createDisplayItems(
            diffResult = diffResult,
            mode = DiffMode.SIDE_BY_SIDE,
            isFoldingEnabled = true,
            contextLines = 2,
            foldingThreshold = 6,
            expandedFoldIds = emptySet()
        )

        // Block 1 (start of file, 20 lines): FoldedHeader(18) + bottom context(2 lines: 19, 20) = 3 items
        // Row 21 (MODIFIED) = 1 item
        // Block 2 (end of file, 20 lines): top context(2 lines: 22, 23) + FoldedHeader(18) = 3 items
        // Total = 1 + 2 + 1 + 2 + 1 = 7 items
        assertEquals(7, items.size)
        assertTrue(items[0] is DiffDisplayItem.FoldedHeader)
        assertEquals(18, (items[0] as DiffDisplayItem.FoldedHeader).lineCount)
        assertTrue(items[1] is DiffDisplayItem.SideBySideRow) // Line 19
        assertTrue(items[2] is DiffDisplayItem.SideBySideRow) // Line 20
        assertTrue(items[3] is DiffDisplayItem.SideBySideRow) // Line 21 (Modified)
        assertTrue(items[4] is DiffDisplayItem.SideBySideRow) // Line 22
        assertTrue(items[5] is DiffDisplayItem.SideBySideRow) // Line 23
        assertTrue(items[6] is DiffDisplayItem.FoldedHeader)
        assertEquals(18, (items[6] as DiffDisplayItem.FoldedHeader).lineCount)
    }

    @Test
    fun testFolding_ThresholdVariation() {
        val rows = (1..6).map { i ->
            DiffRow(i.toLong(), DiffLine(i, "L$i"), DiffLine(i, "R$i"), DiffRowType.UNCHANGED)
        }.toMutableList()
        rows.add(DiffRow(7L, DiffLine(7, "Mod"), DiffLine(7, "Mod"), DiffRowType.MODIFIED))
        val diffResult = DiffResult(rows = rows)

        val items = FoldingManager.createDisplayItems(
            diffResult = diffResult,
            mode = DiffMode.SIDE_BY_SIDE,
            isFoldingEnabled = true,
            contextLines = 2,
            foldingThreshold = 10
        )

        assertEquals(7, items.size)
        assertTrue(items.all { it is DiffDisplayItem.SideBySideRow })
    }

    @Test
    fun testFolding_ExpandToggleAndCollapse() {
        val rows = (1..30).map { i ->
            DiffRow(i.toLong(), DiffLine(i, "L$i"), DiffLine(i, "R$i"), DiffRowType.UNCHANGED)
        }.toMutableList()
        rows.add(DiffRow(31L, DiffLine(31, "Mod1"), DiffLine(31, "Mod2"), DiffRowType.MODIFIED))
        val diffResult = DiffResult(rows = rows)

        val collapsedItems = FoldingManager.createDisplayItems(
            diffResult = diffResult,
            mode = DiffMode.SIDE_BY_SIDE,
            isFoldingEnabled = true,
            contextLines = 3,
            foldingThreshold = 8,
            expandedFoldIds = emptySet()
        )

        val header = collapsedItems.filterIsInstance<DiffDisplayItem.FoldedHeader>().first()
        assertEquals(27, header.lineCount)

        val expandedItems = FoldingManager.createDisplayItems(
            diffResult = diffResult,
            mode = DiffMode.SIDE_BY_SIDE,
            isFoldingEnabled = true,
            contextLines = 3,
            foldingThreshold = 8,
            expandedFoldIds = setOf(header.id)
        )

        assertEquals(31, expandedItems.size)
        assertTrue(expandedItems.all { it is DiffDisplayItem.SideBySideRow })
    }

    // =========================================================================
    // 5. DiffMode Tests (Side-by-Side vs Unified)
    // =========================================================================

    @Test
    fun testUnifiedModeTransformation() = runTest {
        val oldText = "A\nB\nC"
        val newText = "A\nX\nC"

        val result = engine.calculateDiff(oldText, newText)

        val unifiedItems = FoldingManager.createDisplayItems(
            diffResult = result,
            mode = DiffMode.UNIFIED,
            isFoldingEnabled = false
        )

        assertEquals(4, unifiedItems.size)
        assertTrue(unifiedItems.all { it is DiffDisplayItem.UnifiedRow })

        val r0 = unifiedItems[0] as DiffDisplayItem.UnifiedRow
        assertEquals(1, r0.oldLineNumber)
        assertEquals(1, r0.newLineNumber)
        assertEquals("A", r0.content)
        assertEquals(" ", r0.prefix)

        val r1 = unifiedItems[1] as DiffDisplayItem.UnifiedRow
        assertEquals(2, r1.oldLineNumber)
        assertNull(r1.newLineNumber)
        assertEquals("B", r1.content)
        assertEquals("-", r1.prefix)

        val r2 = unifiedItems[2] as DiffDisplayItem.UnifiedRow
        assertNull(r2.oldLineNumber)
        assertEquals(2, r2.newLineNumber)
        assertEquals("X", r2.content)
        assertEquals("+", r2.prefix)

        val r3 = unifiedItems[3] as DiffDisplayItem.UnifiedRow
        assertEquals(3, r3.oldLineNumber)
        assertEquals(3, r3.newLineNumber)
        assertEquals("C", r3.content)
        assertEquals(" ", r3.prefix)
    }

    // =========================================================================
    // 6. Horizontal Scroll Synchronization Group Tests
    // =========================================================================

    @Test
    fun testScrollSyncGroup_MaxContentWidthAndReset() {
        val syncGroup = HorizontalScrollSyncGroup()
        assertEquals(0, syncGroup.maxContentWidth)
        assertEquals(0, syncGroup.currentScrollX)

        syncGroup.reportContentWidth(500)
        assertEquals(500, syncGroup.maxContentWidth)

        syncGroup.reportContentWidth(300)
        assertEquals(500, syncGroup.maxContentWidth)

        syncGroup.reportContentWidth(800)
        assertEquals(800, syncGroup.maxContentWidth)

        syncGroup.reset()
        assertEquals(0, syncGroup.maxContentWidth)
        assertEquals(0, syncGroup.currentScrollX)
    }
}
