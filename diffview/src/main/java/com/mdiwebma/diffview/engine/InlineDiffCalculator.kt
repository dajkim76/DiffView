package com.mdiwebma.diffview.engine

import com.mdiwebma.diffview.model.DiffGranularity
import com.mdiwebma.diffview.model.TextSpan
import kotlin.math.max
import kotlin.math.min

/**
 * 변경된 라인(MODIFIED)에 대해 단어(Word) 또는 문자(Char) 단위로 차이점을 찾아
 * [TextSpan] 목록으로 분할하는 인라인 Diff 계산기.
 */
object InlineDiffCalculator {

    private val TOKEN_REGEX = Regex("""[\p{L}\p{N}_]+|\s+|[^\p{L}\p{N}_\s]+""")

    /**
     * left와 right 문자열을 비교하여 각각의 [TextSpan] 목록을 반환합니다.
     * Pair.first: left의 TextSpan 목록 (변경된/삭제된 부분 하이라이트)
     * Pair.second: right의 TextSpan 목록 (변경된/추가된 부분 하이라이트)
     *
     * @param granularity [DiffGranularity.WORD] (단어 단위, 기본값) 또는 [DiffGranularity.CHARACTER] (문자 단위)
     */
    fun calculateInlineDiff(
        left: String,
        right: String,
        granularity: DiffGranularity = DiffGranularity.WORD
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

        return when (granularity) {
            DiffGranularity.WORD -> calculateWordDiff(left, right)
            DiffGranularity.CHARACTER -> calculateCharDiff(left, right)
        }
    }

    private fun calculateCharDiff(
        left: String,
        right: String
    ): Pair<List<TextSpan>, List<TextSpan>> {
        // 성능 보호: 너무 긴 라인(예: 2000자 초과)은 prefix/suffix 매칭으로 고속 처리
        if (left.length > 2000 || right.length > 2000) {
            return calculatePrefixSuffixDiff(left, right)
        }

        return calculateLcsDiff(left, right)
    }

    private fun calculateWordDiff(
        left: String,
        right: String
    ): Pair<List<TextSpan>, List<TextSpan>> {
        val leftTokens = tokenize(left)
        val rightTokens = tokenize(right)

        // 성능 보호: 너무 많은 토큰(예: 1000개 초과)은 prefix/suffix 매칭으로 고속 처리
        if (leftTokens.size > 1000 || rightTokens.size > 1000) {
            return calculateTokenPrefixSuffixDiff(leftTokens, rightTokens)
        }

        return calculateTokenLcsDiff(leftTokens, rightTokens)
    }

    private fun tokenize(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        return TOKEN_REGEX.findAll(text).map { it.value }.toList()
    }

    private fun calculateTokenPrefixSuffixDiff(
        leftTokens: List<String>,
        rightTokens: List<String>
    ): Pair<List<TextSpan>, List<TextSpan>> {
        var commonPrefix = 0
        val minLen = min(leftTokens.size, rightTokens.size)
        while (commonPrefix < minLen && leftTokens[commonPrefix] == rightTokens[commonPrefix]) {
            commonPrefix++
        }

        var commonSuffix = 0
        while (commonSuffix < minLen - commonPrefix &&
            leftTokens[leftTokens.size - 1 - commonSuffix] == rightTokens[rightTokens.size - 1 - commonSuffix]
        ) {
            commonSuffix++
        }

        val leftSpans = mutableListOf<TextSpan>()
        val rightSpans = mutableListOf<TextSpan>()

        val prefixTokens = leftTokens.subList(0, commonPrefix)
        if (prefixTokens.isNotEmpty()) {
            val text = prefixTokens.joinToString("")
            leftSpans.add(TextSpan(text, false))
            rightSpans.add(TextSpan(text, false))
        }

        val leftDiffTokens = leftTokens.subList(commonPrefix, leftTokens.size - commonSuffix)
        if (leftDiffTokens.isNotEmpty()) {
            leftSpans.add(TextSpan(leftDiffTokens.joinToString(""), true))
        }

        val rightDiffTokens = rightTokens.subList(commonPrefix, rightTokens.size - commonSuffix)
        if (rightDiffTokens.isNotEmpty()) {
            rightSpans.add(TextSpan(rightDiffTokens.joinToString(""), true))
        }

        val leftSuffixTokens = leftTokens.subList(leftTokens.size - commonSuffix, leftTokens.size)
        val rightSuffixTokens = rightTokens.subList(rightTokens.size - commonSuffix, rightTokens.size)
        if (leftSuffixTokens.isNotEmpty()) {
            leftSpans.add(TextSpan(leftSuffixTokens.joinToString(""), false))
        }
        if (rightSuffixTokens.isNotEmpty()) {
            rightSpans.add(TextSpan(rightSuffixTokens.joinToString(""), false))
        }

        return leftSpans to rightSpans
    }

    private fun calculateTokenLcsDiff(
        leftTokens: List<String>,
        rightTokens: List<String>
    ): Pair<List<TextSpan>, List<TextSpan>> {
        val n = leftTokens.size
        val m = rightTokens.size

        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 1..n) {
            for (j in 1..m) {
                if (leftTokens[i - 1] == rightTokens[j - 1]) {
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
            if (leftTokens[i - 1] == rightTokens[j - 1]) {
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

        val leftSpans = buildSpansFromTokens(leftTokens, leftMatched)
        val rightSpans = buildSpansFromTokens(rightTokens, rightMatched)

        return leftSpans to rightSpans
    }

    private fun buildSpansFromTokens(tokens: List<String>, isMatched: BooleanArray): List<TextSpan> {
        if (tokens.isEmpty()) return emptyList()

        val spans = mutableListOf<TextSpan>()
        var currentHighlighted = !isMatched[0]
        val currentBuilder = StringBuilder(tokens[0])

        for (k in 1 until tokens.size) {
            val highlighted = !isMatched[k]
            if (highlighted != currentHighlighted) {
                spans.add(TextSpan(currentBuilder.toString(), currentHighlighted))
                currentBuilder.clear()
                currentHighlighted = highlighted
            }
            currentBuilder.append(tokens[k])
        }
        if (currentBuilder.isNotEmpty()) {
            spans.add(TextSpan(currentBuilder.toString(), currentHighlighted))
        }
        return spans
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
