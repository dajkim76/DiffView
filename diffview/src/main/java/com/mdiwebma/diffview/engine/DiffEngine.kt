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

    /**
     * [oldText]와 [newText]의 표준 Git Patch 텍스트를 생성합니다.
     */
    fun generateGitPatch(
        originalFileName: String,
        modifiedFileName: String,
        oldText: String,
        newText: String,
        contextSize: Int = 3
    ): String
}

/**
 * Git Patch 문자열에서 추출된 원본 텍스트, 수정본 텍스트 및 파일명 정보.
 */
data class ParsedGitPatch(
    val originalText: String,
    val modifiedText: String,
    val originalFileName: String? = null,
    val modifiedFileName: String? = null
)

/**
 * 표준 Git Patch 문자열을 파싱하여 원본/수정본 텍스트 및 파일명을 복원하는 파서.
 */
object GitPatchParser {

    /**
     * 표준 Git Patch 문자열을 파싱하여 변경된 모든 파일의 [ParsedGitPatch] 목록을 반환합니다.
     */
    fun parse(gitPatchText: String): List<ParsedGitPatch> {
        if (gitPatchText.isBlank()) {
            return emptyList()
        }

        val lines = gitPatchText.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val result = mutableListOf<ParsedGitPatch>()

        var origLines = mutableListOf<String>()
        var modLines = mutableListOf<String>()
        var origFileName: String? = null
        var modFileName: String? = null
        var inHunk = false
        var hasFileHeader = false

        fun flushFile() {
            if (hasFileHeader || origLines.isNotEmpty() || modLines.isNotEmpty() || origFileName != null || modFileName != null) {
                result.add(
                    ParsedGitPatch(
                        originalText = origLines.joinToString("\n"),
                        modifiedText = modLines.joinToString("\n"),
                        originalFileName = origFileName,
                        modifiedFileName = modFileName
                    )
                )
                origLines = mutableListOf()
                modLines = mutableListOf()
                origFileName = null
                modFileName = null
                inHunk = false
                hasFileHeader = false
            }
        }

        for (line in lines) {
            if (line.startsWith("diff --git ")) {
                flushFile()
                hasFileHeader = true
                val parts = line.removePrefix("diff --git ").trim().split(" ")
                if (parts.size >= 2) {
                    origFileName = parts[0].removePrefix("a/").removePrefix("b/")
                    modFileName = parts[1].removePrefix("a/").removePrefix("b/")
                }
                continue
            }

            when {
                line.startsWith("--- ") -> {
                    val path = line.removePrefix("--- ").trim()
                    if (path != "/dev/null") {
                        origFileName = path.removePrefix("a/").removePrefix("b/")
                    }
                }

                line.startsWith("+++ ") -> {
                    val path = line.removePrefix("+++ ").trim()
                    if (path != "/dev/null") {
                        modFileName = path.removePrefix("a/").removePrefix("b/")
                    }
                }

                line.startsWith("@@ ") -> {
                    inHunk = true
                }

                inHunk -> {
                    when {
                        line.startsWith("+") -> {
                            modLines.add(line.substring(1))
                        }

                        line.startsWith("-") -> {
                            origLines.add(line.substring(1))
                        }

                        line.startsWith(" ") -> {
                            val content = line.substring(1)
                            origLines.add(content)
                            modLines.add(content)
                        }

                        line.startsWith("\\") -> {
                            // "\ No newline at end of file" 무시
                        }

                        line.isEmpty() -> {
                            origLines.add("")
                            modLines.add("")
                        }
                    }
                }
            }
        }

        flushFile()
        return result
    }

    /**
     * Git Patch 문자열에서 첫 번째 파일의 [ParsedGitPatch]를 파싱합니다.
     */
    fun parseFirst(gitPatchText: String): ParsedGitPatch? = parse(gitPatchText).firstOrNull()
}
