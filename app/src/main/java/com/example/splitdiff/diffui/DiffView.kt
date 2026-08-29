package com.example.splitdiff.diffui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.splitdiff.engine.DiffEngine
import com.example.splitdiff.engine.KotlinDiffEngine
import com.example.splitdiff.model.DiffDisplayItem
import com.example.splitdiff.model.DiffResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Studio 스타일의 Side-by-Side DiffView Android 커스텀 뷰 컴포넌트.
 */
class DiffView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var diffEngine: DiffEngine = KotlinDiffEngine()
    private val viewScope = CoroutineScope(Dispatchers.Main + Job())

    private var originalTitleText: String = "Original"
    private var modifiedTitleText: String = "Modified"

    private var diffColors: DiffColors = DiffColors.defaultFor(context)
    private var isFoldingEnabled: Boolean = true
    private var foldingThreshold: Int = 5
    private val expandedFoldIds = mutableSetOf<Long>()

    private var currentDiffResult: DiffResult? = null
    private var diffJob: Job? = null

    // UI Elements
    private val headerLayout: LinearLayout
    private val leftHeaderTitle: TextView
    private val rightHeaderTitle: TextView
    private val statsLayout: LinearLayout
    private val addedBadge: TextView
    private val deletedBadge: TextView
    private val modifiedBadge: TextView
    private val headerDivider: View
    private val centerHeaderDivider: View

    private val recyclerView: RecyclerView
    private val adapter: DiffViewAdapter
    private val progressBar: ProgressBar

    init {
        val density = context.resources.displayMetrics.density
        val gutterPx = (42 * density).toInt()
        val dividerPx = (1 * density).toInt().coerceAtLeast(1)
        val padHorizontalPx = (8 * density).toInt()
        val padVerticalPx = (8 * density).toInt()

        // 1. Root Container
        val contentContainer = LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            orientation = LinearLayout.VERTICAL
        }

        // 2. Header Layout
        headerLayout = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, padVerticalPx, padHorizontalPx, padVerticalPx)
            gravity = Gravity.CENTER_VERTICAL
        }

        // Left Header
        val leftHeaderBox = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val leftSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(gutterPx, 1)
        }
        leftHeaderTitle = TextView(context).apply {
            text = originalTitleText
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(padHorizontalPx, 0, 0, 0)
        }
        leftHeaderBox.addView(leftSpacer)
        leftHeaderBox.addView(leftHeaderTitle)

        centerHeaderDivider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dividerPx, (18 * density).toInt())
        }

        // Right Header
        val rightHeaderBox = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val rightSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(gutterPx, 1)
        }
        rightHeaderTitle = TextView(context).apply {
            text = modifiedTitleText
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(padHorizontalPx, 0, 0, 0)
        }
        statsLayout = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = padHorizontalPx
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        addedBadge = createBadgeTextView(context, Color.parseColor("#4CAF50"))
        deletedBadge = createBadgeTextView(context, Color.parseColor("#E53935"))
        modifiedBadge = createBadgeTextView(context, Color.parseColor("#2196F3"))
        statsLayout.addView(addedBadge)
        statsLayout.addView(deletedBadge)
        statsLayout.addView(modifiedBadge)

        val rightHeaderInner = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        rightHeaderInner.addView(rightHeaderTitle)

        rightHeaderBox.addView(rightSpacer)
        rightHeaderBox.addView(rightHeaderInner)
        rightHeaderBox.addView(statsLayout)

        headerLayout.addView(leftHeaderBox)
        headerLayout.addView(centerHeaderDivider)
        headerLayout.addView(rightHeaderBox)

        headerDivider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dividerPx)
        }

        // 3. RecyclerView
        recyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            layoutManager = LinearLayoutManager(context)
        }

        adapter = DiffViewAdapter(
            onToggleFold = { foldId ->
                toggleFold(foldId)
            }
        )
        recyclerView.adapter = adapter

        // 4. Progress Indicator
        progressBar = ProgressBar(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            visibility = GONE
        }

        contentContainer.addView(headerLayout)
        contentContainer.addView(headerDivider)
        contentContainer.addView(recyclerView)

        addView(contentContainer)
        addView(progressBar)

        applyColors()
    }

    private fun createBadgeTextView(context: Context, color: Int): TextView {
        val density = context.resources.displayMetrics.density
        return TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = (6 * density).toInt()
            }
            setTextColor(color)
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            visibility = GONE
        }
    }

    /**
     * 원본과 수정본 소스코드를 설정하고 비동기로 Diff를 계산합니다.
     */
    fun setContent(original: String, modified: String) {
        diffJob?.cancel()
        expandedFoldIds.clear()
        progressBar.visibility = VISIBLE

        diffJob = viewScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    diffEngine.calculateDiff(original, modified)
                }
                currentDiffResult = result
                updateStats(result)
                updateDisplayItems()
            } finally {
                progressBar.visibility = GONE
            }
        }
    }

    /**
     * 커스텀 문법 하이라이터 설정.
     */
    fun setSyntaxHighlighter(highlighter: SyntaxHighlighter?) {
        adapter.syntaxHighlighter = highlighter ?: PlainTextSyntaxHighlighter
    }

    /**
     * 텍스트 폰트 크기 조절 (SP 단위).
     */
    fun setTextSize(sizeSp: Float) {
        adapter.textSizeSp = sizeSp
    }

    /**
     * Unchanged 블록 접기 설정 및 임계치 조정.
     */
    fun setFoldingEnabled(enabled: Boolean, threshold: Int = 5) {
        this.isFoldingEnabled = enabled
        this.foldingThreshold = threshold
        updateDisplayItems()
    }

    /**
     * 테마/색상 팔레트 설정.
     */
    fun setDiffColors(colors: DiffColors) {
        this.diffColors = colors
        applyColors()
    }

    /**
     * 헤더 타이틀 설정.
     */
    fun setHeaderTitles(original: String, modified: String) {
        this.originalTitleText = original
        this.modifiedTitleText = modified
        leftHeaderTitle.text = original
        rightHeaderTitle.text = modified
    }

    /**
     * 커스텀 DiffEngine 주입.
     */
    fun setDiffEngine(engine: DiffEngine) {
        this.diffEngine = engine
    }

    /**
     * 모든 접힌 블록 펼치기.
     */
    fun expandAll() {
        val result = currentDiffResult ?: return
        val items = FoldingManager.createDisplayItems(
            diffResult = result,
            isFoldingEnabled = true,
            foldingThreshold = foldingThreshold,
            expandedFoldIds = emptySet()
        ).filterIsInstance<DiffDisplayItem.FoldedHeader>()

        expandedFoldIds.addAll(items.map { it.id })
        updateDisplayItems()
    }

    /**
     * 모든 접힌 블록 다시 접기.
     */
    fun collapseAll() {
        expandedFoldIds.clear()
        updateDisplayItems()
    }

    private fun toggleFold(foldId: Long) {
        if (expandedFoldIds.contains(foldId)) {
            expandedFoldIds.remove(foldId)
        } else {
            expandedFoldIds.add(foldId)
        }
        updateDisplayItems()
    }

    private fun updateDisplayItems() {
        val items = FoldingManager.createDisplayItems(
            diffResult = currentDiffResult,
            isFoldingEnabled = isFoldingEnabled,
            foldingThreshold = foldingThreshold,
            expandedFoldIds = expandedFoldIds
        )
        adapter.submitList(items)
    }

    private fun updateStats(result: DiffResult) {
        if (result.addedCount > 0) {
            addedBadge.text = "+${result.addedCount}"
            addedBadge.visibility = VISIBLE
        } else {
            addedBadge.visibility = GONE
        }

        if (result.deletedCount > 0) {
            deletedBadge.text = "-${result.deletedCount}"
            deletedBadge.visibility = VISIBLE
        } else {
            deletedBadge.visibility = GONE
        }

        if (result.modifiedCount > 0) {
            modifiedBadge.text = "~${result.modifiedCount}"
            modifiedBadge.visibility = VISIBLE
        } else {
            modifiedBadge.visibility = GONE
        }
    }

    private fun applyColors() {
        setBackgroundColor(diffColors.unchangedBackground)
        headerLayout.setBackgroundColor(diffColors.headerBackground)
        leftHeaderTitle.setTextColor(diffColors.headerTextColor)
        rightHeaderTitle.setTextColor(diffColors.headerTextColor)
        headerDivider.setBackgroundColor(diffColors.dividerColor)
        centerHeaderDivider.setBackgroundColor(diffColors.dividerColor)

        val isDarkTheme = diffColors == DiffColors.Dark
        adapter.diffColors = diffColors
        adapter.isDark = isDarkTheme
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        diffJob?.cancel()
        viewScope.cancel()
    }
}
