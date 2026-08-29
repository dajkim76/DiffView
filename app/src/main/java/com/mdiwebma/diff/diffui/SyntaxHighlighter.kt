package com.mdiwebma.diff.diffui

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.annotation.ColorInt
import com.mdiwebma.diff.model.TextSpan
import java.util.regex.Pattern

/**
 * 소스 코드 문법 하이라이팅 인터페이스 (Android View CharSequence 반환).
 */
interface SyntaxHighlighter {
    /**
     * [spans] (인라인 diff span 목록)과 기본 [defaultTextColor], [highlightBgColor], [isDark] 테마 정보를 바탕으로
     * 하이라이팅된 [CharSequence]를 반환합니다.
     */
    fun highlight(
        spans: List<TextSpan>,
        @ColorInt defaultTextColor: Int,
        @ColorInt highlightBgColor: Int,
        isDark: Boolean
    ): CharSequence
}

/**
 * 문법 하이라이팅을 적용하지 않는 기본 텍스트 하이라이터.
 */
object PlainTextSyntaxHighlighter : SyntaxHighlighter {
    override fun highlight(
        spans: List<TextSpan>,
        @ColorInt defaultTextColor: Int,
        @ColorInt highlightBgColor: Int,
        isDark: Boolean
    ): CharSequence {
        val ssb = SpannableStringBuilder()
        for (span in spans) {
            val start = ssb.length
            ssb.append(span.text)
            val end = ssb.length
            if (span.isHighlighted && highlightBgColor != Color.TRANSPARENT) {
                ssb.setSpan(
                    BackgroundColorSpan(highlightBgColor),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        if (ssb.isNotEmpty()) {
            ssb.setSpan(
                ForegroundColorSpan(defaultTextColor),
                0,
                ssb.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return ssb
    }
}

/**
 * Kotlin / Java 등 소스 코드 키워드, 문자열, 주석을 지원하는 기본 하이라이터.
 */
class DefaultKotlinSyntaxHighlighter : SyntaxHighlighter {

    private val keywordPattern = Pattern.compile(
        "\\b(val|var|fun|class|interface|object|enum|data|sealed|package|import|" +
                "if|else|when|for|while|do|return|break|continue|throw|try|catch|finally|" +
                "private|protected|public|internal|override|abstract|open|final|lateinit|by|is|as|in|out|suspend|companion)\\b"
    )
    private val stringPattern = Pattern.compile("\"(\\\\.|[^\"])*\"")
    private val commentPattern = Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/")
    private val numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?([fFL])?\\b")

    override fun highlight(
        spans: List<TextSpan>,
        @ColorInt defaultTextColor: Int,
        @ColorInt highlightBgColor: Int,
        isDark: Boolean
    ): CharSequence {
        val keywordColor = if (isDark) Color.parseColor("#CC7832") else Color.parseColor("#0033B3")
        val stringColor = if (isDark) Color.parseColor("#6A8759") else Color.parseColor("#067D17")
        val commentColor = if (isDark) Color.parseColor("#808080") else Color.parseColor("#8C8C8C")
        val numberColor = if (isDark) Color.parseColor("#6897BB") else Color.parseColor("#1750EB")

        val fullTextBuilder = StringBuilder()
        for (s in spans) {
            fullTextBuilder.append(s.text)
        }
        val fullText = fullTextBuilder.toString()
        val ssb = SpannableStringBuilder(fullText)

        // 1. 전체 기본 텍스트 색상 적용
        if (fullText.isNotEmpty()) {
            ssb.setSpan(
                ForegroundColorSpan(defaultTextColor),
                0,
                fullText.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // 2. Syntax Highlighting 스타일 적용
        applyRegexSpan(ssb, commentPattern, fullText) {
            ForegroundColorSpan(commentColor)
        }
        applyRegexSpan(ssb, stringPattern, fullText) {
            ForegroundColorSpan(stringColor)
        }
        applyRegexSpan(ssb, keywordPattern, fullText) {
            ForegroundColorSpan(keywordColor)
        }
        applyRegexSpan(ssb, keywordPattern, fullText) {
            StyleSpan(Typeface.BOLD)
        }
        applyRegexSpan(ssb, numberPattern, fullText) {
            ForegroundColorSpan(numberColor)
        }

        // 3. 인라인 Diff 하이라이트 배경 적용
        if (highlightBgColor != Color.TRANSPARENT) {
            var currentOffset = 0
            for (span in spans) {
                val spanLength = span.text.length
                if (span.isHighlighted && spanLength > 0) {
                    ssb.setSpan(
                        BackgroundColorSpan(highlightBgColor),
                        currentOffset,
                        currentOffset + spanLength,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                currentOffset += spanLength
            }
        }

        return ssb
    }

    private fun applyRegexSpan(
        ssb: SpannableStringBuilder,
        pattern: Pattern,
        text: String,
        createSpan: () -> Any
    ) {
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            ssb.setSpan(
                createSpan(),
                matcher.start(),
                matcher.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }
}
