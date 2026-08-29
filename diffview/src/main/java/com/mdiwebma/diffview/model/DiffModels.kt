package com.mdiwebma.diffview.model

/**
 * 공백 문자(Whitespace) 비교 무시 옵션.
 */
enum class WhitespaceIgnoreMode {
    /**
     * 공백을 무시하지 않고 엄격하게 비교 (기본값)
     */
    NONE,

    /**
     * 라인 앞/뒤의 공백(들여쓰기 및 줄 끝 공백) 차이를 무시
     */
    TRIM_LEADING_TRAILING,

    /**
     * 공백의 개수 변화를 무시 (연속된 공백을 단일 공백으로 간주)
     */
    COLLAPSE_WHITESPACE,

    /**
     * 모든 공백 문자를 무시하고 내용만 비교
     */
    IGNORE_ALL;

    /**
     * 설정된 모드에 따라 문자열을 정규화합니다.
     */
    fun normalize(str: String): String {
        return when (this) {
            NONE -> str
            TRIM_LEADING_TRAILING -> str.trim()
            COLLAPSE_WHITESPACE -> str.trim().replace(WHITESPACE_REGEX, " ")
            IGNORE_ALL -> str.filterNot { it.isWhitespace() }
        }
    }

    /**
     * 설정된 모드에 따라 두 문자열이 동등한지 판별합니다.
     */
    fun areEqual(a: String, b: String): Boolean {
        return normalize(a) == normalize(b)
    }

    companion object {
        private val WHITESPACE_REGEX = "\\s+".toRegex()
    }
}

/**
 * Diff 표시 모드 (Side-by-Side 분할 뷰 vs Unified 단일 통합 뷰)
 */
enum class DiffMode {
    /**
     * 좌(Original) / 우(Modified) 2열 나란히 표시하는 모드
     */
    SIDE_BY_SIDE,

    /**
     * 변경 사항을 위아래 단일 열로 표시하는 통합 모드 (+/- 표기)
     */
    UNIFIED
}

/**
 * 인라인 차이점 하이라이트 단위
 */
enum class DiffGranularity {
    /**
     * 단어/토큰 단위 비교 (기본값)
     */
    WORD,

    /**
     * 문자 단위 비교
     */
    CHARACTER
}

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
 * UI 렌더링을 위한 표시 단위
 */
sealed interface DiffDisplayItem {
    val id: Long

    /**
     * Side-by-Side 모드용 한 행
     */
    data class SideBySideRow(
        val diffRow: DiffRow
    ) : DiffDisplayItem {
        override val id: Long get() = diffRow.id
    }

    /**
     * Unified 모드용 한 행 (Old Line #, New Line #, 소스코드, +/- 접두사)
     */
    data class UnifiedRow(
        override val id: Long,
        val oldLineNumber: Int?,
        val newLineNumber: Int?,
        val content: String,
        val spans: List<TextSpan>,
        val type: DiffRowType,
        val prefix: String = when (type) {
            DiffRowType.INSERTED -> "+"
            DiffRowType.DELETED -> "-"
            else -> " "
        }
    ) : DiffDisplayItem

    /**
     * 미변경 구간 접기 헤더
     */
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
) {
    val totalDiffCount: Int get() = addedCount + deletedCount + modifiedCount
    val hasChanges: Boolean get() = totalDiffCount > 0
}
