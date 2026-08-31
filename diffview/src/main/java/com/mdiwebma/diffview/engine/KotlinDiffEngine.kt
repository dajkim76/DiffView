package com.mdiwebma.diffview.engine

import com.mdiwebma.diffview.model.DiffGranularity
import com.mdiwebma.diffview.model.DiffLine
import com.mdiwebma.diffview.model.DiffResult
import com.mdiwebma.diffview.model.DiffRow
import com.mdiwebma.diffview.model.DiffRowType
import com.mdiwebma.diffview.model.TextSpan
import com.mdiwebma.diffview.model.WhitespaceIgnoreMode
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
        enableInlineDiff: Boolean,
        whitespaceMode: WhitespaceIgnoreMode,
        granularity: DiffGranularity
    ): DiffResult = withContext(defaultDispatcher) {
        val originalLines = splitLines(oldText)
        val modifiedLines = splitLines(newText)

        if (originalLines.isEmpty() && modifiedLines.isEmpty()) {
            return@withContext DiffResult(emptyList())
        }

        // 공백 무시 모드에 따라 비교용 정규화 리스트 생성
        val normalizedOriginalLines = if (whitespaceMode == WhitespaceIgnoreMode.NONE) {
            originalLines
        } else {
            originalLines.map { whitespaceMode.normalize(it) }
        }

        val normalizedModifiedLines = if (whitespaceMode == WhitespaceIgnoreMode.NONE) {
            modifiedLines
        } else {
            modifiedLines.map { whitespaceMode.normalize(it) }
        }

        val patch = diff(normalizedOriginalLines, normalizedModifiedLines)
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
                val origText = originalLines[originalIndex]
                val modText = modifiedLines[modifiedIndex]
                val line = DiffLine(
                    lineNumber = origLineNum,
                    content = origText,
                    spans = listOf(TextSpan(origText, false))
                )
                val rightLine = DiffLine(
                    lineNumber = modLineNum,
                    content = modText,
                    spans = listOf(TextSpan(modText, false))
                )
                addRow(line, rightLine, DiffRowType.UNCHANGED)
                originalIndex++
                modifiedIndex++
            }

            when (delta) {
                is ChangeDelta -> {
                    val origSize = delta.source.lines.size
                    val modSize = delta.target.lines.size
                    val maxLen = max(origSize, modSize)

                    for (i in 0 until maxLen) {
                        val hasLeft = i < origSize
                        val hasRight = i < modSize

                        if (hasLeft && hasRight) {
                            val origLineIndex = originalIndex + i
                            val modLineIndex = modifiedIndex + i
                            val leftText = originalLines[origLineIndex]
                            val rightText = modifiedLines[modLineIndex]
                            val isEqualByMode = whitespaceMode.areEqual(leftText, rightText)

                            if (isEqualByMode) {
                                val leftLine = DiffLine(
                                    lineNumber = origLineIndex + 1,
                                    content = leftText,
                                    spans = listOf(TextSpan(leftText, false))
                                )
                                val rightLine = DiffLine(
                                    lineNumber = modLineIndex + 1,
                                    content = rightText,
                                    spans = listOf(TextSpan(rightText, false))
                                )
                                addRow(leftLine, rightLine, DiffRowType.UNCHANGED)
                            } else {
                                val (leftSpans, rightSpans) = if (enableInlineDiff) {
                                    InlineDiffCalculator.calculateInlineDiff(
                                        left = leftText,
                                        right = rightText,
                                        granularity = granularity
                                    )
                                } else {
                                    listOf(TextSpan(leftText, false)) to listOf(TextSpan(rightText, false))
                                }
                                val leftLine = DiffLine(
                                    lineNumber = origLineIndex + 1,
                                    content = leftText,
                                    spans = leftSpans
                                )
                                val rightLine = DiffLine(
                                    lineNumber = modLineIndex + 1,
                                    content = rightText,
                                    spans = rightSpans
                                )
                                addRow(leftLine, rightLine, DiffRowType.MODIFIED)
                            }
                        } else if (hasLeft) {
                            val origLineIndex = originalIndex + i
                            val leftText = originalLines[origLineIndex]
                            val leftLine = DiffLine(
                                lineNumber = origLineIndex + 1,
                                content = leftText,
                                spans = listOf(TextSpan(leftText, false))
                            )
                            addRow(leftLine, null, DiffRowType.DELETED)
                        } else {
                            val modLineIndex = modifiedIndex + i
                            val rightText = modifiedLines[modLineIndex]
                            val rightLine = DiffLine(
                                lineNumber = modLineIndex + 1,
                                content = rightText,
                                spans = listOf(TextSpan(rightText, false))
                            )
                            addRow(null, rightLine, DiffRowType.INSERTED)
                        }
                    }
                    originalIndex += origSize
                    modifiedIndex += modSize
                }

                is DeleteDelta -> {
                    for (i in 0 until delta.source.lines.size) {
                        val origLineIndex = originalIndex + i
                        val leftText = originalLines[origLineIndex]
                        val leftLine = DiffLine(
                            lineNumber = origLineIndex + 1,
                            content = leftText,
                            spans = listOf(TextSpan(leftText, false))
                        )
                        addRow(leftLine, null, DiffRowType.DELETED)
                    }
                    originalIndex += delta.source.lines.size
                }

                is InsertDelta -> {
                    for (i in 0 until delta.target.lines.size) {
                        val modLineIndex = modifiedIndex + i
                        val rightText = modifiedLines[modLineIndex]
                        val rightLine = DiffLine(
                            lineNumber = modLineIndex + 1,
                            content = rightText,
                            spans = listOf(TextSpan(rightText, false))
                        )
                        addRow(null, rightLine, DiffRowType.INSERTED)
                    }
                    modifiedIndex += delta.target.lines.size
                }

                else -> {
                    val origSize = delta.source.lines.size
                    for (i in 0 until origSize) {
                        val origLineIndex = originalIndex + i
                        val modLineIndex = modifiedIndex + i
                        val origText = originalLines[origLineIndex]
                        val modText = modifiedLines[modLineIndex]
                        val leftLine = DiffLine(
                            lineNumber = origLineIndex + 1,
                            content = origText,
                            spans = listOf(TextSpan(origText, false))
                        )
                        val rightLine = DiffLine(
                            lineNumber = modLineIndex + 1,
                            content = modText,
                            spans = listOf(TextSpan(modText, false))
                        )
                        addRow(leftLine, rightLine, DiffRowType.UNCHANGED)
                    }
                    originalIndex += origSize
                    modifiedIndex += origSize
                }
            }
        }

        // 마지막 남은 UNCHANGED 라인들 채우기
        while (originalIndex < originalLines.size && modifiedIndex < modifiedLines.size) {
            val origText = originalLines[originalIndex]
            val modText = modifiedLines[modifiedIndex]
            val leftLine = DiffLine(
                lineNumber = originalIndex + 1,
                content = origText,
                spans = listOf(TextSpan(origText, false))
            )
            val rightLine = DiffLine(
                lineNumber = modifiedIndex + 1,
                content = modText,
                spans = listOf(TextSpan(modText, false))
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
