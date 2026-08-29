package com.example.splitdiff.engine

import com.example.splitdiff.model.DiffResult

/**
 * 텍스트 간의 Diff를 계산하는 엔진 인터페이스.
 * UI는 구체적인 Diff 라이브러리에 직접 의존하지 않고 이 인터페이스를 통해 Diff 결과를 전달받습니다.
 */
interface DiffEngine {

    /**
     * [oldText]와 [newText]를 비교하여 Side-by-Side [DiffResult]를 계산합니다.
     */
    suspend fun calculateDiff(
        oldText: String,
        newText: String,
        enableInlineDiff: Boolean = true
    ): DiffResult
}
