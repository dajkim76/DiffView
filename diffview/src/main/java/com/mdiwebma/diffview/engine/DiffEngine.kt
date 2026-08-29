package com.mdiwebma.diffview.engine

import com.mdiwebma.diffview.model.DiffGranularity
import com.mdiwebma.diffview.model.DiffResult
import com.mdiwebma.diffview.model.WhitespaceIgnoreMode

/**
 * 텍스트 간의 Diff를 계산하는 엔진 인터페이스.
 */
interface DiffEngine {

    /**
     * [oldText]와 [newText]를 비교하여 Side-by-Side [DiffResult]를 계산합니다.
     *
     * @param enableInlineDiff 변경된 행에 대해 단어/문자 단위 인라인 하이라이트를 계산할지 여부
     * @param whitespaceMode 공백 무시 비교 옵션 ([WhitespaceIgnoreMode.NONE], [WhitespaceIgnoreMode.TRIM_LEADING_TRAILING], 등)
     * @param granularity 인라인 하이라이트 비교 단위 ([DiffGranularity.WORD] vs [DiffGranularity.CHARACTER])
     */
    suspend fun calculateDiff(
        oldText: String,
        newText: String,
        enableInlineDiff: Boolean = true,
        whitespaceMode: WhitespaceIgnoreMode = WhitespaceIgnoreMode.NONE,
        granularity: DiffGranularity = DiffGranularity.WORD
    ): DiffResult
}
