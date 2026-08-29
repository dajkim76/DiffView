package com.example.splitdiff.diffui

import com.example.splitdiff.model.DiffDisplayItem
import com.example.splitdiff.model.DiffMode
import com.example.splitdiff.model.DiffResult
import com.example.splitdiff.model.DiffRow
import com.example.splitdiff.model.DiffRowType

/**
 * 변경되지 않은 긴 코드 블록을 접거나 펼치며, DiffMode(Side-by-Side vs Unified)에 맞게
 * [DiffDisplayItem] 목록으로 변환하는 매니저.
 */
object FoldingManager {

    /**
     * [diffResult]의 rows를 받아 [mode] 및 [foldingThreshold] 설정에 맞춰
     * [DiffDisplayItem] 목록을 생성합니다.
     */
    fun createDisplayItems(
        diffResult: DiffResult?,
        mode: DiffMode = DiffMode.SIDE_BY_SIDE,
        isFoldingEnabled: Boolean = true,
        foldingThreshold: Int = 5,
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
                                // 위아래로 표시: 구 라인(-) 먼저, 신 라인(+) 다음
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

                if (blockLength > foldingThreshold) {
                    val foldId = 1_000_000L + foldIndex++
                    val isExpanded = expandedFoldIds.contains(foldId)

                    if (isExpanded) {
                        addRowsToItems(unchangedBlock)
                    } else {
                        val firstRow = unchangedBlock.first()
                        val lastRow = unchangedBlock.last()
                        displayItems.add(
                            DiffDisplayItem.FoldedHeader(
                                id = foldId,
                                hiddenRows = unchangedBlock,
                                lineCount = blockLength,
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
                i = j
            } else {
                addRowsToItems(listOf(row))
                i++
            }
        }

        return displayItems
    }
}
