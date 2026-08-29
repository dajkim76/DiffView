package com.example.splitdiff

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.splitdiff.diffui.DiffColors
import com.example.splitdiff.diffui.DiffView
import com.example.splitdiff.model.DiffMode

/**
 * Jetpack Compose를 사용하지 않는 순수 Android View 기반 DiffView 데모 액티비티.
 */
class DemoActivity : ComponentActivity() {

    private lateinit var diffView: DiffView
    private var isDarkMode = false
    private var currentMode = DiffMode.SIDE_BY_SIDE
    private var textSizeSp = 12.5f
    private var isFoldingEnabled = true
    private var selectedPresetIndex = 0

    private val presetButtons = mutableListOf<Button>()
    private lateinit var modeSplitButton: Button
    private lateinit var modeUnifiedButton: Button
    private lateinit var themeButton: Button
    private lateinit var foldingButton: Button

    private val presets = listOf(
        "Kotlin Sample" to (SAMPLE_ORIGINAL to SAMPLE_MODIFIED),
        "Middle Insert/Delete" to (INSERT_DELETE_ORIGINAL to INSERT_DELETE_MODIFIED),
        "Long Line (Chars)" to (LONG_LINE_ORIGINAL to LONG_LINE_MODIFIED),
        "Large File (5,000L)" to createLargeSample(5000)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val density = resources.displayMetrics.density
        val pad8Px = (8 * density).toInt()
        val pad4Px = (4 * density).toInt()

        // Root Container
        val rootLayout = LinearLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F7"))
        }

        // Window Insets (Edge to edge padding)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        // --- 상단 컨트롤 패널 ---
        val controlContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(pad8Px, pad8Px, pad8Px, pad8Px)
            setBackgroundColor(Color.parseColor("#ECEFF1"))
        }

        // Row 1: Mode + Presets
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val row1Scroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = false
            addView(row1)
        }

        // Mode 토글 버튼
        modeSplitButton = createPillButton("Side-by-Side (Split)", isSelected = true) {
            currentMode = DiffMode.SIDE_BY_SIDE
            diffView.setDiffMode(DiffMode.SIDE_BY_SIDE)
            updateModeButtons()
        }
        modeUnifiedButton = createPillButton("Unified (위아래 표시)", isSelected = false) {
            currentMode = DiffMode.UNIFIED
            diffView.setDiffMode(DiffMode.UNIFIED)
            updateModeButtons()
        }

        row1.addView(modeSplitButton)
        row1.addView(modeUnifiedButton)

        // 구분자
        row1.addView(createVerticalBar())

        // Preset 버튼들
        presets.forEachIndexed { index, (title, _) ->
            val btn = createPillButton(title, isSelected = (index == 0)) {
                selectedPresetIndex = index
                updatePresetButtons()
                val (orig, mod) = presets[index].second
                diffView.setContent(orig, mod)
            }
            presetButtons.add(btn)
            row1.addView(btn)
        }

        // Row 2: Controls (Dark mode, Font, Folding, Expand/Collapse)
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val row2Scroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = pad4Px
            }
            isHorizontalScrollBarEnabled = false
            addView(row2)
        }

        themeButton = createOutlineButton("Dark Mode") {
            isDarkMode = !isDarkMode
            themeButton.text = if (isDarkMode) "Light Mode" else "Dark Mode"
            val colors = if (isDarkMode) DiffColors.Dark else DiffColors.Light
            diffView.setDiffColors(colors)
            controlContainer.setBackgroundColor(if (isDarkMode) Color.parseColor("#1E1F22") else Color.parseColor("#ECEFF1"))
            rootLayout.setBackgroundColor(if (isDarkMode) Color.parseColor("#141416") else Color.parseColor("#F5F5F7"))
        }

        val fontPlusButton = createOutlineButton("Font +") {
            textSizeSp = (textSizeSp + 1f).coerceAtMost(20f)
            diffView.setTextSize(textSizeSp)
        }

        val fontMinusButton = createOutlineButton("Font -") {
            textSizeSp = (textSizeSp - 1f).coerceAtLeast(8f)
            diffView.setTextSize(textSizeSp)
        }

        foldingButton = createOutlineButton("Folding ON") {
            isFoldingEnabled = !isFoldingEnabled
            foldingButton.text = if (isFoldingEnabled) "Folding ON" else "Folding OFF"
            diffView.setFoldingEnabled(isFoldingEnabled, 5)
        }

        val expandAllButton = createSolidButton("Expand All") {
            diffView.expandAll()
        }

        val collapseAllButton = createSolidButton("Collapse All") {
            diffView.collapseAll()
        }

        row2.addView(themeButton)
        row2.addView(fontPlusButton)
        row2.addView(fontMinusButton)
        row2.addView(foldingButton)
        row2.addView(expandAllButton)
        row2.addView(collapseAllButton)

        controlContainer.addView(row1Scroll)
        controlContainer.addView(row2Scroll)

        // --- DiffView (Custom View) ---
        diffView = DiffView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setDiffColors(if (isDarkMode) DiffColors.Dark else DiffColors.Light)
            setTextSize(textSizeSp)
            setDiffMode(currentMode)
            setFoldingEnabled(isFoldingEnabled, 5)
            setHeaderTitles("Original Code", "Modified Code")
            val (orig, mod) = presets[selectedPresetIndex].second
            setContent(orig, mod)
        }

        rootLayout.addView(controlContainer)
        rootLayout.addView(diffView)

        setContentView(rootLayout)
    }

    private fun createPillButton(text: String, isSelected: Boolean, onClick: () -> Unit): Button {
        val density = resources.displayMetrics.density
        return Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (36 * density).toInt()
            ).apply {
                marginEnd = (6 * density).toInt()
            }
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
            applyPillStyle(this, isSelected)
            setOnClickListener { onClick() }
        }
    }

    private fun applyPillStyle(button: Button, isSelected: Boolean) {
        val density = resources.displayMetrics.density
        val bg = GradientDrawable().apply {
            cornerRadius = 18 * density
            if (isSelected) {
                setColor(Color.parseColor("#2196F3"))
            } else {
                setColor(Color.parseColor("#E0E0E0"))
                setStroke((1 * density).toInt(), Color.parseColor("#BDBDBD"))
            }
        }
        button.background = bg
        button.setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#333333"))
    }

    private fun createOutlineButton(text: String, onClick: () -> Unit): Button {
        val density = resources.displayMetrics.density
        return Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (34 * density).toInt()
            ).apply {
                marginEnd = (6 * density).toInt()
            }
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)
            val bg = GradientDrawable().apply {
                cornerRadius = 6 * density
                setColor(Color.WHITE)
                setStroke((1 * density).toInt(), Color.parseColor("#90A4AE"))
            }
            background = bg
            setTextColor(Color.parseColor("#37474F"))
            setOnClickListener { onClick() }
        }
    }

    private fun createSolidButton(text: String, onClick: () -> Unit): Button {
        val density = resources.displayMetrics.density
        return Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (34 * density).toInt()
            ).apply {
                marginEnd = (6 * density).toInt()
            }
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)
            val bg = GradientDrawable().apply {
                cornerRadius = 6 * density
                setColor(Color.parseColor("#455A64"))
            }
            background = bg
            setTextColor(Color.WHITE)
            setOnClickListener { onClick() }
        }
    }

    private fun createVerticalBar(): View {
        val density = resources.displayMetrics.density
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                (1 * density).toInt().coerceAtLeast(1),
                (22 * density).toInt()
            ).apply {
                marginStart = (4 * density).toInt()
                marginEnd = (8 * density).toInt()
            }
            setBackgroundColor(Color.parseColor("#B0BEC5"))
        }
    }

    private fun updateModeButtons() {
        applyPillStyle(modeSplitButton, currentMode == DiffMode.SIDE_BY_SIDE)
        applyPillStyle(modeUnifiedButton, currentMode == DiffMode.UNIFIED)
    }

    private fun updatePresetButtons() {
        presetButtons.forEachIndexed { index, button ->
            applyPillStyle(button, index == selectedPresetIndex)
        }
    }

    companion object {
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
    }
}
