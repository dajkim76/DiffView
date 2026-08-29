package com.mdiwebma.diff.diffui

import com.mdiwebma.diff.model.DiffDisplayItem
import com.mdiwebma.diff.model.DiffMode
import com.mdiwebma.diff.model.DiffResult
import com.mdiwebma.diff.model.DiffRow
import com.mdiwebma.diff.model.DiffRowType

/**
 * Git / Android Studio 표준 방식의 문맥 기반 미변경 라인 접기 매니저.
 * - 파일 시작부: 변경점 바로 앞 [contextLines]만 유지하고 앞부분 접기
 * - 파일 끝부분: 변경점 바로 뒤 [contextLines]만 유지하고 뒷부분 접기
 * - 변경점 사이: 위 [contextLines] + 중간 접기 배너 + 아래 [contextLines]
 */
object FoldingManager {

    /**
     * [diffResult]의 rows를 받아 [mode], [contextLines], [foldingThreshold] 설정에 맞춰
     * [DiffDisplayItem] 목록을 생성합니다.
     *
     * @param contextLines 변경점 주변에 항상 표시할 앞/뒤 미변경 문맥 라인 수 (기본 3줄)
     * @param foldingThreshold 접기 처리를 시작할 최소 미변경 라인 수 (기본 8줄)
     */
    fun createDisplayItems(
        diffResult: DiffResult?,
        mode: DiffMode = DiffMode.SIDE_BY_SIDE,
        isFoldingEnabled: Boolean = true,
        contextLines: Int = 3,
        foldingThreshold: Int = 8,
        expandedFoldIds: Set<Long> = emptySet()
    ): List<DiffDisplayItem> {
        if (diffResult == null || diffResult.rows.isEmpty()) {
            return emptyList()
        }

        val rows = diffResult.rows
        val displayItems = mutableListOf<DiffDisplayItem>()
        var i = 0
        var foldIndex = 0L

        fun addRowsToItems(rowsToAdd: List<DiffRow>) {
            when (mode) {
                DiffMode.SIDE_BY_SIDE -> {
                    for (r in rowsToAdd) {
                        displayItems.add(DiffDisplayItem.SideBySideRow(r))
                    }
                }

                DiffMode.UNIFIED -> {
                    for (r in rowsToAdd) {
                        when (r.type) {
                            DiffRowType.UNCHANGED -> {
                                displayItems.add(
                                    DiffDisplayItem.UnifiedRow(
                                        id = r.id * 10,
                                        oldLineNumber = r.left?.lineNumber,
                                        newLineNumber = r.right?.lineNumber,
                                        content = r.left?.content ?: "",
                                        spans = r.left?.spans ?: emptyList(),
                                        type = DiffRowType.UNCHANGED
                                    )
                                )
                            }

                            DiffRowType.DELETED -> {
                                displayItems.add(
                                    DiffDisplayItem.UnifiedRow(
                                        id = r.id * 10,
                                        oldLineNumber = r.left?.lineNumber,
                                        newLineNumber = null,
                                        content = r.left?.content ?: "",
                                        spans = r.left?.spans ?: emptyList(),
                                        type = DiffRowType.DELETED
                                    )
                                )
                            }

                            DiffRowType.INSERTED -> {
                                displayItems.add(
                                    DiffDisplayItem.UnifiedRow(
                                        id = r.id * 10,
                                        oldLineNumber = null,
                                        newLineNumber = r.right?.lineNumber,
                                        content = r.right?.content ?: "",
                                        spans = r.right?.spans ?: emptyList(),
                                        type = DiffRowType.INSERTED
                                    )
                                )
                            }

                            DiffRowType.MODIFIED -> {
                                if (r.left != null) {
                                    displayItems.add(
                                        DiffDisplayItem.UnifiedRow(
                                            id = r.id * 10,
                                            oldLineNumber = r.left.lineNumber,
                                            newLineNumber = null,
                                            content = r.left.content,
                                            spans = r.left.spans,
                                            type = DiffRowType.DELETED
                                        )
                                    )
                                }
                                if (r.right != null) {
                                    displayItems.add(
                                        DiffDisplayItem.UnifiedRow(
                                            id = r.id * 10 + 1,
                                            oldLineNumber = null,
                                            newLineNumber = r.right.lineNumber,
                                            content = r.right.content,
                                            spans = r.right.spans,
                                            type = DiffRowType.INSERTED
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!isFoldingEnabled) {
            addRowsToItems(rows)
            return displayItems
        }

        while (i < rows.size) {
            val row = rows[i]

            if (row.type == DiffRowType.UNCHANGED) {
                var j = i
                while (j < rows.size && rows[j].type == DiffRowType.UNCHANGED) {
                    j++
                }

                val unchangedBlock = rows.subList(i, j)
                val blockLength = unchangedBlock.size
                val isStartOfFile = (i == 0)
                val isEndOfFile = (j == rows.size)

                // 파일 전체가 미변경인 경우
                if (isStartOfFile && isEndOfFile) {
                    addRowsToItems(unchangedBlock)
                    break
                }

                if (isStartOfFile) {
                    if (blockLength > contextLines + 2 && blockLength > foldingThreshold) {
                        val foldId = 1_000_000L + foldIndex++
                        val hiddenCount = blockLength - contextLines
                        val hiddenBlock = unchangedBlock.subList(0, hiddenCount)
                        val bottomContext = unchangedBlock.subList(hiddenCount, blockLength)

                        if (expandedFoldIds.contains(foldId)) {
                            addRowsToItems(hiddenBlock)
                        } else {
                            val firstRow = hiddenBlock.first()
                            val lastRow = hiddenBlock.last()
                            displayItems.add(
                                DiffDisplayItem.FoldedHeader(
                                    id = foldId,
                                    hiddenRows = hiddenBlock,
                                    lineCount = hiddenBlock.size,
                                    startLineLeft = firstRow.left?.lineNumber,
                                    endLineLeft = lastRow.left?.lineNumber,
                                    startLineRight = firstRow.right?.lineNumber,
                                    endLineRight = lastRow.right?.lineNumber
                                )
                            )
                        }
                        addRowsToItems(bottomContext)
                    } else {
                        addRowsToItems(unchangedBlock)
                    }
                } else if (isEndOfFile) {
                    if (blockLength > contextLines + 2 && blockLength > foldingThreshold) {
                        val topContext = unchangedBlock.subList(0, contextLines)
                        val foldId = 1_000_000L + foldIndex++
                        val hiddenBlock = unchangedBlock.subList(contextLines, blockLength)

                        addRowsToItems(topContext)
                        if (expandedFoldIds.contains(foldId)) {
                            addRowsToItems(hiddenBlock)
                        } else {
                            val firstRow = hiddenBlock.first()
                            val lastRow = hiddenBlock.last()
                            displayItems.add(
                                DiffDisplayItem.FoldedHeader(
                                    id = foldId,
                                    hiddenRows = hiddenBlock,
                                    lineCount = hiddenBlock.size,
                                    startLineLeft = firstRow.left?.lineNumber,
                                    endLineLeft = lastRow.left?.lineNumber,
                                    startLineRight = firstRow.right?.lineNumber,
                                    endLineRight = lastRow.right?.lineNumber
                                )
                            )
                        }
                    } else {
                        addRowsToItems(unchangedBlock)
                    }
                } else {
                    if (blockLength > foldingThreshold && blockLength > contextLines * 2) {
                        // 1. 앞쪽 문맥 라인 (항상 화면에 표시)
                        val topContext = unchangedBlock.subList(0, contextLines)
                        addRowsToItems(topContext)

                        // 2. 중간 접히는 블록
                        val middleBlock = unchangedBlock.subList(contextLines, blockLength - contextLines)
                        val foldId = 1_000_000L + foldIndex++
                        val isExpanded = expandedFoldIds.contains(foldId)

                        if (isExpanded) {
                            addRowsToItems(middleBlock)
                        } else {
                            val firstRow = middleBlock.first()
                            val lastRow = middleBlock.last()
                            displayItems.add(
                                DiffDisplayItem.FoldedHeader(
                                    id = foldId,
                                    hiddenRows = middleBlock,
                                    lineCount = middleBlock.size,
                                    startLineLeft = firstRow.left?.lineNumber,
                                    endLineLeft = lastRow.left?.lineNumber,
                                    startLineRight = firstRow.right?.lineNumber,
                                    endLineRight = lastRow.right?.lineNumber
                                )
                            )
                        }

                        // 3. 뒤쪽 문맥 라인 (항상 화면에 표시)
                        val bottomContext = unchangedBlock.subList(blockLength - contextLines, blockLength)
                        addRowsToItems(bottomContext)
                    } else {
                        // 임계치 이하인 경우 전체 표시
                        addRowsToItems(unchangedBlock)
                    }
                }
                i = j
            } else {
                addRowsToItems(listOf(row))
                i++
            }
        }

        return displayItems
    }
}
