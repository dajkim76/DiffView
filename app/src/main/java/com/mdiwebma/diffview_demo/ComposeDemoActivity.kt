package com.mdiwebma.diffview_demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mdiwebma.diffview.DiffColors
import com.mdiwebma.diffview.DiffView
import com.mdiwebma.diffview.model.DiffGranularity
import com.mdiwebma.diffview.model.DiffMode
import com.mdiwebma.diffview_demo.ui.theme.SplitDiffTheme

class ComposeDemoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var isDarkTheme by remember { mutableStateOf(false) }

            SplitDiffTheme(darkTheme = isDarkTheme) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DiffDemoScreen(
                        isDark = isDarkTheme,
                        onToggleDark = { isDarkTheme = !isDarkTheme },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun DiffDemoScreen(
    isDark: Boolean,
    onToggleDark: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPreset by remember { mutableStateOf(0) }
    var diffMode by remember { mutableStateOf(DiffMode.SIDE_BY_SIDE) }
    var textSizeSp by remember { mutableFloatStateOf(12.5f) }
    var isFoldingEnabled by remember { mutableStateOf(true) }
    var whitespaceMode by remember { mutableStateOf(com.mdiwebma.diffview.model.WhitespaceIgnoreMode.NONE) }
    var diffGranularity by remember { mutableStateOf(DiffGranularity.WORD) }
    var isLineWrap by remember { mutableStateOf(false) }
    var isSyntaxKotlin by remember { mutableStateOf(false) }
    var diffViewInstance by remember { mutableStateOf<DiffView?>(null) }

    val presets = remember {
        listOf(
            "Kotlin Sample" to (SAMPLE_ORIGINAL to SAMPLE_MODIFIED),
            "Whitespace Sample" to (WHITESPACE_ORIGINAL to WHITESPACE_MODIFIED),
            "Middle Insert/Delete" to (INSERT_DELETE_ORIGINAL to INSERT_DELETE_MODIFIED),
            "Long Line (Chars)" to (LONG_LINE_ORIGINAL to LONG_LINE_MODIFIED),
            "Large File (5,000L)" to createLargeSample(5000)
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = diffMode == DiffMode.SIDE_BY_SIDE,
                        onClick = {
                            diffMode = DiffMode.SIDE_BY_SIDE
                            diffViewInstance?.setDiffMode(DiffMode.SIDE_BY_SIDE)
                        },
                        label = { Text("Side-by-Side (Split)", fontSize = 12.sp) }
                    )

                    FilterChip(
                        selected = diffMode == DiffMode.UNIFIED,
                        onClick = {
                            diffMode = DiffMode.UNIFIED
                            diffViewInstance?.setDiffMode(DiffMode.UNIFIED)
                        },
                        label = { Text("Unified (위아래 표시)", fontSize = 12.sp) }
                    )

                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))

                    presets.forEachIndexed { index, (title, _) ->
                        FilterChip(
                            selected = selectedPreset == index,
                            onClick = {
                                selectedPreset = index
                                val (orig, mod) = presets[index].second
                                diffViewInstance?.setContent(orig, mod)
                            },
                            label = { Text(title, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onToggleDark,
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text(if (isDark) "Light Mode" else "Dark Mode", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            textSizeSp = (textSizeSp + 1f).coerceAtMost(20f)
                            diffViewInstance?.setTextSize(textSizeSp)
                        },
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text("Font +", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            textSizeSp = (textSizeSp - 1f).coerceAtLeast(8f)
                            diffViewInstance?.setTextSize(textSizeSp)
                        },
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text("Font -", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            isFoldingEnabled = !isFoldingEnabled
                            diffViewInstance?.setFoldingEnabled(isFoldingEnabled, contextLines = 3, threshold = 8)
                        },
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text(if (isFoldingEnabled) "Folding ON" else "Folding OFF", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            whitespaceMode = when (whitespaceMode) {
                                com.mdiwebma.diffview.model.WhitespaceIgnoreMode.NONE -> com.mdiwebma.diffview.model.WhitespaceIgnoreMode.TRIM_LEADING_TRAILING
                                com.mdiwebma.diffview.model.WhitespaceIgnoreMode.TRIM_LEADING_TRAILING -> com.mdiwebma.diffview.model.WhitespaceIgnoreMode.COLLAPSE_WHITESPACE
                                com.mdiwebma.diffview.model.WhitespaceIgnoreMode.COLLAPSE_WHITESPACE -> com.mdiwebma.diffview.model.WhitespaceIgnoreMode.IGNORE_ALL
                                com.mdiwebma.diffview.model.WhitespaceIgnoreMode.IGNORE_ALL -> com.mdiwebma.diffview.model.WhitespaceIgnoreMode.NONE
                            }
                            diffViewInstance?.setWhitespaceIgnoreMode(whitespaceMode)
                        },
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        val wsLabel = when (whitespaceMode) {
                            com.mdiwebma.diffview.model.WhitespaceIgnoreMode.NONE -> "WS: None"
                            com.mdiwebma.diffview.model.WhitespaceIgnoreMode.TRIM_LEADING_TRAILING -> "WS: Trim"
                            com.mdiwebma.diffview.model.WhitespaceIgnoreMode.COLLAPSE_WHITESPACE -> "WS: Collapse"
                            com.mdiwebma.diffview.model.WhitespaceIgnoreMode.IGNORE_ALL -> "WS: Ignore All"
                        }
                        Text(wsLabel, fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            diffGranularity = if (diffGranularity == DiffGranularity.WORD) {
                                DiffGranularity.CHARACTER
                            } else {
                                DiffGranularity.WORD
                            }
                            diffViewInstance?.setDiffGranularity(diffGranularity)
                        },
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        val unitLabel = if (diffGranularity == DiffGranularity.WORD) "Unit: Word" else "Unit: Char"
                        Text(unitLabel, fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            isLineWrap = !isLineWrap
                            diffViewInstance?.setLineWrap(isLineWrap)
                        },
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text(if (isLineWrap) "Wrap: ON" else "Wrap: OFF", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            isSyntaxKotlin = !isSyntaxKotlin
                            val highlighter = if (isSyntaxKotlin) com.mdiwebma.diffview.DefaultKotlinSyntaxHighlighter() else com.mdiwebma.diffview.PlainTextSyntaxHighlighter
                            diffViewInstance?.setSyntaxHighlighter(highlighter)
                        },
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text(if (isSyntaxKotlin) "Syntax: Kotlin" else "Syntax: None", fontSize = 11.sp)
                    }

                    Button(
                        onClick = { diffViewInstance?.expandAll() },
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text("Expand All", fontSize = 11.sp)
                    }

                    Button(
                        onClick = { diffViewInstance?.collapseAll() },
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text("Collapse All", fontSize = 11.sp)
                    }
                }
            }
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { context ->
                DiffView(context).apply {
                    val colors = if (isDark) DiffColors.Dark else DiffColors.Light
                    setDiffColors(colors)
                    setTextSize(textSizeSp)
                    setDiffMode(diffMode)
                    setFoldingEnabled(isFoldingEnabled, contextLines = 3, threshold = 8)
                    setWhitespaceIgnoreMode(whitespaceMode)
                    setDiffGranularity(diffGranularity)
                    setLineWrap(isLineWrap)
                    setHeaderTitles("Original Code", "Modified Code")
                    val (orig, mod) = presets[selectedPreset].second
                    setContent(orig, mod)
                    diffViewInstance = this
                }
            },
            update = { view ->
                val colors = if (isDark) DiffColors.Dark else DiffColors.Light
                view.setDiffColors(colors)
                view.setTextSize(textSizeSp)
                view.setDiffMode(diffMode)
                view.setWhitespaceIgnoreMode(whitespaceMode)
                view.setDiffGranularity(diffGranularity)
                view.setLineWrap(isLineWrap)
                diffViewInstance = view
            }
        )
    }
}

private val WHITESPACE_ORIGINAL = """
val a = 1
    val indentDiff = 2
val multipleSpaces     =     3
val exactSame = 4
val allWhitespaceIgnored = "hello world"
""".trimIndent()

private val WHITESPACE_MODIFIED = """
val a = 1
val indentDiff = 2
val multipleSpaces = 3
val exactSame = 4
val   all   Whitespace   Ignored = "hello world"
""".trimIndent()

private val SAMPLE_ORIGINAL = """
package com.example.splitdiff

import java.util.Date

class UserProfile(
    val id: Long,
    val name: String,
    val age: Int,
    val city: String = "Seoul"
) {
    fun printInfo() {
        println("User: ${'$'}name, Age: ${'$'}age")
        println("Created at: ${'$'}{Date()}")
    }

    fun helper1() = 1
    fun helper2() = 2
    fun helper3() = 3
    fun helper4() = 4
    fun helper5() = 5
    fun helper6() = 6
    fun helper7() = 7
    fun helper8() = 8
    fun helper9() = 9
    fun helper10() = 10

    fun calculateDiscount(price: Double): Double {
        val discountRate = 0.10
        val finalPrice = price * (1.0 - discountRate)
        println("Old discount logic")
        return finalPrice
    }
}
""".trimIndent()

private val SAMPLE_MODIFIED = """
package com.example.splitdiff

import java.util.Date
import java.time.Instant

data class UserProfile(
    val id: Long,
    val name: String,
    val age: Int,
    val email: String? = null,
    val city: String = "Jeju"
) {
    fun printInfo() {
        println("User: ${'$'}name, Age: ${'$'}age, Email: ${'$'}email")
        println("Created at: ${'$'}{Instant.now()}")
    }

    fun helper1() = 1
    fun helper2() = 2
    fun helper3() = 3
    fun helper4() = 4
    fun helper5() = 5
    fun helper6() = 6
    fun helper7() = 7
    fun helper8() = 8
    fun helper9() = 9
    fun helper10() = 10

    fun calculateDiscount(price: Double, isVip: Boolean = false): Double {
        val discountRate = if (isVip) 0.20 else 0.10
        val finalPrice = price * (1.0 - discountRate)
        return finalPrice
    }
}
""".trimIndent()

private val INSERT_DELETE_ORIGINAL = """
Item 1: unchanged
Item 2: unchanged
Item 3: will be deleted
Item 4: will be modified old text
Item 5: unchanged
""".trimIndent()

private val INSERT_DELETE_MODIFIED = """
Item 1: unchanged
Item 2: unchanged
Item 2.5: inserted item
Item 4: will be modified new text
Item 5: unchanged
Item 6: added at bottom
""".trimIndent()

private val LONG_LINE_ORIGINAL = """
val shortLine = "hello"
val extremelyLongLine = "PREFIX_" + "THIS_IS_A_VERY_LONG_LINE_WITH_LOTS_OF_CHARACTERS_FOR_HORIZONTAL_SCROLLING_TEST_1234567890_ABCDEFGHIJKLMNOPQRSTUVWXYZ_".repeat(20) + "_SUFFIX_OLD"
val endLine = "done"
""".trimIndent()

private val LONG_LINE_MODIFIED = """
val shortLine = "hello"
val extremelyLongLine = "PREFIX_" + "THIS_IS_A_VERY_LONG_LINE_WITH_LOTS_OF_CHARACTERS_FOR_HORIZONTAL_SCROLLING_TEST_1234567890_ABCDEFGHIJKLMNOPQRSTUVWXYZ_".repeat(20) + "_SUFFIX_NEW"
val endLine = "done"
""".trimIndent()

private fun createLargeSample(count: Int): Pair<String, String> {
    val oldLines = (1..count).map { "val item_$it = \"OldValue_$it\"" }
    val newLines = oldLines.toMutableList().apply {
        this[10] = "val item_11 = \"NewModifiedValue_11\""
        this.add(25, "val insertedItem = \"Inserted\"")
        this.removeAt(100)
    }
    return oldLines.joinToString("\n") to newLines.joinToString("\n")
}
