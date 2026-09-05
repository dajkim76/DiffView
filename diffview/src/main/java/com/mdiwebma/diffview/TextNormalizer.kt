package com.mdiwebma.diffview

import java.io.StringReader
import java.io.StringWriter
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

abstract class TextNormalizer(val name: String, val key: String, val extensionList: Array<String>) {
    abstract fun normalize(rawText: String): String

    companion object {
        // 더 보기 메뉴에서 참조한다.
        val normalizerList: MutableList<TextNormalizer> by lazy {
            mutableListOf(
                PlainTextNormalize,
                JsonTextNormalize,
                XmlTextNormalizer,
                YamlTextNormalizer,
                EnvTextNormalizer,
                SQLTextNormalizer
            )
        }

        fun replaceOrAddTextNormalizer(normalizer: TextNormalizer) {
            val index = normalizerList.indexOfFirst { it.key == normalizer.key }
            if (index >= 0) {
                normalizerList[index] = normalizer
            } else {
                normalizerList.add(normalizer)
            }
        }

        fun forFileName(fileName: String): TextNormalizer? {
            val dotIndex = fileName.lastIndexOf('.')
            if (dotIndex == -1 || dotIndex == fileName.length - 1) {
                return null
            }

            val extension = fileName.substring(dotIndex + 1).lowercase()
            return normalizerList.firstOrNull { it.extensionList.contains(extension) }
        }

        fun forKey(key: String): TextNormalizer? {
            return normalizerList.firstOrNull { it.key == key }
        }
    }
}

object PlainTextNormalize : TextNormalizer("Plain Text", "PLAIN", emptyArray()) {
    override fun normalize(rawText: String): String = rawText
}

object JsonTextNormalize : TextNormalizer("JSON", "JSON", arrayOf("json")) {
    override fun normalize(rawText: String): String {
        return runCatching {
            val trimmed = rawText.trim()
            if (trimmed.startsWith("{")) {
                org.json.JSONObject(trimmed).toString(2)
            } else if (trimmed.startsWith("[")) {
                org.json.JSONArray(trimmed).toString(2)
            } else rawText
        }.getOrDefault(rawText)
    }
}

object XmlTextNormalizer : TextNormalizer("XML", "XML", arrayOf("xml")) {
    private const val INTENT_SPACES: Int = 4

    override fun normalize(rawText: String): String {
        return runCatching {
            val src = StreamSource(StringReader(rawText.trim()))
            val out = StreamResult(StringWriter())

            val transformer = TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(OutputKeys.INDENT, "yes")
                setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
                setOutputProperty("{http://xml.apache.org/xslt}indent-amount", INTENT_SPACES.toString())
            }

            transformer.transform(src, out)
            var result = out.writer.toString().trim()

            // 1. <?xml ...?> 뒤에 강제로 개행을 1줄 추가
            result = result.replace(Regex("^(<\\?xml[^>]*\\?>)\\s*"), "$1\n")

            // 2. 태그 내부의 속성(Attribute)을 한 줄씩 분리
            // \n\n이 생기는 문제를 막기 위해 들여쓰기 캡처에 줄바꿈이 포함되지 않도록 [ \t]* 사용
            val tagRegex = Regex("^([ \\t]*)<([a-zA-Z0-9_:\\.-]+)(\\s+[^>]+?)(/?)>", RegexOption.MULTILINE)
            val attrRegex = Regex("([a-zA-Z0-9_:\\.-]+=(?:\"[^\"]*\"|'[^']*'))")

            result = tagRegex.replace(result) { match ->
                val indent = match.groupValues[1]
                val tagName = match.groupValues[2]
                val attrsStr = match.groupValues[3]
                val closing = match.groupValues[4]

                val attrs = attrRegex.findAll(attrsStr).map { it.value }.toList()
                if (attrs.isNotEmpty()) {
                    val attrIndent = indent + " ".repeat(INTENT_SPACES)
                    val formattedAttrs = attrs.joinToString("\n$attrIndent")
                    val closingStr = if (closing == "/") " />" else ">"
                    "$indent<$tagName\n$attrIndent$formattedAttrs$closingStr"
                } else {
                    match.value
                }
            }

            result
        }.getOrDefault(rawText) // 파싱 실패 시 원본 반환
    }
}

object YamlTextNormalizer : TextNormalizer("YAML / YML", "YAML", arrayOf("yaml", "yml")) {

    override fun normalize(rawText: String): String {
        val lines = rawText.lines()
        if (lines.isEmpty()) return rawText

        val rootNodes = parseYamlTree(lines)
        val sb = StringBuilder()
        renderYamlTree(rootNodes, 0, sb)
        return sb.toString().replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private data class ParsedLine(
        val indent: Int,
        val isListItem: Boolean,
        val key: String?,
        val value: String?,
        val comment: String?
    )

    private class YamlNode(
        val isListItem: Boolean,
        val key: String?,
        val value: String?,
        val comment: String?,
        val children: MutableList<YamlNode> = mutableListOf()
    )

    private fun parseYamlTree(lines: List<String>): List<YamlNode> {
        val parsedLines = mutableListOf<ParsedLine>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val expanded = line.replace("\t", "  ")
            val trimmedStart = expanded.trimStart()
            val indent = expanded.length - trimmedStart.length

            val (contentPart, commentPart) = splitComment(trimmedStart)

            if (contentPart.isEmpty()) {
                parsedLines.add(ParsedLine(indent, false, null, null, commentPart))
                continue
            }

            var isListItem = false
            var rest = contentPart
            if (rest.startsWith("-")) {
                isListItem = true
                rest = rest.substring(1).trimStart()
            }

            val colonIndex = findColonIndex(rest)
            val (key, value) = if (colonIndex != -1) {
                val k = rest.substring(0, colonIndex).trim()
                val v = rest.substring(colonIndex + 1).trim()
                k to if (v.isNotEmpty()) normalizeScalarValue(v) else null
            } else {
                if (isListItem) {
                    null to if (rest.isNotEmpty()) normalizeScalarValue(rest) else null
                } else {
                    rest to null
                }
            }

            parsedLines.add(
                ParsedLine(
                    indent = indent,
                    isListItem = isListItem,
                    key = key,
                    value = value,
                    comment = commentPart.ifEmpty { null }
                )
            )
        }

        return buildTree(parsedLines)
    }

    private fun buildTree(lines: List<ParsedLine>): List<YamlNode> {
        val rootNodes = mutableListOf<YamlNode>()
        val stack = mutableListOf<Pair<Int, YamlNode>>()

        for (p in lines) {
            val node = YamlNode(
                isListItem = p.isListItem,
                key = p.key,
                value = p.value,
                comment = p.comment
            )

            while (stack.isNotEmpty() && stack.last().first >= p.indent) {
                stack.removeAt(stack.size - 1)
            }

            if (stack.isEmpty()) {
                rootNodes.add(node)
            } else {
                stack.last().second.children.add(node)
            }

            stack.add(p.indent to node)
        }

        return sortNodesRecursively(rootNodes)
    }

    private fun sortNodesRecursively(nodes: List<YamlNode>): List<YamlNode> {
        // 모든 자식 노드들을 먼저 재귀 정렬
        for (node in nodes) {
            if (node.children.isNotEmpty()) {
                val sortedChildren = sortNodesRecursively(node.children)
                node.children.clear()
                node.children.addAll(sortedChildren)
            }
        }

        val result = mutableListOf<YamlNode>()
        var currentMapGroup = mutableListOf<YamlNode>()

        fun flushMapGroup() {
            if (currentMapGroup.isNotEmpty()) {
                currentMapGroup.sortBy { it.key ?: "" }
                result.addAll(currentMapGroup)
                currentMapGroup = mutableListOf()
            }
        }

        for (node in nodes) {
            if (node.isListItem) {
                flushMapGroup()
                result.add(node)
            } else if (node.key != null) {
                currentMapGroup.add(node)
            } else {
                flushMapGroup()
                result.add(node)
            }
        }
        flushMapGroup()
        return result
    }

    private fun renderYamlTree(nodes: List<YamlNode>, depth: Int, sb: StringBuilder) {
        val indentStr = "  ".repeat(depth)

        for (node in nodes) {
            val commentSuffix = if (node.comment != null) " ${node.comment}" else ""

            when {
                node.key == null && node.value == null && node.comment != null -> {
                    sb.appendLine("$indentStr${node.comment}")
                }

                node.isListItem -> {
                    if (node.key != null) {
                        // 리스트 항목 내 맵 객체: 자기 자신과 자식 맵 속성들을 함께 A-Z 정렬
                        val directProperties = mutableListOf<YamlNode>()
                        val nonPropertyChildren = mutableListOf<YamlNode>()

                        val selfProp = YamlNode(
                            isListItem = false,
                            key = node.key,
                            value = node.value,
                            comment = node.comment,
                            children = mutableListOf()
                        )
                        directProperties.add(selfProp)

                        for (child in node.children) {
                            if (!child.isListItem && child.key != null) {
                                directProperties.add(child)
                            } else {
                                nonPropertyChildren.add(child)
                            }
                        }

                        directProperties.sortBy { it.key ?: "" }

                        // 1번째 속성은 "- " 접두사와 함께 출력
                        val firstProp = directProperties.first()
                        val firstComment = if (firstProp.comment != null) " ${firstProp.comment}" else ""
                        if (firstProp.value != null) {
                            sb.appendLine("$indentStr- ${firstProp.key}: ${firstProp.value}$firstComment")
                        } else {
                            sb.appendLine("$indentStr- ${firstProp.key}:$firstComment")
                        }
                        if (firstProp.children.isNotEmpty()) {
                            renderYamlTree(firstProp.children, depth + 2, sb)
                        }

                        // 2번째 이후 속성들은 동일 객체 내부 속성이므로 "  "(2칸 들여쓰기)로 출력
                        for (i in 1 until directProperties.size) {
                            val prop = directProperties[i]
                            val propComment = if (prop.comment != null) " ${prop.comment}" else ""
                            if (prop.value != null) {
                                sb.appendLine("$indentStr  ${prop.key}: ${prop.value}$propComment")
                            } else {
                                sb.appendLine("$indentStr  ${prop.key}:$propComment")
                            }
                            if (prop.children.isNotEmpty()) {
                                renderYamlTree(prop.children, depth + 2, sb)
                            }
                        }

                        if (nonPropertyChildren.isNotEmpty()) {
                            renderYamlTree(nonPropertyChildren, depth + 1, sb)
                        }
                    } else {
                        // 단순 스칼라 리스트 항목 (- value)
                        if (node.value != null) {
                            sb.appendLine("$indentStr- ${node.value}$commentSuffix")
                        } else {
                            sb.appendLine("$indentStr-$commentSuffix")
                        }
                        if (node.children.isNotEmpty()) {
                            renderYamlTree(node.children, depth + 1, sb)
                        }
                    }
                }

                node.key != null -> {
                    if (node.value != null) {
                        sb.appendLine("$indentStr${node.key}: ${node.value}$commentSuffix")
                    } else {
                        sb.appendLine("$indentStr${node.key}:$commentSuffix")
                    }
                    if (node.children.isNotEmpty()) {
                        renderYamlTree(node.children, depth + 1, sb)
                    }
                }

                else -> {
                    if (node.value != null) {
                        sb.appendLine("$indentStr${node.value}$commentSuffix")
                    }
                    if (node.children.isNotEmpty()) {
                        renderYamlTree(node.children, depth + 1, sb)
                    }
                }
            }
        }
    }

    private fun normalizeScalarValue(rawVal: String): String {
        val trimmed = rawVal.trim()
        if (trimmed.isEmpty()) return ""

        // 1. 따옴표 감싸진 문자열: 단순 문자열이면 불필요한 따옴표 제거, 특수문자/예약어/공백 있으면 유지
        val isDoubleQuoted = trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2
        val isSingleQuoted = trimmed.startsWith("'") && trimmed.endsWith("'") && trimmed.length >= 2

        if (isDoubleQuoted || isSingleQuoted) {
            val inner = trimmed.substring(1, trimmed.length - 1)
            if (canSafelyUnquote(inner)) {
                return inner
            }
            return trimmed
        }

        // 2. 따옴표 없는 스칼라: boolean 정규화 (yes/no/on/off -> true/false)
        return when (trimmed.lowercase()) {
            "yes", "true", "on" -> "true"
            "no", "false", "off" -> "false"
            else -> trimmed
        }
    }

    private fun canSafelyUnquote(inner: String): Boolean {
        if (inner.isEmpty()) return false
        if (inner.first().isWhitespace() || inner.last().isWhitespace()) return false

        val lower = inner.lowercase()
        if (lower in setOf("true", "false", "yes", "no", "on", "off", "null", "~", "y", "n")) return false

        val forbiddenChars = setOf(':', '#', '@', '%', '&', '*', '?', '|', '>', '<', '[', ']', '{', '}', ',', '!', '\'', '"', '\\', '`')
        if (inner.any { it in forbiddenChars }) return false

        if (inner.startsWith("-") || inner.startsWith("?") || inner.startsWith(":")) return false

        // 숫자 형태(123, 1.0.0 등)는 문자열 타입 보존을 위해 따옴표 유지
        if (inner.matches(Regex("^[0-9]+(\\.[0-9]+)*$"))) return false

        return true
    }

    private fun findColonIndex(text: String): Int {
        var inSingleQuote = false
        var inDoubleQuote = false
        for (i in text.indices) {
            val c = text[i]
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote
            } else if (c == ':' && !inSingleQuote && !inDoubleQuote) {
                if (i + 1 == text.length || text[i + 1].isWhitespace()) {
                    return i
                }
            }
        }
        return -1
    }

    private fun splitComment(line: String): Pair<String, String> {
        var inSingleQuote = false
        var inDoubleQuote = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote
            } else if (c == '#' && !inSingleQuote && !inDoubleQuote) {
                val content = line.substring(0, i).trimEnd()
                val comment = line.substring(i).trim()
                return content to comment
            }
            i++
        }
        return line to ""
    }
}

object EnvTextNormalizer : TextNormalizer("ENV", "ENV", arrayOf("env")) {
    override fun normalize(rawText: String): String {
        return rawText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val separatorIndex = line.indexOf('=')
                if (separatorIndex != -1) {
                    val key = line.substring(0, separatorIndex).trim()
                    val value = line.substring(separatorIndex + 1).trim()
                    key to value
                } else null
            }
            .sortedBy { it.first } // Key 기준 A-Z 정렬
            .joinToString("\n") { (key, value) -> "$key=$value" }
    }
}

object SQLTextNormalizer : TextNormalizer("SQL", "SQL", arrayOf("sql")) {

    private val KEYWORDS = setOf(
        "SELECT", "FROM", "WHERE", "GROUP BY", "HAVING",
        "ORDER BY", "LEFT JOIN", "RIGHT JOIN", "INNER JOIN",
        "JOIN", "LIMIT", "OFFSET", "UNION", "VALUES", "SET",
        "INSERT INTO", "UPDATE", "DELETE FROM"
    )

    override fun normalize(rawText: String): String {
        // 1. 여러 줄 주석 제거 (/* ... */)
        var sql = rawText.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")

        // 2. 단일 행 주석 제거 (-- ...)
        sql = sql.replace(Regex("--.*$", RegexOption.MULTILINE), "")

        val tokens = tokenize(sql)
        val sb = StringBuilder()

        for (token in tokens) {
            when (token.type) {
                TokenType.STRING_LITERAL, TokenType.COMMENT -> {
                    // 문자열 리터럴이나 주석 내부는 공백/대소문자/줄바꿈을 100% 원본 그대로 보존
                    sb.append(token.text)
                }

                TokenType.SQL_CODE -> {
                    // 코드 영역에 대해서만 키워드 대문자화 및 줄바꿈 적용
                    sb.append(formatCodeSegment(token.text))
                }
            }
        }

        val result = sb.toString().trim()
        return if (result.isNotEmpty() && !result.endsWith(";")) {
            "$result;"
        } else {
            result
        }
    }

    private enum class TokenType { SQL_CODE, STRING_LITERAL, COMMENT }
    private data class Token(val type: TokenType, val text: String)

    // 상태 머신을 이용한 토큰 분리 (문자열, 주석, 일반 코드)
    private fun tokenize(sql: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val len = sql.length
        var i = 0
        var codeStart = 0

        fun flushCode(endIndex: Int) {
            if (endIndex > codeStart) {
                tokens.add(Token(TokenType.SQL_CODE, sql.substring(codeStart, endIndex)))
            }
        }

        while (i < len) {
            val c = sql[i]
            val nextC = if (i + 1 < len) sql[i + 1] else null

            // 1. 단일 인용부호 문자열 리터럴 ('...')
            if (c == '\'') {
                flushCode(i)
                val start = i
                i++
                while (i < len) {
                    if (sql[i] == '\'') {
                        // 연속 두 개('')는 Escape
                        if (i + 1 < len && sql[i + 1] == '\'') {
                            i += 2
                            continue
                        }
                        i++ // 닫는 따옴표 포함
                        break
                    }
                    i++
                }
                tokens.add(Token(TokenType.STRING_LITERAL, sql.substring(start, i)))
                codeStart = i
            }
            // 2. 단일 줄 주석 (-- ...)
            else if (c == '-' && nextC == '-') {
                flushCode(i)
                val start = i
                while (i < len && sql[i] != '\n') i++
                tokens.add(Token(TokenType.COMMENT, sql.substring(start, i)))
                codeStart = i
            }
            // 3. 다중 줄 주석 (/* ... */)
            else if (c == '/' && nextC == '*') {
                flushCode(i)
                val start = i
                i += 2
                while (i + 1 < len && !(sql[i] == '*' && sql[i + 1] == '/')) i++
                i = (i + 2).coerceAtMost(len)
                tokens.add(Token(TokenType.COMMENT, sql.substring(start, i)))
                codeStart = i
            } else {
                i++
            }
        }
        flushCode(len)
        return tokens
    }

    // 순수 코드 영역만 안전하게 키워드 변환, 연산자/세미콜론 정규화 및 줄바꿈 처리
    private fun formatCodeSegment(code: String): String {
        var result = code.replace(Regex("\\s+"), " ")

        // 1. 연산자 앞뒤 공백 정규화 (<=, >=, !=, <>, =, <, >)
        val operatorRegex = Regex("\\s*(<=|>=|!=|<>|=|<|>)\\s*")
        result = operatorRegex.replace(result, " $1 ")

        // 2. 세미콜론 앞 공백 제거 및 뒤 개행 정규화
        result = result.replace(Regex("\\s*;\\s*"), ";\n\n")

        // 3. 다중 공백 정리
        result = result.replace(Regex("[ \\t]+"), " ")

        // 4. 예약어 매칭 및 대문자화, 절 단위 개행
        for (kw in KEYWORDS) {
            val pattern = Regex("(?i)\\b$kw\\b")
            result = pattern.replace(result) { "\n${kw.uppercase()} " }
        }

        // 5. 쉼표 뒤 공백 정규화
        result = result.replace(Regex(",\\s*"), ",\n  ")

        // 6. 3줄 이상의 연속 개행 정리
        result = result.replace(Regex("\n{3,}"), "\n\n")
        return result
    }
}
