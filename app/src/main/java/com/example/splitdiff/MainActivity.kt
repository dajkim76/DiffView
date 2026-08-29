package com.example.splitdiff

import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.splitdiff.diffui.DiffColors
import com.example.splitdiff.diffui.DiffView
import com.example.splitdiff.ui.theme.SplitDiffTheme

class MainActivity : ComponentActivity() {

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
    var textSizeSp by remember { mutableFloatStateOf(12.5f) }
    var isFoldingEnabled by remember { mutableStateOf(true) }
    var diffViewInstance by remember { mutableStateOf<DiffView?>(null) }

    val presets = remember {
        listOf(
            "Kotlin Sample" to (SAMPLE_ORIGINAL to SAMPLE_MODIFIED),
            "Middle Insert/Delete" to (INSERT_DELETE_ORIGINAL to INSERT_DELETE_MODIFIED),
            "Long Line (Chars)" to (LONG_LINE_ORIGINAL to LONG_LINE_MODIFIED),
            "Large File (5,000L)" to createLargeSample(5000)
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 상단 컨트롤 바
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                // 프리셋 칩 목록
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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

                // 조작 버튼 (다크모드, 폰트크기, 접기/펼치기)
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
                            diffViewInstance?.setFoldingEnabled(isFoldingEnabled, 5)
                        },
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text(if (isFoldingEnabled) "Folding ON" else "Folding OFF", fontSize = 11.sp)
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

        // Android View 기반 DiffView 임베딩
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { context ->
                DiffView(context).apply {
                    val colors = if (isDark) DiffColors.Dark else DiffColors.Light
                    setDiffColors(colors)
                    setTextSize(textSizeSp)
                    setFoldingEnabled(isFoldingEnabled, 5)
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
                diffViewInstance = view
            }
        )
    }
}

// --- 샘플 데이터 정의 ---

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

    // 10줄 이상의 변경 없는 구간 (자동 접기 테스트)
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

    // 10줄 이상의 변경 없는 구간 (자동 접기 테스트)
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