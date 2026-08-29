package com.mdiwebma.diffview.engine

import com.mdiwebma.diffview.model.DiffLine
import com.mdiwebma.diffview.model.DiffResult
import com.mdiwebma.diffview.model.DiffRow
import com.mdiwebma.diffview.model.DiffRowType
import com.mdiwebma.diffview.model.TextSpan
import io.github.petertrr.diffutils.diff
import io.github.petertrr.diffutils.patch.ChangeDelta
import io.github.petertrr.diffutils.patch.DeleteDelta
import io.github.petertrr.diffutils.patch.InsertDelta
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * [io.github.petertrr.diffutils] 라이브러리를 활용한 [DiffEngine] 구현체.
 */
class KotlinDiffEngine(
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : DiffEngine {

    override suspend fun calculateDiff(
        oldText: String,
        newText: String,
        enableInlineDiff: Boolean
    ): DiffResult = withContext(defaultDispatcher) {
        val originalLines = splitLines(oldText)
        val modifiedLines = splitLines(newText)

        if (originalLines.isEmpty() && modifiedLines.isEmpty()) {
            return@withContext DiffResult(emptyList())
        }

        val patch = diff(originalLines, modifiedLines)
        val deltas = patch.deltas.sortedBy { it.source.position }

        val rows = mutableListOf<DiffRow>()
        var rowId = 0L

        var originalIndex = 0
        var modifiedIndex = 0

        var addedCount = 0
        var deletedCount = 0
        var modifiedCount = 0
        var unchangedCount = 0

        fun addRow(left: DiffLine?, right: DiffLine?, type: DiffRowType) {
            rows.add(DiffRow(id = rowId++, left = left, right = right, type = type))
            when (type) {
                DiffRowType.UNCHANGED -> unchangedCount++
                DiffRowType.MODIFIED -> modifiedCount++
                DiffRowType.INSERTED -> addedCount++
                DiffRowType.DELETED -> deletedCount++
            }
        }

        for (delta in deltas) {
            val deltaSourcePos = delta.source.position
            // Delta 이전의 동일한(UNCHANGED) 라인들 채우기
            while (originalIndex < deltaSourcePos) {
                val origLineNum = originalIndex + 1
                val modLineNum = modifiedIndex + 1
                val text = originalLines[originalIndex]
                val line = DiffLine(
                    lineNumber = origLineNum,
                    content = text,
                    spans = listOf(TextSpan(text, false))
                )
                val rightLine = DiffLine(
                    lineNumber = modLineNum,
                    content = text,
                    spans = listOf(TextSpan(text, false))
                )
                addRow(line, rightLine, DiffRowType.UNCHANGED)
                originalIndex++
                modifiedIndex++
            }

            when (delta) {
                is ChangeDelta -> {
                    val origChunk = delta.source.lines
                    val modChunk = delta.target.lines
                    val maxLen = max(origChunk.size, modChunk.size)

                    for (i in 0 until maxLen) {
                        val hasLeft = i < origChunk.size
                        val hasRight = i < modChunk.size

                        if (hasLeft && hasRight) {
                            val leftText = origChunk[i]
                            val rightText = modChunk[i]
                            val (leftSpans, rightSpans) = if (enableInlineDiff) {
                                InlineDiffCalculator.calculateInlineDiff(leftText, rightText)
                            } else {
                                listOf(TextSpan(leftText, false)) to listOf(TextSpan(rightText, false))
                            }
                            val leftLine = DiffLine(
                                lineNumber = originalIndex + 1,
                                content = leftText,
                                spans = leftSpans
                            )
                            val rightLine = DiffLine(
                                lineNumber = modifiedIndex + 1,
                                content = rightText,
                                spans = rightSpans
                            )
                            addRow(leftLine, rightLine, DiffRowType.MODIFIED)
                            originalIndex++
                            modifiedIndex++
                        } else if (hasLeft) {
                            val leftText = origChunk[i]
                            val leftLine = DiffLine(
                                lineNumber = originalIndex + 1,
                                content = leftText,
                                spans = listOf(TextSpan(leftText, false))
                            )
                            addRow(leftLine, null, DiffRowType.DELETED)
                            originalIndex++
                        } else {
                            val rightText = modChunk[i]
                            val rightLine = DiffLine(
                                lineNumber = modifiedIndex + 1,
                                content = rightText,
                                spans = listOf(TextSpan(rightText, false))
                            )
                            addRow(null, rightLine, DiffRowType.INSERTED)
                            modifiedIndex++
                        }
                    }
                }

                is DeleteDelta -> {
                    for (lineText in delta.source.lines) {
                        val leftLine = DiffLine(
                            lineNumber = originalIndex + 1,
                            content = lineText,
                            spans = listOf(TextSpan(lineText, false))
                        )
                        addRow(leftLine, null, DiffRowType.DELETED)
                        originalIndex++
                    }
                }

                is InsertDelta -> {
                    for (lineText in delta.target.lines) {
                        val rightLine = DiffLine(
                            lineNumber = modifiedIndex + 1,
                            content = lineText,
                            spans = listOf(TextSpan(lineText, false))
                        )
                        addRow(null, rightLine, DiffRowType.INSERTED)
                        modifiedIndex++
                    }
                }

                else -> {
                    val origChunk = delta.source.lines
                    for (lineText in origChunk) {
                        val leftLine = DiffLine(
                            lineNumber = originalIndex + 1,
                            content = lineText,
                            spans = listOf(TextSpan(lineText, false))
                        )
                        val rightLine = DiffLine(
                            lineNumber = modifiedIndex + 1,
                            content = lineText,
                            spans = listOf(TextSpan(lineText, false))
                        )
                        addRow(leftLine, rightLine, DiffRowType.UNCHANGED)
                        originalIndex++
                        modifiedIndex++
                    }
                }
            }
        }

        // 마지막 남은 UNCHANGED 라인들 채우기
        while (originalIndex < originalLines.size && modifiedIndex < modifiedLines.size) {
            val text = originalLines[originalIndex]
            val leftLine = DiffLine(
                lineNumber = originalIndex + 1,
                content = text,
                spans = listOf(TextSpan(text, false))
            )
            val rightLine = DiffLine(
                lineNumber = modifiedIndex + 1,
                content = text,
                spans = listOf(TextSpan(text, false))
            )
            addRow(leftLine, rightLine, DiffRowType.UNCHANGED)
            originalIndex++
            modifiedIndex++
        }

        DiffResult(
            rows = rows,
            addedCount = addedCount,
            deletedCount = deletedCount,
            modifiedCount = modifiedCount,
            unchangedCount = unchangedCount
        )
    }

    private fun splitLines(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val normalized = text.replace("\r\n", "\n").replace("\r", "\n")
        return normalized.split("\n")
    }
}
