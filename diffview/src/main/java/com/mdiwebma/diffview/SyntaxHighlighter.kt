package com.mdiwebma.diffview

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.annotation.ColorInt
import com.mdiwebma.diffview.model.TextSpan
import java.util.regex.Pattern

/**
 * 소스 코드 문법 하이라이팅 인터페이스 (Android View CharSequence 반환).
 */
abstract class SyntaxHighlighter(val name: String, val key: String, val extensionList: Array<String>) {
    /**
     * [spans] (인라인 diff span 목록)과 기본 [defaultTextColor], [highlightBgColor], [isDark] 테마 정보를 바탕으로
     * 하이라이팅된 [CharSequence]를 반환합니다.
     */
    abstract fun highlight(
        spans: List<TextSpan>,
        @ColorInt defaultTextColor: Int,
        @ColorInt highlightBgColor: Int,
        isDark: Boolean
    ): CharSequence

    companion object {
        val syntaxHighlighterList: MutableList<SyntaxHighlighter> by lazy {
            mutableListOf(
                PlainTextSyntaxHighlighter,
                KotlinSyntaxHighlighter,
                JavaSyntaxHighlighter,
                JavaScriptSyntaxHighlighter,
                PythonSyntaxHighlighter,
                CppSyntaxHighlighter,
                CSharpSyntaxHighlighter
            )
        }

        fun replaceOrAddSyntaxHighlighter(syntaxHighlighter: RegexSyntaxHighlighter) {
            val index = syntaxHighlighterList.indexOfFirst { it.key == syntaxHighlighter.key }
            if (index >= 0) {
                syntaxHighlighterList[index] = syntaxHighlighter
            } else {
                syntaxHighlighterList.add(syntaxHighlighter)
            }
        }

        /**
         * 파일 확장자(예: "kt", "java", "js", "py", "cpp", "cs")를 바탕으로 적절한 [SyntaxHighlighter]를 반환합니다.
         */
        fun forFileName(fileName: String): SyntaxHighlighter {
            val dotIndex = fileName.lastIndexOf('.')
            if (dotIndex == -1 || dotIndex == fileName.length - 1) {
                return PlainTextSyntaxHighlighter
            }
            val extension = fileName.substring(dotIndex + 1).lowercase()
            return syntaxHighlighterList.firstOrNull { it.extensionList.contains(extension) } ?: PlainTextSyntaxHighlighter
        }

        fun forKey(key: String): SyntaxHighlighter {
            return syntaxHighlighterList.firstOrNull { it.key == key } ?: PlainTextSyntaxHighlighter
        }
    }
}

/**
 * 문법 하이라이팅을 적용하지 않는 기본 텍스트 하이라이터.
 */
object PlainTextSyntaxHighlighter : SyntaxHighlighter("Plain Text", "PLAIN", emptyArray()) {
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
 * 정규식 기반 공통 소스 코드 문법 하이라이터 베이스 클래스.
 */
open class RegexSyntaxHighlighter(
    name: String, key: String, extensionList: Array<String>,
    val keywordPattern: Pattern? = null,
    val stringPattern: Pattern? = null,
    val commentPattern: Pattern? = null,
    val numberPattern: Pattern? = null,
    val preprocessorPattern: Pattern? = null,
    val annotationPattern: Pattern? = null
) : SyntaxHighlighter(name, key, extensionList) {

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
        val specialColor = if (isDark) Color.parseColor("#BBB529") else Color.parseColor("#871094")

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
        preprocessorPattern?.let { pattern ->
            applyRegexSpan(ssb, pattern, fullText) { ForegroundColorSpan(specialColor) }
        }
        annotationPattern?.let { pattern ->
            applyRegexSpan(ssb, pattern, fullText) { ForegroundColorSpan(specialColor) }
        }
        numberPattern?.let { pattern ->
            applyRegexSpan(ssb, pattern, fullText) { ForegroundColorSpan(numberColor) }
        }
        keywordPattern?.let { pattern ->
            applyRegexSpan(ssb, pattern, fullText) { ForegroundColorSpan(keywordColor) }
            applyRegexSpan(ssb, pattern, fullText) { StyleSpan(Typeface.BOLD) }
        }
        stringPattern?.let { pattern ->
            applyRegexSpan(ssb, pattern, fullText) { ForegroundColorSpan(stringColor) }
        }
        commentPattern?.let { pattern ->
            applyRegexSpan(ssb, pattern, fullText) { ForegroundColorSpan(commentColor) }
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

/**
 * Kotlin 소스 코드 문법 하이라이터.
 */
object KotlinSyntaxHighlighter : RegexSyntaxHighlighter(
    "Kotlin", "KOTLIN", arrayOf("kt", "kts"),
    keywordPattern = Pattern.compile(
        "\\b(val|var|fun|class|interface|object|enum|data|sealed|package|import|" +
                "if|else|when|for|while|do|return|break|continue|throw|try|catch|finally|" +
                "private|protected|public|internal|override|abstract|open|final|lateinit|by|is|as|in|out|suspend|companion|true|false|null)\\b"
    ),
    stringPattern = Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"|\"(\\\\.|[^\"])*\""),
    commentPattern = Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/"),
    numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?([fFL])?\\b"),
    annotationPattern = Pattern.compile("@[A-Za-z0-9_]+")
)

/**
 * Java 소스 코드 문법 하이라이터.
 */
object JavaSyntaxHighlighter : RegexSyntaxHighlighter(
    "Java", "JAVA", arrayOf("java"),
    keywordPattern = Pattern.compile(
        "\\b(public|protected|private|static|final|abstract|class|interface|enum|extends|implements|" +
                "package|import|new|this|super|return|if|else|for|while|do|switch|case|default|break|continue|" +
                "throw|throws|try|catch|finally|synchronized|volatile|transient|native|strictfp|instanceof|" +
                "void|boolean|byte|char|short|int|long|float|double|null|true|false|var|record|yield|sealed|permits|non-sealed)\\b"
    ),
    stringPattern = Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"|\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*'"),
    commentPattern = Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/"),
    numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?([fFdDlL])?\\b|0x[0-9a-fA-F]+"),
    annotationPattern = Pattern.compile("@[A-Za-z0-9_]+")
)

/**
 * JavaScript / TypeScript 소스 코드 문법 하이라이터.
 */
object JavaScriptSyntaxHighlighter : RegexSyntaxHighlighter(
    "JavaScript / TypeScript", "JS", arrayOf("js", "jsx", "ts", "tsx", "mjs", "cjs"),
    keywordPattern = Pattern.compile(
        "\\b(function|const|let|var|if|else|for|while|do|switch|case|default|break|continue|return|" +
                "try|catch|finally|throw|class|extends|super|this|new|typeof|instanceof|void|delete|in|of|" +
                "async|await|yield|import|export|from|as|null|undefined|true|false|debugger|type|interface)\\b"
    ),
    stringPattern = Pattern.compile("`[\\s\\S]*?`|\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*'"),
    commentPattern = Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/"),
    numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b|0x[0-9a-fA-F]+")
)

/**
 * Python 소스 코드 문법 하이라이터.
 */
object PythonSyntaxHighlighter : RegexSyntaxHighlighter(
    "Python", "PYTHON", arrayOf("py", "pyw"),
    keywordPattern = Pattern.compile(
        "\\b(def|class|if|elif|else|for|while|try|except|finally|with|as|import|from|return|yield|" +
                "break|continue|pass|raise|lambda|assert|global|nonlocal|and|or|not|is|in|True|False|None|self|async|await)\\b"
    ),
    stringPattern = Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*'"),
    commentPattern = Pattern.compile("#.*"),
    numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b|0x[0-9a-fA-F]+"),
    annotationPattern = Pattern.compile("@[A-Za-z0-9_.]+")
)

/**
 * C / C++ 소스 코드 문법 하이라이터.
 */
object CppSyntaxHighlighter : RegexSyntaxHighlighter(
    "C / C++", "CPP", arrayOf("cpp", "cxx", "cc", "c", "h", "hpp", "hxx"),
    keywordPattern = Pattern.compile(
        "\\b(auto|bool|break|case|catch|char|class|const|constexpr|continue|default|delete|do|double|" +
                "else|enum|explicit|export|extern|false|float|for|friend|goto|if|inline|int|long|mutable|" +
                "namespace|new|noexcept|nullptr|operator|private|protected|public|register|reinterpret_cast|" +
                "return|short|signed|sizeof|static|static_assert|static_cast|struct|switch|template|this|" +
                "thread_local|throw|true|try|typedef|typeid|typename|union|unsigned|using|virtual|void|volatile|wchar_t|while)\\b"
    ),
    stringPattern = Pattern.compile("R\"\\([\\s\\S]*?\\)\"|\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*'"),
    commentPattern = Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/"),
    numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?([fFlLuU])?\\b|0x[0-9a-fA-F]+"),
    preprocessorPattern = Pattern.compile("#\\s*(include|define|ifdef|ifndef|endif|if|else|elif|pragma|undef)\\b.*")
)

/**
 * C# 소스 코드 문법 하이라이터.
 */
object CSharpSyntaxHighlighter : RegexSyntaxHighlighter(
    "C#", "CSHARP", arrayOf("cs"),
    keywordPattern = Pattern.compile(
        "\\b(abstract|as|base|bool|break|byte|case|catch|char|checked|class|const|continue|decimal|default|" +
                "delegate|do|double|else|enum|event|explicit|extern|false|finally|fixed|float|for|foreach|goto|" +
                "if|implicit|in|int|interface|internal|is|lock|long|namespace|new|null|object|operator|out|" +
                "override|params|private|protected|public|readonly|ref|return|sbyte|sealed|short|sizeof|stackalloc|" +
                "static|string|struct|switch|this|throw|true|try|typeof|uint|ulong|unchecked|unsafe|ushort|using|" +
                "virtual|void|volatile|while|async|await|var|record|init|yield|get|set)\\b"
    ),
    stringPattern = Pattern.compile("@\"[^\"]*(\"\"[^\"]*)*\"|\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*'|\\$\"(?:[^\"\\\\{]|\\\\.)*\""),
    commentPattern = Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/"),
    numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?([fFdDmMlL])?\\b|0x[0-9a-fA-F]+"),
    annotationPattern = Pattern.compile("\\[[A-Za-z0-9_]+\\]")
)
