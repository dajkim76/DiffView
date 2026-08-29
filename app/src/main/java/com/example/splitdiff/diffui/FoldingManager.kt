package com.example.splitdiff.diffui

import com.example.splitdiff.model.DiffDisplayItem
import com.example.splitdiff.model.DiffResult
import com.example.splitdiff.model.DiffRow
import com.example.splitdiff.model.DiffRowType

/**
 * 변경되지 않은 긴 코드 블록을 접거나 펼치도록 DiffDisplayItem 목록으로 변환하는 매니저.
 */
object FoldingManager {

    /**
     * [diffResult]의 rows를 받아 [foldingThreshold]보다 긴 연속 UNCHANGED 구간을
     * [DiffDisplayItem.FoldedHeader]로 묶어 반환합니다.
     * [expandedFoldIds]에 포함된 fold id는 펼쳐서 개별 라인으로 반환합니다.
     */
    fun createDisplayItems(
        diffResult: DiffResult?,
        isFoldingEnabled: Boolean = true,
        foldingThreshold: Int = 5,
        expandedFoldIds: Set<Long> = emptySet()
    ): List<DiffDisplayItem> {
        if (diffResult == null || diffResult.rows.isEmpty()) {
            return emptyList()
        }

        val rows = diffResult.rows
        if (!isFoldingEnabled) {
            return rows.map { DiffDisplayItem.LineRow(it) }
        }

        val displayItems = mutableListOf<DiffDisplayItem>()
        var i = 0
        var foldIndex = 0L

        while (i < rows.size) {
            val row = rows[i]

            if (row.type == DiffRowType.UNCHANGED) {
                // 연속된 UNCHANGED 블록 크기 확인
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
                        // 펼쳐진 상태: 해당 블록 라인들을 그대로 출력
                        for (r in unchangedBlock) {
                            displayItems.add(DiffDisplayItem.LineRow(r))
                        }
                    } else {
                        // 접힌 상태: FoldedHeader 생성
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
                    for (r in unchangedBlock) {
                        displayItems.add(DiffDisplayItem.LineRow(r))
                    }
                }
                i = j
            } else {
                displayItems.add(DiffDisplayItem.LineRow(row))
                i++
            }
        }

        return displayItems
    }
}
