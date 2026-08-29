package com.example.splitdiff.model

/**
 * 인라인 차이점 하이라이트 단위
 */
data class TextSpan(
    val text: String,
    val isHighlighted: Boolean = false
)

/**
 * 한 쪽(Left 또는 Right)의 라인 정보
 */
data class DiffLine(
    val lineNumber: Int?,
    val content: String,
    val spans: List<TextSpan> = listOf(TextSpan(content, false))
)

/**
 * Diff 행의 변경 타입
 */
enum class DiffRowType {
    UNCHANGED,
    MODIFIED,
    INSERTED,
    DELETED
}

/**
 * Left와 Right의 정렬된 한 줄의 Diff 모델
 */
data class DiffRow(
    val id: Long,
    val left: DiffLine?,
    val right: DiffLine?,
    val type: DiffRowType
)

/**
 * UI 렌더링을 위한 표시 단위 (일반 DiffRow 또는 접힌 Unchanged Block)
 */
sealed interface DiffDisplayItem {
    val id: Long

    data class LineRow(
        val diffRow: DiffRow
    ) : DiffDisplayItem {
        override val id: Long get() = diffRow.id
    }

    data class FoldedHeader(
        override val id: Long,
        val hiddenRows: List<DiffRow>,
        val lineCount: Int,
        val startLineLeft: Int?,
        val endLineLeft: Int?,
        val startLineRight: Int?,
        val endLineRight: Int?
    ) : DiffDisplayItem
}

/**
 * 최종 Diff 계산 결과
 */
data class DiffResult(
    val rows: List<DiffRow>,
    val addedCount: Int = 0,
    val deletedCount: Int = 0,
    val modifiedCount: Int = 0,
    val unchangedCount: Int = 0
)
