package com.mdiwebma.diffview

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mdiwebma.diffview.model.DiffDisplayItem
import com.mdiwebma.diffview.model.DiffLine
import com.mdiwebma.diffview.model.DiffRow
import com.mdiwebma.diffview.model.DiffRowType

/**
 * Side-by-Side 및 Unified 모드를 모두 지원하며,
 * 뷰/컬럼 단위로 가로 스크롤이 동기화되는 Diff 어댑터.
 */
class DiffViewAdapter(
    val leftSyncGroup: HorizontalScrollSyncGroup = HorizontalScrollSyncGroup(),
    val rightSyncGroup: HorizontalScrollSyncGroup = HorizontalScrollSyncGroup(),
    val unifiedSyncGroup: HorizontalScrollSyncGroup = HorizontalScrollSyncGroup(),
    private val onToggleFold: (Long) -> Unit
) : ListAdapter<DiffDisplayItem, RecyclerView.ViewHolder>(DiffItemCallback) {

    private var _diffColors: DiffColors = DiffColors.Light
    var diffColors: DiffColors
        get() = _diffColors
        set(value) {
            _diffColors = value
            notifyDataSetChanged()
        }

    var syntaxHighlighter: SyntaxHighlighter = DefaultKotlinSyntaxHighlighter()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var textSizeSp: Float = 12.5f
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private var _isDark: Boolean = false
    var isDark: Boolean
        get() = _isDark
        set(value) {
            _isDark = value
            notifyDataSetChanged()
        }

    var gutterWidthDp: Int = 42

    /**
     * [diffColors]와 [isDark]를 한 번에 업데이트하여 [notifyDataSetChanged]를 한 번만 호출합니다.
     * [DiffView.applyColors]에서 두 값이 동시에 변경될 때 중복 리렌더링을 방지합니다.
     */
    fun applyTheme(diffColors: DiffColors, isDark: Boolean) {
        val colorsChanged = _diffColors != diffColors
        val darkChanged = _isDark != isDark
        if (!colorsChanged && !darkChanged) return
        _diffColors = diffColors
        _isDark = isDark
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is DiffDisplayItem.SideBySideRow -> TYPE_SIDE_BY_SIDE_ROW
            is DiffDisplayItem.UnifiedRow -> TYPE_UNIFIED_ROW
            is DiffDisplayItem.FoldedHeader -> TYPE_FOLDED_HEADER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_SIDE_BY_SIDE_ROW -> DiffRowViewHolder.create(parent.context, gutterWidthDp, leftSyncGroup, rightSyncGroup)
            TYPE_UNIFIED_ROW -> UnifiedRowViewHolder.create(parent.context, gutterWidthDp, unifiedSyncGroup)
            TYPE_FOLDED_HEADER -> FoldedHeaderViewHolder.create(parent.context, onToggleFold)
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is DiffDisplayItem.SideBySideRow -> {
                (holder as DiffRowViewHolder).bind(
                    row = item.diffRow,
                    colors = diffColors,
                    highlighter = syntaxHighlighter,
                    textSizeSp = textSizeSp,
                    isDark = isDark
                )
            }

            is DiffDisplayItem.UnifiedRow -> {
                (holder as UnifiedRowViewHolder).bind(
                    item = item,
                    colors = diffColors,
                    highlighter = syntaxHighlighter,
                    textSizeSp = textSizeSp,
                    isDark = isDark
                )
            }

            is DiffDisplayItem.FoldedHeader -> {
                (holder as FoldedHeaderViewHolder).bind(
                    item = item,
                    colors = diffColors,
                    textSizeSp = textSizeSp
                )
            }
        }
    }

    fun resetScrollGroups() {
        leftSyncGroup.reset()
        rightSyncGroup.reset()
        unifiedSyncGroup.reset()
    }

    companion object {
        private const val TYPE_SIDE_BY_SIDE_ROW = 0
        private const val TYPE_UNIFIED_ROW = 1
        private const val TYPE_FOLDED_HEADER = 2
    }

    object DiffItemCallback : DiffUtil.ItemCallback<DiffDisplayItem>() {
        override fun areItemsTheSame(oldItem: DiffDisplayItem, newItem: DiffDisplayItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DiffDisplayItem, newItem: DiffDisplayItem): Boolean {
            return oldItem == newItem
        }
    }
}

/**
 * Side-by-Side (Split) 모드용 ViewHolder.
 */
class DiffRowViewHolder(
    itemView: View,
    private val leftContainer: LinearLayout,
    private val leftGutterText: TextView,
    private val leftGutterDivider: View,
    private val leftScrollView: SyncHorizontalScrollView,
    private val leftCodeText: TextView,
    private val centerDivider: View,
    private val rightContainer: LinearLayout,
    private val rightGutterText: TextView,
    private val rightGutterDivider: View,
    private val rightScrollView: SyncHorizontalScrollView,
    private val rightCodeText: TextView,
    private val leftSyncGroup: HorizontalScrollSyncGroup,
    private val rightSyncGroup: HorizontalScrollSyncGroup
) : RecyclerView.ViewHolder(itemView) {

    fun bind(
        row: DiffRow,
        colors: DiffColors,
        highlighter: SyntaxHighlighter,
        textSizeSp: Float,
        isDark: Boolean
    ) {
        leftScrollView.syncGroup = leftSyncGroup
        rightScrollView.syncGroup = rightSyncGroup
        leftScrollView.scrollTo(leftSyncGroup.currentScrollX, 0)
        rightScrollView.scrollTo(rightSyncGroup.currentScrollX, 0)
        leftScrollView.applyContentMinWidth(leftSyncGroup.maxContentWidth)
        rightScrollView.applyContentMinWidth(rightSyncGroup.maxContentWidth)

        leftGutterText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        leftCodeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        rightGutterText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        rightCodeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)

        centerDivider.setBackgroundColor(colors.dividerColor)
        leftGutterDivider.setBackgroundColor(colors.dividerColor)
        rightGutterDivider.setBackgroundColor(colors.dividerColor)

        val leftBg = when (row.type) {
            DiffRowType.DELETED -> colors.deletedBackground
            DiffRowType.MODIFIED -> colors.modifiedBackground
            else -> colors.unchangedBackground
        }
        val leftHighlightBg = when (row.type) {
            DiffRowType.DELETED -> colors.deletedHighlight
            DiffRowType.MODIFIED -> colors.modifiedHighlight
            else -> Color.TRANSPARENT
        }

        val rightBg = when (row.type) {
            DiffRowType.INSERTED -> colors.addedBackground
            DiffRowType.MODIFIED -> colors.modifiedBackground
            else -> colors.unchangedBackground
        }
        val rightHighlightBg = when (row.type) {
            DiffRowType.INSERTED -> colors.addedHighlight
            DiffRowType.MODIFIED -> colors.modifiedHighlight
            else -> Color.TRANSPARENT
        }

        bindSide(
            line = row.left,
            container = leftContainer,
            gutterText = leftGutterText,
            codeText = leftCodeText,
            bgColor = leftBg,
            highlightColor = leftHighlightBg,
            colors = colors,
            highlighter = highlighter,
            isDark = isDark,
            sideLabel = "Original"
        )

        bindSide(
            line = row.right,
            container = rightContainer,
            gutterText = rightGutterText,
            codeText = rightCodeText,
            bgColor = rightBg,
            highlightColor = rightHighlightBg,
            colors = colors,
            highlighter = highlighter,
            isDark = isDark,
            sideLabel = "Modified"
        )
    }

    private fun bindSide(
        line: DiffLine?,
        container: LinearLayout,
        gutterText: TextView,
        codeText: TextView,
        bgColor: Int,
        highlightColor: Int,
        colors: DiffColors,
        highlighter: SyntaxHighlighter,
        isDark: Boolean,
        sideLabel: String
    ) {
        container.setBackgroundColor(bgColor)
        gutterText.setBackgroundColor(colors.lineNumberBackground)
        gutterText.setTextColor(colors.lineNumberTextColor)
        codeText.setTextColor(colors.codeTextColor)

        if (line != null) {
            gutterText.text = line.lineNumber?.toString() ?: ""
            val highlighted = highlighter.highlight(
                spans = line.spans,
                defaultTextColor = colors.codeTextColor,
                highlightBgColor = highlightColor,
                isDark = isDark
            )
            codeText.text = highlighted
            container.contentDescription = "$sideLabel line ${line.lineNumber}: ${line.content}"
        } else {
            gutterText.text = ""
            codeText.text = ""
            container.contentDescription = "$sideLabel empty line"
        }
    }

    companion object {
        fun create(
            context: Context,
            gutterWidthDp: Int,
            leftSyncGroup: HorizontalScrollSyncGroup,
            rightSyncGroup: HorizontalScrollSyncGroup
        ): DiffRowViewHolder {
            val density = context.resources.displayMetrics.density
            val gutterPx = (gutterWidthDp * density).toInt()
            val dividerPx = (1 * density).toInt().coerceAtLeast(1)
            val padHorizontalPx = (6 * density).toInt()
            val padVerticalPx = (3 * density).toInt()

            val rootLayout = LinearLayout(context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
            }

            val leftContainer = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                orientation = LinearLayout.HORIZONTAL
            }
            val leftGutterText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(gutterPx, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                typeface = Typeface.MONOSPACE
                setPadding(0, padVerticalPx, padHorizontalPx, padVerticalPx)
            }
            val leftGutterDivider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dividerPx, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            val leftCodeText = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                typeface = Typeface.MONOSPACE
                setSingleLine(true)
                setTextIsSelectable(true)
                setPadding(padHorizontalPx, padVerticalPx, padHorizontalPx, padVerticalPx)
            }
            val leftScrollView = SyncHorizontalScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                isFillViewport = true
                scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                addView(leftCodeText)
            }
            leftContainer.addView(leftGutterText)
            leftContainer.addView(leftGutterDivider)
            leftContainer.addView(leftScrollView)

            val centerDivider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dividerPx, ViewGroup.LayoutParams.MATCH_PARENT)
            }

            val rightContainer = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                orientation = LinearLayout.HORIZONTAL
            }
            val rightGutterText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(gutterPx, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                typeface = Typeface.MONOSPACE
                setPadding(0, padVerticalPx, padHorizontalPx, padVerticalPx)
            }
            val rightGutterDivider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dividerPx, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            val rightCodeText = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                typeface = Typeface.MONOSPACE
                setSingleLine(true)
                setTextIsSelectable(true)
                setPadding(padHorizontalPx, padVerticalPx, padHorizontalPx, padVerticalPx)
            }
            val rightScrollView = SyncHorizontalScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                isFillViewport = true
                scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                addView(rightCodeText)
            }
            rightContainer.addView(rightGutterText)
            rightContainer.addView(rightGutterDivider)
            rightContainer.addView(rightScrollView)

            rootLayout.addView(leftContainer)
            rootLayout.addView(centerDivider)
            rootLayout.addView(rightContainer)

            return DiffRowViewHolder(
                itemView = rootLayout,
                leftContainer = leftContainer,
                leftGutterText = leftGutterText,
                leftGutterDivider = leftGutterDivider,
                leftScrollView = leftScrollView,
                leftCodeText = leftCodeText,
                centerDivider = centerDivider,
                rightContainer = rightContainer,
                rightGutterText = rightGutterText,
                rightGutterDivider = rightGutterDivider,
                rightScrollView = rightScrollView,
                rightCodeText = rightCodeText,
                leftSyncGroup = leftSyncGroup,
                rightSyncGroup = rightSyncGroup
            )
        }
    }
}

/**
 * Unified (통합 단일 열) 모드용 ViewHolder.
 */
class UnifiedRowViewHolder(
    itemView: View,
    private val rootContainer: LinearLayout,
    private val oldGutterText: TextView,
    private val newGutterText: TextView,
    private val gutterDivider: View,
    private val prefixText: TextView,
    private val scrollView: SyncHorizontalScrollView,
    private val codeText: TextView,
    private val unifiedSyncGroup: HorizontalScrollSyncGroup
) : RecyclerView.ViewHolder(itemView) {

    fun bind(
        item: DiffDisplayItem.UnifiedRow,
        colors: DiffColors,
        highlighter: SyntaxHighlighter,
        textSizeSp: Float,
        isDark: Boolean
    ) {
        scrollView.syncGroup = unifiedSyncGroup
        scrollView.scrollTo(unifiedSyncGroup.currentScrollX, 0)
        scrollView.applyContentMinWidth(unifiedSyncGroup.maxContentWidth)

        oldGutterText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        newGutterText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        prefixText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        codeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)

        gutterDivider.setBackgroundColor(colors.dividerColor)
        oldGutterText.setBackgroundColor(colors.lineNumberBackground)
        oldGutterText.setTextColor(colors.lineNumberTextColor)
        newGutterText.setBackgroundColor(colors.lineNumberBackground)
        newGutterText.setTextColor(colors.lineNumberTextColor)

        oldGutterText.text = item.oldLineNumber?.toString() ?: ""
        newGutterText.text = item.newLineNumber?.toString() ?: ""
        prefixText.text = item.prefix

        val bgColor = when (item.type) {
            DiffRowType.DELETED -> colors.deletedBackground
            DiffRowType.INSERTED -> colors.addedBackground
            DiffRowType.MODIFIED -> colors.modifiedBackground
            else -> colors.unchangedBackground
        }

        val highlightBg = when (item.type) {
            DiffRowType.DELETED -> colors.deletedHighlight
            DiffRowType.INSERTED -> colors.addedHighlight
            DiffRowType.MODIFIED -> colors.modifiedHighlight
            else -> Color.TRANSPARENT
        }

        val prefixColor = when (item.type) {
            DiffRowType.DELETED -> Color.parseColor("#E53935")
            DiffRowType.INSERTED -> Color.parseColor("#4CAF50")
            else -> colors.lineNumberTextColor
        }

        rootContainer.setBackgroundColor(bgColor)
        prefixText.setTextColor(prefixColor)
        codeText.setTextColor(colors.codeTextColor)

        val highlighted = highlighter.highlight(
            spans = item.spans,
            defaultTextColor = colors.codeTextColor,
            highlightBgColor = highlightBg,
            isDark = isDark
        )
        codeText.text = highlighted

        val typeDesc = when (item.type) {
            DiffRowType.DELETED -> "deleted"
            DiffRowType.INSERTED -> "added"
            else -> "unchanged"
        }
        rootContainer.contentDescription = "Line ${item.oldLineNumber ?: ""}/${item.newLineNumber ?: ""} $typeDesc: ${item.content}"
    }

    companion object {
        fun create(
            context: Context,
            gutterWidthDp: Int,
            unifiedSyncGroup: HorizontalScrollSyncGroup
        ): UnifiedRowViewHolder {
            val density = context.resources.displayMetrics.density
            val gutterPx = (gutterWidthDp * density).toInt()
            val dividerPx = (1 * density).toInt().coerceAtLeast(1)
            val padHorizontalPx = (6 * density).toInt()
            val padVerticalPx = (3 * density).toInt()
            val prefixPx = (16 * density).toInt()

            val rootLayout = LinearLayout(context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
            }

            val oldGutterText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(gutterPx, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                typeface = Typeface.MONOSPACE
                setPadding(0, padVerticalPx, padHorizontalPx, padVerticalPx)
            }

            val newGutterText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(gutterPx, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                typeface = Typeface.MONOSPACE
                setPadding(0, padVerticalPx, padHorizontalPx, padVerticalPx)
            }

            val gutterDivider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dividerPx, ViewGroup.LayoutParams.MATCH_PARENT)
            }

            val prefixText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(prefixPx, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = Gravity.CENTER
                typeface = Typeface.MONOSPACE
            }

            val codeText = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                typeface = Typeface.MONOSPACE
                setSingleLine(true)
                setTextIsSelectable(true)
                setPadding(padHorizontalPx, padVerticalPx, padHorizontalPx, padVerticalPx)
            }

            val scrollView = SyncHorizontalScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                isFillViewport = true
                scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                addView(codeText)
            }

            rootLayout.addView(oldGutterText)
            rootLayout.addView(newGutterText)
            rootLayout.addView(gutterDivider)
            rootLayout.addView(prefixText)
            rootLayout.addView(scrollView)

            return UnifiedRowViewHolder(
                itemView = rootLayout,
                rootContainer = rootLayout,
                oldGutterText = oldGutterText,
                newGutterText = newGutterText,
                gutterDivider = gutterDivider,
                prefixText = prefixText,
                scrollView = scrollView,
                codeText = codeText,
                unifiedSyncGroup = unifiedSyncGroup
            )
        }
    }
}

/**
 * 접힌 Unchanged Block을 나타내는 ViewHolder.
 */
class FoldedHeaderViewHolder(
    itemView: View,
    private val bannerText: TextView,
    private val onToggleFold: (Long) -> Unit
) : RecyclerView.ViewHolder(itemView) {

    private var currentItem: DiffDisplayItem.FoldedHeader? = null

    init {
        itemView.setOnClickListener {
            currentItem?.let { onToggleFold(it.id) }
        }
    }

    fun bind(
        item: DiffDisplayItem.FoldedHeader,
        colors: DiffColors,
        textSizeSp: Float
    ) {
        currentItem = item
        itemView.setBackgroundColor(colors.foldedBannerBackground)
        bannerText.setTextColor(colors.foldedBannerTextColor)
        bannerText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp - 1f)

        val rangeLeft = if (item.startLineLeft != null && item.endLineLeft != null) {
            "L${item.startLineLeft}~L${item.endLineLeft}"
        } else ""
        val rangeRight = if (item.startLineRight != null && item.endLineRight != null) {
            "R${item.startLineRight}~R${item.endLineRight}"
        } else ""

        bannerText.text = "⋯ ${item.lineCount} unchanged lines ($rangeLeft / $rangeRight - Click to expand) ⋯"
    }

    companion object {
        fun create(context: Context, onToggleFold: (Long) -> Unit): FoldedHeaderViewHolder {
            val density = context.resources.displayMetrics.density
            val padVerticalPx = (6 * density).toInt()
            val padHorizontalPx = (16 * density).toInt()

            val root = FrameLayout(context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(padHorizontalPx, padVerticalPx, padHorizontalPx, padVerticalPx)
                isClickable = true
                isFocusable = true
            }

            val textView = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
            }

            root.addView(textView)
            return FoldedHeaderViewHolder(root, textView, onToggleFold)
        }
    }
}
