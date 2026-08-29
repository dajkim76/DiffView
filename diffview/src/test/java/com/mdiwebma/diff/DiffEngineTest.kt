package com.mdiwebma.diff

import com.mdiwebma.diffview.FoldingManager
import com.mdiwebma.diffview.HorizontalScrollSyncGroup
import com.mdiwebma.diffview.engine.InlineDiffCalculator
import com.mdiwebma.diffview.engine.KotlinDiffEngine
import com.mdiwebma.diffview.model.DiffDisplayItem
import com.mdiwebma.diffview.model.DiffGranularity
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

        // enableInlineDiff = true (default: DiffGranularity.WORD)
        val resultWithInline = engine.calculateDiff(oldLine, newLine, enableInlineDiff = true)
        val modifiedRowWithInline = resultWithInline.rows[0]
        val leftSpansWithInline = modifiedRowWithInline.left?.spans ?: emptyList()
        val rightSpansWithInline = modifiedRowWithInline.right?.spans ?: emptyList()

        assertTrue(leftSpansWithInline.any { it.isHighlighted })
        assertTrue(rightSpansWithInline.any { it.isHighlighted })
        assertEquals("10", leftSpansWithInline.filter { it.isHighlighted }.joinToString("") { it.text })
        assertEquals("20", rightSpansWithInline.filter { it.isHighlighted }.joinToString("") { it.text })

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
    fun testDiffGranularity_WordVsCharacter() = runTest {
        val left = "val count = 10"
        val right = "val count = 20"

        // 1. WORD unit (default) -> entire word token "10" and "20" are highlighted
        val (leftWordSpans, rightWordSpans) = InlineDiffCalculator.calculateInlineDiff(
            left = left,
            right = right,
            granularity = DiffGranularity.WORD
        )
        val leftWordHigh = leftWordSpans.filter { it.isHighlighted }.joinToString("") { it.text }
        val rightWordHigh = rightWordSpans.filter { it.isHighlighted }.joinToString("") { it.text }
        assertEquals("10", leftWordHigh)
        assertEquals("20", rightWordHigh)

        // 2. CHARACTER unit -> only differing char "1" and "2" are highlighted ("0" is common)
        val (leftCharSpans, rightCharSpans) = InlineDiffCalculator.calculateInlineDiff(
            left = left,
            right = right,
            granularity = DiffGranularity.CHARACTER
        )
        val leftCharHigh = leftCharSpans.filter { it.isHighlighted }.joinToString("") { it.text }
        val rightCharHigh = rightCharSpans.filter { it.isHighlighted }.joinToString("") { it.text }
        assertEquals("1", leftCharHigh)
        assertEquals("2", rightCharHigh)
    }

    @Test
    fun testDiffGranularity_KoreanWords() {
        val left = "안녕하세요 좋은 아침입니다"
        val right = "안녕하세요 활기찬 아침입니다"

        val (leftSpans, rightSpans) = InlineDiffCalculator.calculateInlineDiff(left, right, DiffGranularity.WORD)
        val leftHigh = leftSpans.filter { it.isHighlighted }.joinToString("") { it.text }
        val rightHigh = rightSpans.filter { it.isHighlighted }.joinToString("") { it.text }

        assertEquals("좋은", leftHigh)
        assertEquals("활기찬", rightHigh)
    }

    @Test
    fun testCase8_LongSingleLineInlineDiff() = runTest {
        val prefix = "A".repeat(100)
        val suffix = "Z".repeat(100)
        val oldLine = prefix + "MIDDLE_OLD" + suffix
        val newLine = prefix + "MIDDLE_NEW" + suffix

        val (leftSpans, rightSpans) = InlineDiffCalculator.calculateInlineDiff(
            left = oldLine,
            right = newLine,
            granularity = DiffGranularity.CHARACTER
        )

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

    // =========================================================================
    // 7. KotlinDiffEngine Edge Case Tests (identified during review)
    // =========================================================================

    @Test
    fun testLineNumbersCorrectAfterInsertion() = runTest {
        // Verify that line numbers remain correct in the modified side
        // after rows have been inserted.
        val oldText = "A\nC"
        val newText = "A\nB\nC"

        val result = engine.calculateDiff(oldText, newText)

        // A: UNCHANGED at line 1/1
        assertEquals(1, result.rows[0].left?.lineNumber)
        assertEquals(1, result.rows[0].right?.lineNumber)
        assertEquals(DiffRowType.UNCHANGED, result.rows[0].type)

        // B: INSERTED at right line 2 (no left line)
        assertNull(result.rows[1].left)
        assertEquals(2, result.rows[1].right?.lineNumber)
        assertEquals(DiffRowType.INSERTED, result.rows[1].type)

        // C: UNCHANGED at old line 2, new line 3
        assertEquals(2, result.rows[2].left?.lineNumber)
        assertEquals(3, result.rows[2].right?.lineNumber)
        assertEquals(DiffRowType.UNCHANGED, result.rows[2].type)
    }

    @Test
    fun testLineNumbersCorrectAfterDeletion() = runTest {
        // Verify that line numbers on the modified side stay consecutive
        // even when lines are deleted from the original.
        val oldText = "A\nB\nC"
        val newText = "A\nC"

        val result = engine.calculateDiff(oldText, newText)

        assertEquals(3, result.rows.size)
        // A: UNCHANGED
        assertEquals(1, result.rows[0].left?.lineNumber)
        assertEquals(1, result.rows[0].right?.lineNumber)
        // B: DELETED from left (old line 2), no right
        assertEquals(2, result.rows[1].left?.lineNumber)
        assertNull(result.rows[1].right)
        assertEquals(DiffRowType.DELETED, result.rows[1].type)
        // C: UNCHANGED — left line 3, right line 2
        assertEquals(3, result.rows[2].left?.lineNumber)
        assertEquals(2, result.rows[2].right?.lineNumber)
    }

    @Test
    fun testRowIdIsUniqueAndMonotonicallyIncreasing() = runTest {
        val oldText = "A\nB\nC\nD\nE"
        val newText = "A\nX\nC\nY\nE"

        val result = engine.calculateDiff(oldText, newText)

        val ids = result.rows.map { it.id }
        assertEquals(ids.distinct(), ids)
        assertEquals(ids.sorted(), ids)
    }

    @Test
    fun testWindowsLineEndings_CRLF() = runTest {
        // CRLF should be treated the same as LF
        val oldText = "A\r\nB\r\nC"
        val newText = "A\nB\nC"

        val result = engine.calculateDiff(oldText, newText)

        assertEquals(3, result.rows.size)
        assertTrue(result.rows.all { it.type == DiffRowType.UNCHANGED })
    }

    @Test
    fun testOldCR_LineEndings() = runTest {
        // Classic Mac CR-only line endings should also be normalized
        val oldText = "A\rB\rC"
        val newText = "A\nB\nC"

        val result = engine.calculateDiff(oldText, newText)

        assertEquals(3, result.rows.size)
        assertTrue(result.rows.all { it.type == DiffRowType.UNCHANGED })
    }

    @Test
    fun testCountersMatchRows() = runTest {
        val oldText = "A\nB\nC\nD"
        val newText = "A\nX\nC\nE\nF"

        val result = engine.calculateDiff(oldText, newText)

        // Verify that addedCount + deletedCount + modifiedCount + unchangedCount == rows.size
        // (note: MODIFIED row counts as 1 in modifiedCount but may expand in Unified mode)
        val sumFromCounters = result.addedCount + result.deletedCount + result.modifiedCount + result.unchangedCount
        assertEquals(result.rows.size, sumFromCounters)

        // Individually verify by counting
        assertEquals(result.rows.count { it.type == DiffRowType.UNCHANGED }, result.unchangedCount)
        assertEquals(result.rows.count { it.type == DiffRowType.MODIFIED }, result.modifiedCount)
        assertEquals(result.rows.count { it.type == DiffRowType.INSERTED }, result.addedCount)
        assertEquals(result.rows.count { it.type == DiffRowType.DELETED }, result.deletedCount)
    }

    @Test
    fun testWhitespace_OriginalTextPreservedInContent() = runTest {
        // Even when whitespace is ignored for diff calculation, the displayed
        // content must be the ORIGINAL (unmodified) text, not the normalized text.
        val oldText = "   val x = 1"   // leading whitespace
        val newText = "val x = 1"

        val result = engine.calculateDiff(
            oldText = oldText,
            newText = newText,
            whitespaceMode = WhitespaceIgnoreMode.TRIM_LEADING_TRAILING
        )

        assertEquals(DiffRowType.UNCHANGED, result.rows[0].type)
        // Content must be the original raw text — NOT trimmed
        assertEquals("   val x = 1", result.rows[0].left?.content)
        assertEquals("val x = 1", result.rows[0].right?.content)
    }

    @Test
    fun testWhitespace_MultipleBlocks() = runTest {
        // Mixed scenario: some lines differ only by whitespace (become UNCHANGED),
        // others differ in content (remain MODIFIED).
        val oldText = "   val a = 1\nval b = \"changed\"\n   val c = 3"
        val newText = "val a = 1\nval b = \"other\"\nval c = 3"

        val result = engine.calculateDiff(
            oldText = oldText,
            newText = newText,
            whitespaceMode = WhitespaceIgnoreMode.TRIM_LEADING_TRAILING
        )

        assertEquals(3, result.rows.size)
        assertEquals(DiffRowType.UNCHANGED, result.rows[0].type)
        assertEquals(DiffRowType.MODIFIED, result.rows[1].type)
        assertEquals(DiffRowType.UNCHANGED, result.rows[2].type)
    }

    // =========================================================================
    // 8. InlineDiffCalculator Performance Guard Tests
    // =========================================================================

    @Test
    fun testInlineDiff_LongLineFallbackToFastPath() {
        // Lines > 2000 chars in CHARACTER mode should use prefix/suffix fast path (not O(n*m) LCS)
        val prefix = "X".repeat(1500)
        val suffix = "Y".repeat(1500)
        val leftLine = prefix + "OLD" + suffix
        val rightLine = prefix + "NEW" + suffix

        val elapsedMs = measureTimeMillis {
            val (leftSpans, rightSpans) = InlineDiffCalculator.calculateInlineDiff(
                left = leftLine,
                right = rightLine,
                granularity = DiffGranularity.CHARACTER
            )
            assertEquals("OLD", leftSpans.filter { it.isHighlighted }.joinToString("") { it.text })
            assertEquals("NEW", rightSpans.filter { it.isHighlighted }.joinToString("") { it.text })
        }

        println("Long line inline diff (3003 chars each) took $elapsedMs ms")
        assertTrue("Fast path for long lines should complete in < 50ms", elapsedMs < 50)
    }

    @Test
    fun testInlineDiff_WordTokenFallbackToFastPath() {
        // Token count > 1000 in WORD mode should use token prefix/suffix fast path
        val prefix = (1..600).joinToString(" ") { "token$it" }
        val suffix = (1..600).joinToString(" ") { "end$it" }
        val leftLine = "$prefix oldToken $suffix"
        val rightLine = "$prefix newToken $suffix"

        val elapsedMs = measureTimeMillis {
            val (leftSpans, rightSpans) = InlineDiffCalculator.calculateInlineDiff(
                left = leftLine,
                right = rightLine,
                granularity = DiffGranularity.WORD
            )
            assertEquals("oldToken", leftSpans.filter { it.isHighlighted }.joinToString("") { it.text })
            assertEquals("newToken", rightSpans.filter { it.isHighlighted }.joinToString("") { it.text })
        }

        println("Long token line inline diff took $elapsedMs ms")
        assertTrue("Fast path for long token lists should complete in < 50ms", elapsedMs < 50)
    }

    @Test
    fun testInlineDiff_BoundaryExact2000Chars() {
        // Exactly at the 2000-char threshold — LCS path used
        val left = "A".repeat(2000)
        val right = "B".repeat(2000)

        val elapsedMs = measureTimeMillis {
            val (leftSpans, rightSpans) = InlineDiffCalculator.calculateInlineDiff(
                left = left,
                right = right,
                granularity = DiffGranularity.CHARACTER
            )
            // Entire line is highlighted since nothing matches
            assertEquals(left, leftSpans.filter { it.isHighlighted }.joinToString("") { it.text })
            assertEquals(right, rightSpans.filter { it.isHighlighted }.joinToString("") { it.text })
        }

        println("LCS diff at 2000 chars took $elapsedMs ms")
    }

    @Test
    fun testInlineDiff_BoundaryJustOver2000Chars() {
        // 2001 chars — falls back to fast path
        val left = "A".repeat(2001)
        val right = "B".repeat(2001)

        val elapsedMs = measureTimeMillis {
            val (leftSpans, rightSpans) = InlineDiffCalculator.calculateInlineDiff(
                left = left,
                right = right,
                granularity = DiffGranularity.CHARACTER
            )
            // Fast path: common prefix=0, common suffix=0 → entire line highlighted
            assertTrue(leftSpans.all { it.isHighlighted })
            assertTrue(rightSpans.all { it.isHighlighted })
        }

        println("Fast path inline diff at 2001 chars took $elapsedMs ms")
        assertTrue("Fast path should be much faster than LCS for large strings", elapsedMs < 50)
    }

    // =========================================================================
    // 9. FoldingManager Additional Coverage Tests
    // =========================================================================

    @Test
    fun testFolding_AllUnchanged_NeverFolds() {
        // A file with zero changes should never show a FoldedHeader,
        // regardless of block length.
        val rows = (1..100).map { i ->
            DiffRow(i.toLong(), DiffLine(i, "L$i"), DiffLine(i, "R$i"), DiffRowType.UNCHANGED)
        }
        val diffResult = DiffResult(rows = rows, unchangedCount = 100)

        val items = FoldingManager.createDisplayItems(
            diffResult = diffResult,
            mode = DiffMode.SIDE_BY_SIDE,
            isFoldingEnabled = true,
            contextLines = 3,
            foldingThreshold = 8
        )

        // No FoldedHeaders for an all-unchanged file
        assertTrue(
            "All-unchanged file should not produce any FoldedHeaders",
            items.none { it is DiffDisplayItem.FoldedHeader }
        )
        assertEquals(100, items.size)
    }

    @Test
    fun testFolding_MultipleChangedBlocks_MultipleHeaders() {
        // Two separate changed blocks surrounded by long unchanged blocks.
        val rows = mutableListOf<DiffRow>()
        // 15 unchanged
        for (i in 1..15) rows.add(DiffRow(i.toLong(), DiffLine(i, "L$i"), DiffLine(i, "R$i"), DiffRowType.UNCHANGED))
        // 1 modified
        rows.add(DiffRow(16L, DiffLine(16, "Old1"), DiffLine(16, "New1"), DiffRowType.MODIFIED))
        // 15 unchanged
        for (i in 17..31) rows.add(DiffRow(i.toLong(), DiffLine(i, "L$i"), DiffLine(i, "R$i"), DiffRowType.UNCHANGED))
        // 1 modified
        rows.add(DiffRow(32L, DiffLine(32, "Old2"), DiffLine(32, "New2"), DiffRowType.MODIFIED))
        // 15 unchanged
        for (i in 33..47) rows.add(DiffRow(i.toLong(), DiffLine(i, "L$i"), DiffLine(i, "R$i"), DiffRowType.UNCHANGED))

        val diffResult = DiffResult(rows = rows)

        val items = FoldingManager.createDisplayItems(
            diffResult = diffResult,
            mode = DiffMode.SIDE_BY_SIDE,
            isFoldingEnabled = true,
            contextLines = 3,
            foldingThreshold = 8,
            expandedFoldIds = emptySet()
        )

        val headers = items.filterIsInstance<DiffDisplayItem.FoldedHeader>()
        // Expect: before first change (start fold), between changes (middle fold), after last change (end fold)
        assertEquals(3, headers.size)

        // Each header's lineCount should account for the hidden lines
        // Start block (15 lines): hide 15-3=12 lines
        assertEquals(12, headers[0].lineCount)
        // Middle block (15 lines, between two changes): hide 15-3-3=9 lines
        assertEquals(9, headers[1].lineCount)
        // End block (15 lines): hide 15-3=12 lines
        assertEquals(12, headers[2].lineCount)
    }

    @Test
    fun testFolding_UnifiedMode_ModifiedRowExpandsToTwoRows() {
        // In UNIFIED mode, a MODIFIED DiffRow must produce two UnifiedRows: one DELETED, one INSERTED
        val rows = listOf(
            DiffRow(0L, DiffLine(1, "old line"), DiffLine(1, "new line"), DiffRowType.MODIFIED)
        )
        val diffResult = DiffResult(rows = rows, modifiedCount = 1)

        val items = FoldingManager.createDisplayItems(
            diffResult = diffResult,
            mode = DiffMode.UNIFIED,
            isFoldingEnabled = false
        )

        assertEquals(2, items.size)
        val deleted = items[0] as DiffDisplayItem.UnifiedRow
        val inserted = items[1] as DiffDisplayItem.UnifiedRow

        assertEquals(DiffRowType.DELETED, deleted.type)
        assertEquals("-", deleted.prefix)
        assertEquals(1, deleted.oldLineNumber)
        assertNull(deleted.newLineNumber)
        assertEquals("old line", deleted.content)

        assertEquals(DiffRowType.INSERTED, inserted.type)
        assertEquals("+", inserted.prefix)
        assertNull(inserted.oldLineNumber)
        assertEquals(1, inserted.newLineNumber)
        assertEquals("new line", inserted.content)
    }

    @Test
    fun testFolding_NullDiffResult_ReturnsEmpty() {
        val items = FoldingManager.createDisplayItems(
            diffResult = null,
            mode = DiffMode.SIDE_BY_SIDE,
            isFoldingEnabled = true
        )
        assertTrue(items.isEmpty())
    }

    @Test
    fun testFolding_EmptyDiffResult_ReturnsEmpty() {
        val items = FoldingManager.createDisplayItems(
            diffResult = DiffResult(emptyList()),
            mode = DiffMode.SIDE_BY_SIDE,
            isFoldingEnabled = true
        )
        assertTrue(items.isEmpty())
    }

    @Test
    fun testFolding_FoldedHeaderLineRange_IsAccurate() {
        // Verify that the line number range stored in FoldedHeader
        // matches the actual hidden rows.
        val rows = (1..20).map { i ->
            DiffRow(i.toLong(), DiffLine(i, "L$i"), DiffLine(i, "R$i"), DiffRowType.UNCHANGED)
        }.toMutableList()
        rows.add(DiffRow(21L, DiffLine(21, "Mod"), DiffLine(21, "Mod2"), DiffRowType.MODIFIED))
        val diffResult = DiffResult(rows = rows)

        val items = FoldingManager.createDisplayItems(
            diffResult = diffResult,
            mode = DiffMode.SIDE_BY_SIDE,
            isFoldingEnabled = true,
            contextLines = 3,
            foldingThreshold = 8,
            expandedFoldIds = emptySet()
        )

        val header = items.filterIsInstance<DiffDisplayItem.FoldedHeader>().first()
        // Hidden rows: lines 1..17 (20 - 3 context = 17 hidden)
        assertEquals(1, header.startLineLeft)
        assertEquals(17, header.endLineLeft)
        assertEquals(17, header.lineCount)
    }

    // =========================================================================
    // 10. WhitespaceIgnoreMode.normalize() Unit Tests
    // =========================================================================

    @Test
    fun testWhitespaceModeNormalize_None() {
        val mode = WhitespaceIgnoreMode.NONE
        assertEquals("  hello  ", mode.normalize("  hello  "))
        assertEquals("a  b", mode.normalize("a  b"))
    }

    @Test
    fun testWhitespaceModeNormalize_Trim() {
        val mode = WhitespaceIgnoreMode.TRIM_LEADING_TRAILING
        assertEquals("hello", mode.normalize("  hello  "))
        assertEquals("a  b", mode.normalize("a  b")) // middle spaces preserved
        assertEquals("", mode.normalize("   "))
    }

    @Test
    fun testWhitespaceModeNormalize_Collapse() {
        val mode = WhitespaceIgnoreMode.COLLAPSE_WHITESPACE
        assertEquals("a b c", mode.normalize("a   b   c"))
        assertEquals("hello world", mode.normalize("  hello   world  "))
        assertEquals("", mode.normalize("   "))
    }

    @Test
    fun testWhitespaceModeNormalize_IgnoreAll() {
        val mode = WhitespaceIgnoreMode.IGNORE_ALL
        assertEquals("abc", mode.normalize("a b c"))
        assertEquals("valx=1", mode.normalize("val x = 1"))
        assertEquals("", mode.normalize("   "))
    }

    @Test
    fun testWhitespaceModeAreEqual_Symmetry() {
        // areEqual must be symmetric: areEqual(a, b) == areEqual(b, a)
        val a = "  val x = 1  "
        val b = "val x = 1"
        for (mode in WhitespaceIgnoreMode.entries) {
            assertEquals(
                "Mode $mode: areEqual should be symmetric",
                mode.areEqual(a, b),
                mode.areEqual(b, a)
            )
        }
    }

    // =========================================================================
    // 11. SyntaxHighlighter & LineWrap Unit Tests
    // =========================================================================

    @Test
    fun testPlainTextSyntaxHighlighter_Invocation() {
        val spans = listOf(
            com.mdiwebma.diffview.model.TextSpan("val x = ", isHighlighted = false),
            com.mdiwebma.diffview.model.TextSpan("10", isHighlighted = true)
        )
        val result = com.mdiwebma.diffview.PlainTextSyntaxHighlighter.highlight(
            spans = spans,
            defaultTextColor = 0xFF000000.toInt(),
            highlightBgColor = 0xFFFF0000.toInt(),
            isDark = false
        )
        assertNotNull(result)
    }

    @Test
    fun testDefaultKotlinSyntaxHighlighter_Invocation() {
        val spans = listOf(
            com.mdiwebma.diffview.model.TextSpan("fun calculate(): Int = 42", isHighlighted = false)
        )
        val result = com.mdiwebma.diffview.KotlinSyntaxHighlighter().highlight(
            spans = spans,
            defaultTextColor = 0xFF000000.toInt(),
            highlightBgColor = 0,
            isDark = false
        )
        assertNotNull(result)
    }

    // =========================================================================
    // 12. DiffLabels Customization Tests
    // =========================================================================

    @Test
    fun testDiffLabels_DefaultValuesAndCustomFormatter() {
        val defaultLabels = com.mdiwebma.diffview.DiffLabels.Default
        assertEquals("Original", defaultLabels.originalHeader)
        assertEquals("Modified", defaultLabels.modifiedHeader)
        assertEquals("Unified Changes (+ / -)", defaultLabels.unifiedHeader)
        assertEquals("Old", defaultLabels.oldGutterHeader)
        assertEquals("New", defaultLabels.newGutterHeader)

        val banner = defaultLabels.foldedBannerFormatter(15, "L1~L15", "R1~R15")
        assertTrue(banner.contains("15 unchanged lines"))
        assertTrue(banner.contains("L1~L15"))

        val customLabels = com.mdiwebma.diffview.DiffLabels(
            originalHeader = "이전 코드",
            modifiedHeader = "이후 코드",
            unifiedHeader = "통합 변경",
            foldedBannerFormatter = { count, left, right -> "접힘: ${count}줄 ($left ~ $right)" }
        )
        assertEquals("이전 코드", customLabels.originalHeader)
        assertEquals("접힘: 10줄 (L1~L10 ~ R1~R10)", customLabels.foldedBannerFormatter(10, "L1~L10", "R1~R10"))
    }

    @Test
    fun testKotlinSampleOutput() = runTest {
        val orig = """
package com.example.splitdiff

import java.util.Date

class UserProfile(
    val id: Long,
    val name: String,
    val age: Int,
    val city: String = "Seoul"
) {
    fun printInfo() {
        println("User: ${'$'}name, Age: ${'$'}age")
        println("Created at: ${'$'}{Date()}")
    }

    fun helper1() = 1
    fun helper2() = 2
    fun helper3() = 3
    fun helper4() = 4
    fun helper5() = 5
    fun helper6() = 6
    fun helper7() = 7
    fun helper8() = 8
    fun helper9() = 9
    fun helper10() = 10

    fun calculateDiscount(price: Double): Double {
        val discountRate = 0.10
        val finalPrice = price * (1.0 - discountRate)
        println("Old discount logic")
        return finalPrice
    }
}
        """.trimIndent()

        val mod = """
package com.example.splitdiff

import java.util.Date
import java.time.Instant

data class UserProfile(
    val id: Long,
    val name: String,
    val age: Int,
    val email: String? = null,
    val city: String = "Jeju"
) {
    fun printInfo() {
        println("User: ${'$'}name, Age: ${'$'}age, Email: ${'$'}email")
        println("Created at: ${'$'}{Instant.now()}")
    }

    fun helper1() = 1
    fun helper2() = 2
    fun helper3() = 3
    fun helper4() = 4
    fun helper5() = 5
    fun helper6() = 6
    fun helper7() = 7
    fun helper8() = 8
    fun helper9() = 9
    fun helper10() = 10

    fun calculateDiscount(price: Double, isVip: Boolean = false): Double {
        val discountRate = if (isVip) 0.20 else 0.10
        val finalPrice = price * (1.0 - discountRate)
        return finalPrice
    }
}
        """.trimIndent()

        val diff = engine.calculateDiff(orig, mod, enableInlineDiff = true)
        println("--- DIFF ROWS ---")
        diff.rows.forEachIndexed { i, row ->
            val lNum = row.left?.lineNumber?.toString() ?: "  "
            val rNum = row.right?.lineNumber?.toString() ?: "  "
            val lText = row.left?.content ?: ""
            val rText = row.right?.content ?: ""
            println("Row ${i + 1} [${row.type}] L$lNum: '$lText' | R$rNum: '$rText'")
        }
    }

    @Test
    fun testFormatWrappedText_LeadingSpacesReplacedWithNbsp() {
        val input = "        println(\"Hello\")"
        val formatted = com.mdiwebma.diffview.formatWrappedText(input)
        assertEquals("\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0println(\"Hello\")", formatted.toString())

        val noIndent = "val x = 10"
        assertEquals("val x = 10", com.mdiwebma.diffview.formatWrappedText(noIndent).toString())
    }

    // =========================================================================
    // 9. Comprehensive Edge Cases Tests
    // =========================================================================

    @Test
    fun testEdgeCase_EmptyStrings() = runTest {
        // 1) Empty vs Empty
        val emptyBoth = engine.calculateDiff("", "")
        assertEquals(0, emptyBoth.rows.size)
        assertFalse(emptyBoth.hasChanges)

        // 2) Empty vs Non-empty (All Inserted)
        val emptyToNew = engine.calculateDiff("", "Line1\nLine2")
        assertEquals(2, emptyToNew.rows.size)
        assertEquals(2, emptyToNew.addedCount)
        assertTrue(emptyToNew.rows.all { it.type == DiffRowType.INSERTED })
        assertNull(emptyToNew.rows[0].left)
        assertEquals("Line1", emptyToNew.rows[0].right?.content)

        // 3) Non-empty vs Empty (All Deleted)
        val oldToEmpty = engine.calculateDiff("Line1\nLine2", "")
        assertEquals(2, oldToEmpty.rows.size)
        assertEquals(2, oldToEmpty.deletedCount)
        assertTrue(oldToEmpty.rows.all { it.type == DiffRowType.DELETED })
        assertEquals("Line1", oldToEmpty.rows[0].left?.content)
        assertNull(oldToEmpty.rows[0].right)
    }

    @Test
    fun testEdgeCase_MixedNewlines() = runTest {
        // CRLF (\r\n), LF (\n), and legacy CR (\r) mixed in both texts
        val oldText = "Line1\r\nLine2\nLine3\rLine4"
        val newText = "Line1\nLine2\r\nLine3\nLine4"

        val result = engine.calculateDiff(oldText, newText)
        assertEquals(4, result.rows.size)
        assertTrue(result.rows.all { it.type == DiffRowType.UNCHANGED })
        assertFalse(result.hasChanges)
        assertEquals("Line1", result.rows[0].left?.content)
        assertEquals("Line4", result.rows[3].right?.content)
    }

    @Test
    fun testEdgeCase_ConsecutiveEmptyLines() = runTest {
        val oldText = "\n\n\n" // 4 lines (3 empty line breaks)
        val newText = "\n\n"   // 3 lines

        val result = engine.calculateDiff(oldText, newText)
        assertEquals(4, result.rows.size)
        assertEquals(1, result.deletedCount)
        assertEquals(3, result.unchangedCount)
    }

    @Test
    fun testEdgeCase_EmojiAndSurrogatePairs() = runTest {
        // 1) Line-level diff with emojis
        val oldText = "Kotlin 🚀 1.9\nAndroid 🤖 Studio"
        val newText = "Kotlin 🛸 2.0\nAndroid 🤖 Studio"

        val result = engine.calculateDiff(oldText, newText, enableInlineDiff = true)
        assertEquals(2, result.rows.size)
        assertEquals(DiffRowType.MODIFIED, result.rows[0].type)
        assertEquals(DiffRowType.UNCHANGED, result.rows[1].type)

        // 2) Word-level inline diff with complex emojis / surrogate pairs
        val (leftWordSpans, rightWordSpans) = InlineDiffCalculator.calculateInlineDiff(
            left = "Status: 👨‍👩‍👧‍👦 Family, Code: 🚀",
            right = "Status: 👨‍👩‍👦 Family, Code: 🛸",
            granularity = DiffGranularity.WORD
        )
        // Verify reconstructed text matches original without corrupting surrogate pairs
        val leftReconstructed = leftWordSpans.joinToString("") { it.text }
        val rightReconstructed = rightWordSpans.joinToString("") { it.text }
        assertEquals("Status: 👨‍👩‍👧‍👦 Family, Code: 🚀", leftReconstructed)
        assertEquals("Status: 👨‍👩‍👦 Family, Code: 🛸", rightReconstructed)

        // Verify highlight spans exist
        assertTrue(leftWordSpans.any { it.isHighlighted })
        assertTrue(rightWordSpans.any { it.isHighlighted })

        // 3) Character-level inline diff with multi-byte Korean and Emojis
        val (leftCharSpans, rightCharSpans) = InlineDiffCalculator.calculateInlineDiff(
            left = "가나다🚀라바",
            right = "가나다🛸라바",
            granularity = DiffGranularity.CHARACTER
        )
        assertEquals("가나다🚀라바", leftCharSpans.joinToString("") { it.text })
        assertEquals("가나다🛸라바", rightCharSpans.joinToString("") { it.text })
    }

    @Test
    fun testEdgeCase_VeryLongSingleLine_DiffAndFolding() = runTest {
        // Very long line (5000 chars)
        val prefix = "val massiveString = \"" + "A".repeat(2500)
        val oldMiddle = "OLD_TOKEN"
        val newMiddle = "NEW_TOKEN"
        val suffix = "B".repeat(2500) + "\""

        val oldLongLine = prefix + oldMiddle + suffix
        val newLongLine = prefix + newMiddle + suffix

        val (leftSpans, rightSpans) = InlineDiffCalculator.calculateInlineDiff(
            left = oldLongLine,
            right = newLongLine,
            granularity = DiffGranularity.WORD
        )

        assertEquals(oldLongLine, leftSpans.joinToString("") { it.text })
        assertEquals(newLongLine, rightSpans.joinToString("") { it.text })
        assertTrue(leftSpans.any { it.isHighlighted && it.text.contains("OLD_TOKEN") })
        assertTrue(rightSpans.any { it.isHighlighted && it.text.contains("NEW_TOKEN") })

        // Folding test with 20 long identical lines
        val lines = (1..20).map { "val line$it = \"" + "X".repeat(1000) + "\"" }
        val oldText = (lines + listOf("val diff = 1")).joinToString("\n")
        val newText = (lines + listOf("val diff = 2")).joinToString("\n")

        val diffResult = engine.calculateDiff(oldText, newText)
        val displayItems = FoldingManager.createDisplayItems(
            diffResult = diffResult,
            isFoldingEnabled = true,
            contextLines = 3,
            foldingThreshold = 8
        )

        // Context lines = 3, so first 17 lines should be folded into 1 header
        assertTrue(displayItems.any { it is DiffDisplayItem.FoldedHeader })
        val foldedHeader = displayItems.first { it is DiffDisplayItem.FoldedHeader } as DiffDisplayItem.FoldedHeader
        assertEquals(17, foldedHeader.lineCount)
    }

    @Test
    fun testEdgeCase_SpecialCharactersAndRtlScripts() = runTest {
        // RTL (Arabic, Hebrew) and control characters
        val oldArabic = "مرحبا بالعالم 123\nשָׁלוֹם עוֹלָם\nTab\tSeparated\tValues"
        val newArabic = "مرحبا بالكون 123\nשָׁלוֹם עוֹלָם\nTab\tModified\tValues"

        val result = engine.calculateDiff(oldArabic, newArabic, enableInlineDiff = true)
        assertEquals(3, result.rows.size)
        assertEquals(DiffRowType.MODIFIED, result.rows[0].type)
        assertEquals(DiffRowType.UNCHANGED, result.rows[1].type)
        assertEquals(DiffRowType.MODIFIED, result.rows[2].type)

        assertEquals("שָׁלוֹם עוֹלָם", result.rows[1].left?.content)
        assertEquals("שָׁלוֹם עוֹלָם", result.rows[1].right?.content)
    }

    @Test
    fun testIncrementalFolding_Middle_ExpandUpAndDown() = runTest {
        // 30 unchanged lines between 2 changed lines
        val oldLines = listOf("CHANGE_START") + (1..30).map { "Line $it" } + listOf("CHANGE_END")
        val newLines = listOf("MODIFIED_START") + (1..30).map { "Line $it" } + listOf("MODIFIED_END")

        val diffResult = engine.calculateDiff(oldLines.joinToString("\n"), newLines.joinToString("\n"))

        // Initial folding: contextLines = 3, middle block hidden = 24 lines (Line 4..27)
        val initialItems = FoldingManager.createDisplayItems(
            diffResult = diffResult,
            contextLines = 3,
            foldingThreshold = 8
        )
        val initialHeader = initialItems.first { it is DiffDisplayItem.FoldedHeader } as DiffDisplayItem.FoldedHeader
        assertEquals(24, initialHeader.lineCount)
        assertEquals(com.mdiwebma.diffview.model.FoldPosition.MIDDLE, initialHeader.position)

        // Expand down 6 lines
        val foldMap1 = mapOf(initialHeader.id to com.mdiwebma.diffview.FoldExpansionState(expandBottomLines = 6))
        val itemsAfterDown = FoldingManager.createDisplayItems(
            diffResult = diffResult,
            contextLines = 3,
            foldingThreshold = 8,
            expandedFoldMap = foldMap1
        )
        val headerAfterDown = itemsAfterDown.first { it is DiffDisplayItem.FoldedHeader } as DiffDisplayItem.FoldedHeader
        assertEquals(18, headerAfterDown.lineCount)

        // Expand up 6 lines as well (total 12 lines expanded, 12 lines remaining)
        val foldMap2 = mapOf(initialHeader.id to com.mdiwebma.diffview.FoldExpansionState(expandBottomLines = 6, expandTopLines = 6))
        val itemsAfterBoth = FoldingManager.createDisplayItems(
            diffResult = diffResult,
            contextLines = 3,
            foldingThreshold = 8,
            expandedFoldMap = foldMap2
        )
        val headerAfterBoth = itemsAfterBoth.first { it is DiffDisplayItem.FoldedHeader } as DiffDisplayItem.FoldedHeader
        assertEquals(12, headerAfterBoth.lineCount)

        // Expand All
        val foldMapAll = mapOf(initialHeader.id to com.mdiwebma.diffview.FoldExpansionState(isFullyExpanded = true))
        val itemsAll = FoldingManager.createDisplayItems(
            diffResult = diffResult,
            contextLines = 3,
            foldingThreshold = 8,
            expandedFoldMap = foldMapAll
        )
        assertTrue(itemsAll.none { it is DiffDisplayItem.FoldedHeader })
    }

    @Test
    fun testIncrementalFolding_StartAndEndOfFile() = runTest {
        // Start of file folding
        val startOld = (1..20).map { "Line $it" } + listOf("CHANGE")
        val startNew = (1..20).map { "Line $it" } + listOf("MODIFIED")
        val startDiff = engine.calculateDiff(startOld.joinToString("\n"), startNew.joinToString("\n"))

        val startItems = FoldingManager.createDisplayItems(diffResult = startDiff, contextLines = 3, foldingThreshold = 8)
        val startHeader = startItems.first { it is DiffDisplayItem.FoldedHeader } as DiffDisplayItem.FoldedHeader
        assertEquals(17, startHeader.lineCount)
        assertEquals(com.mdiwebma.diffview.model.FoldPosition.START_OF_FILE, startHeader.position)

        // Expand up 6 lines -> remaining 11 lines
        val startMap = mapOf(startHeader.id to com.mdiwebma.diffview.FoldExpansionState(expandTopLines = 6))
        val startExpandedItems = FoldingManager.createDisplayItems(diffResult = startDiff, contextLines = 3, foldingThreshold = 8, expandedFoldMap = startMap)
        val startExpandedHeader = startExpandedItems.first { it is DiffDisplayItem.FoldedHeader } as DiffDisplayItem.FoldedHeader
        assertEquals(11, startExpandedHeader.lineCount)
    }
}
