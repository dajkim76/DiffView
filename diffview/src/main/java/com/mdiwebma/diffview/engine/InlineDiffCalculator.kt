package com.mdiwebma.diffview.engine

import com.mdiwebma.diffview.model.TextSpan
import kotlin.math.max
import kotlin.math.min

/**
 * 변경된 라인(MODIFIED)에 대해 문자 단위로 차이점을 찾아
 * [TextSpan] 목록으로 분할하는 인라인 Diff 계산기.
 */
object InlineDiffCalculator {

    /**
     * left와 right 문자열을 비교하여 각각의 [TextSpan] 목록을 반환합니다.
     * Pair.first: left의 TextSpan 목록 (변경된/삭제된 부분 하이라이트)
     * Pair.second: right의 TextSpan 목록 (변경된/추가된 부분 하이라이트)
     */
    fun calculateInlineDiff(
        left: String,
        right: String
    ): Pair<List<TextSpan>, List<TextSpan>> {
        if (left == right) {
            val leftSpans = listOf(TextSpan(left, false))
            val rightSpans = listOf(TextSpan(right, false))
            return leftSpans to rightSpans
        }

        if (left.isEmpty()) {
            return emptyList<TextSpan>() to listOf(TextSpan(right, true))
        }

        if (right.isEmpty()) {
            return listOf(TextSpan(left, true)) to emptyList()
        }

        // 성능 보호: 너무 긴 라인(예: 2000자 초과)은 prefix/suffix 매칭으로 고속 처리
        if (left.length > 2000 || right.length > 2000) {
            return calculatePrefixSuffixDiff(left, right)
        }

        return calculateLcsDiff(left, right)
    }

    private fun calculatePrefixSuffixDiff(
        left: String,
        right: String
    ): Pair<List<TextSpan>, List<TextSpan>> {
        var commonPrefix = 0
        val minLen = min(left.length, right.length)
        while (commonPrefix < minLen && left[commonPrefix] == right[commonPrefix]) {
            commonPrefix++
        }

        var commonSuffix = 0
        while (commonSuffix < minLen - commonPrefix &&
            left[left.length - 1 - commonSuffix] == right[right.length - 1 - commonSuffix]
        ) {
            commonSuffix++
        }

        val leftSpans = mutableListOf<TextSpan>()
        val rightSpans = mutableListOf<TextSpan>()

        val prefixText = left.substring(0, commonPrefix)
        if (prefixText.isNotEmpty()) {
            leftSpans.add(TextSpan(prefixText, false))
            rightSpans.add(TextSpan(prefixText, false))
        }

        val leftDiff = left.substring(commonPrefix, left.length - commonSuffix)
        if (leftDiff.isNotEmpty()) {
            leftSpans.add(TextSpan(leftDiff, true))
        }

        val rightDiff = right.substring(commonPrefix, right.length - commonSuffix)
        if (rightDiff.isNotEmpty()) {
            rightSpans.add(TextSpan(rightDiff, true))
        }

        val leftSuffix = left.substring(left.length - commonSuffix)
        val rightSuffix = right.substring(right.length - commonSuffix)
        if (leftSuffix.isNotEmpty()) {
            leftSpans.add(TextSpan(leftSuffix, false))
        }
        if (rightSuffix.isNotEmpty()) {
            rightSpans.add(TextSpan(rightSuffix, false))
        }

        return leftSpans to rightSpans
    }

    private fun calculateLcsDiff(
        left: String,
        right: String
    ): Pair<List<TextSpan>, List<TextSpan>> {
        val n = left.length
        val m = right.length

        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 1..n) {
            for (j in 1..m) {
                if (left[i - 1] == right[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1] + 1
                } else {
                    dp[i][j] = max(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }

        val leftMatched = BooleanArray(n)
        val rightMatched = BooleanArray(m)

        var i = n
        var j = m
        while (i > 0 && j > 0) {
            if (left[i - 1] == right[j - 1]) {
                leftMatched[i - 1] = true
                rightMatched[j - 1] = true
                i--
                j--
            } else if (dp[i - 1][j] >= dp[i][j - 1]) {
                i--
            } else {
                j--
            }
        }

        val leftSpans = buildSpans(left, leftMatched)
        val rightSpans = buildSpans(right, rightMatched)

        return leftSpans to rightSpans
    }

    private fun buildSpans(text: String, isMatched: BooleanArray): List<TextSpan> {
        if (text.isEmpty()) return emptyList()

        val spans = mutableListOf<TextSpan>()
        var currentHighlighted = !isMatched[0]
        var startIdx = 0

        for (k in 1 until text.length) {
            val highlighted = !isMatched[k]
            if (highlighted != currentHighlighted) {
                spans.add(TextSpan(text.substring(startIdx, k), currentHighlighted))
                currentHighlighted = highlighted
                startIdx = k
            }
        }
        spans.add(TextSpan(text.substring(startIdx), currentHighlighted))
        return spans
    }
}
