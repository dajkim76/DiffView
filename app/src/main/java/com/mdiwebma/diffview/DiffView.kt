package com.mdiwebma.diffview

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mdiwebma.diffview.engine.DiffEngine
import com.mdiwebma.diffview.engine.KotlinDiffEngine
import com.mdiwebma.diffview.model.DiffDisplayItem
import com.mdiwebma.diffview.model.DiffMode
import com.mdiwebma.diffview.model.DiffResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Side-by-Side (Split) 및 Unified (통합 단일 열) 모드를 모두 지원하는
 * Android Studio 스타일의 DiffView 커스텀 뷰.
 */
class DiffView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var diffEngine: DiffEngine = KotlinDiffEngine()
    private val viewScope = CoroutineScope(Dispatchers.Main + Job())

    private var diffMode: DiffMode = DiffMode.SIDE_BY_SIDE
    private var originalTitleText: String = "Original"
    private var modifiedTitleText: String = "Modified"

    private var diffColors: DiffColors = DiffColors.defaultFor(context)
    private var isFoldingEnabled: Boolean = true
    private var contextLines: Int = 3
    private var foldingThreshold: Int = 8
    private val expandedFoldIds = mutableSetOf<Long>()

    private var currentOriginalText: String = ""
    private var currentModifiedText: String = ""
    private var currentDiffResult: DiffResult? = null
    private var diffJob: Job? = null

    // UI Elements
    private val headerLayout: LinearLayout
    private val leftHeaderBox: LinearLayout
    private val leftHeaderTitle: TextView
    private val rightHeaderBox: LinearLayout
    private val rightHeaderTitle: TextView
    private val unifiedHeaderBox: LinearLayout
    private val unifiedHeaderTitle: TextView

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
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, padVerticalPx, padHorizontalPx, padVerticalPx)
            gravity = Gravity.CENTER_VERTICAL
        }

        // --- Side-by-Side Left Header ---
        leftHeaderBox = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
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

        // --- Side-by-Side Right Header ---
        rightHeaderBox = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
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
        val rightHeaderInner = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        rightHeaderInner.addView(rightHeaderTitle)
        rightHeaderBox.addView(rightSpacer)
        rightHeaderBox.addView(rightHeaderInner)

        // --- Unified Mode Header ---
        unifiedHeaderBox = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = GONE
        }
        val oldGutterSpacer = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(gutterPx, LayoutParams.WRAP_CONTENT)
            text = "Old"
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }
        val newGutterSpacer = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(gutterPx, LayoutParams.WRAP_CONTENT)
            text = "New"
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }
        unifiedHeaderTitle = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            text = "Unified Changes (+ / -)"
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding((20 * density).toInt(), 0, 0, 0)
        }
        unifiedHeaderBox.addView(oldGutterSpacer)
        unifiedHeaderBox.addView(newGutterSpacer)
        unifiedHeaderBox.addView(unifiedHeaderTitle)

        // Stats Badges (+ / - / ~)
        statsLayout = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
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

        headerLayout.addView(leftHeaderBox)
        headerLayout.addView(centerHeaderDivider)
        headerLayout.addView(rightHeaderBox)
        headerLayout.addView(unifiedHeaderBox)
        headerLayout.addView(statsLayout)

        headerDivider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dividerPx)
        }

        // 3. RecyclerView
        recyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
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
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            visibility = GONE
        }

        contentContainer.addView(headerLayout)
        contentContainer.addView(headerDivider)
        contentContainer.addView(recyclerView)

        addView(contentContainer)
        addView(progressBar)

        updateHeaderMode()
        applyColors()
    }

    private fun createBadgeTextView(context: Context, color: Int): TextView {
        val density = context.resources.displayMetrics.density
        return TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
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
     * Diff 표시 모드 설정 ([DiffMode.SIDE_BY_SIDE] vs [DiffMode.UNIFIED]).
     */
    fun setDiffMode(mode: DiffMode) {
        if (this.diffMode != mode) {
            this.diffMode = mode
            adapter.resetScrollGroups()
            updateHeaderMode()
            estimateAndPreloadContentWidths()
            updateDisplayItems()
        }
    }

    fun getDiffMode(): DiffMode = diffMode

    private fun updateHeaderMode() {
        if (diffMode == DiffMode.SIDE_BY_SIDE) {
            leftHeaderBox.visibility = VISIBLE
            centerHeaderDivider.visibility = VISIBLE
            rightHeaderBox.visibility = VISIBLE
            unifiedHeaderBox.visibility = GONE
        } else {
            leftHeaderBox.visibility = GONE
            centerHeaderDivider.visibility = VISIBLE
            rightHeaderBox.visibility = GONE
            unifiedHeaderBox.visibility = VISIBLE
        }
    }

    /**
     * 원본과 수정본 소스코드를 설정하고 비동기로 Diff를 계산합니다.
     */
    fun setContent(original: String, modified: String) {
        currentOriginalText = original
        currentModifiedText = modified
        diffJob?.cancel()
        expandedFoldIds.clear()
        adapter.resetScrollGroups()
        estimateAndPreloadContentWidths()
        recyclerView.scrollToPosition(0)
        progressBar.visibility = VISIBLE

        diffJob = viewScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    diffEngine.calculateDiff(original, modified)
                }
                currentDiffResult = result
                updateStats(result)
                estimateAndPreloadContentWidths()
                updateDisplayItems()
            } finally {
                progressBar.visibility = GONE
            }
        }
    }

    /**
     * Monospace 폰트를 기준으로 각 사이드의 최대 라인 너비를 즉시 계산하여
     * 첫 번째 라인부터 완벽하게 드래그 가로 스크롤이 작동하도록 사전 설정합니다.
     */
    private fun estimateAndPreloadContentWidths() {
        val density = context.resources.displayMetrics.density
        val paint = Paint().apply {
            typeface = Typeface.MONOSPACE
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                adapter.textSizeSp,
                context.resources.displayMetrics
            )
        }
        val charWidth = paint.measureText("M")
        val paddingPx = (16 * density).toInt()

        val origMaxLen = currentOriginalText.lineSequence().maxOfOrNull { it.length } ?: 0
        val modMaxLen = currentModifiedText.lineSequence().maxOfOrNull { it.length } ?: 0

        val leftWidthPx = (origMaxLen * charWidth + paddingPx).toInt()
        val rightWidthPx = (modMaxLen * charWidth + paddingPx).toInt()
        val unifiedWidthPx = (max(origMaxLen, modMaxLen) * charWidth + paddingPx).toInt()

        adapter.leftSyncGroup.reportContentWidth(leftWidthPx)
        adapter.rightSyncGroup.reportContentWidth(rightWidthPx)
        adapter.unifiedSyncGroup.reportContentWidth(unifiedWidthPx)
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
        estimateAndPreloadContentWidths()
    }

    /**
     * Unchanged 블록 접기 설정 및 문맥 라인/임계치 조정.
     * @param contextLines 변경점 주변에 항상 표시할 앞/뒤 미변경 문맥 라인 수 (기본 3줄)
     * @param threshold 접기를 수행할 최소 미변경 라인 수 (기본 8줄)
     */
    fun setFoldingEnabled(enabled: Boolean, contextLines: Int = 3, threshold: Int = 8) {
        this.isFoldingEnabled = enabled
        this.contextLines = contextLines
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
            mode = diffMode,
            isFoldingEnabled = true,
            contextLines = contextLines,
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
            mode = diffMode,
            isFoldingEnabled = isFoldingEnabled,
            contextLines = contextLines,
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
        unifiedHeaderTitle.setTextColor(diffColors.headerTextColor)
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
