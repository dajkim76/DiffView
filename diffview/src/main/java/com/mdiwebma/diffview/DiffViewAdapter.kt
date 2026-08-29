package com.mdiwebma.diffview

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
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

    var syntaxHighlighter: SyntaxHighlighter = PlainTextSyntaxHighlighter
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var textSizeSp: Float = 12.5f
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var isLineWrap: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    var showDiffSymbols: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    var isTextSelectable: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    var diffLabels: DiffLabels = DiffLabels.Default
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

    var gutterWidthDp: Int = 48
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

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
            TYPE_FOLDED_HEADER -> FoldedHeaderViewHolder.create(parent.context, gutterWidthDp, onToggleFold)
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
                    isDark = isDark,
                    isLineWrap = isLineWrap,
                    showDiffSymbols = showDiffSymbols,
                    gutterWidthDp = gutterWidthDp,
                    isTextSelectable = isTextSelectable
                )
            }

            is DiffDisplayItem.UnifiedRow -> {
                (holder as UnifiedRowViewHolder).bind(
                    item = item,
                    colors = diffColors,
                    highlighter = syntaxHighlighter,
                    textSizeSp = textSizeSp,
                    isDark = isDark,
                    isLineWrap = isLineWrap,
                    gutterWidthDp = gutterWidthDp,
                    isTextSelectable = isTextSelectable
                )
            }

            is DiffDisplayItem.FoldedHeader -> {
                (holder as FoldedHeaderViewHolder).bind(
                    item = item,
                    colors = diffColors,
                    labels = diffLabels,
                    textSizeSp = textSizeSp,
                    gutterWidthDp = gutterWidthDp
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
        isDark: Boolean,
        isLineWrap: Boolean = false,
        showDiffSymbols: Boolean = true,
        gutterWidthDp: Int = 48,
        isTextSelectable: Boolean = false
    ) {
        val density = leftGutterText.context.resources.displayMetrics.density
        val gutterPx = (gutterWidthDp * density).toInt()
        if (leftGutterText.layoutParams.width != gutterPx) {
            leftGutterText.layoutParams = leftGutterText.layoutParams.apply { width = gutterPx }
        }
        if (rightGutterText.layoutParams.width != gutterPx) {
            rightGutterText.layoutParams = rightGutterText.layoutParams.apply { width = gutterPx }
        }

        leftCodeText.setTextIsSelectable(isTextSelectable)
        rightCodeText.setTextIsSelectable(isTextSelectable)

        leftScrollView.isLineWrap = isLineWrap
        rightScrollView.isLineWrap = isLineWrap

        leftCodeText.isSingleLine = !isLineWrap
        leftCodeText.gravity = Gravity.TOP or Gravity.START
        rightCodeText.isSingleLine = !isLineWrap
        rightCodeText.gravity = Gravity.TOP or Gravity.START

        leftScrollView.syncGroup = if (isLineWrap) null else leftSyncGroup
        rightScrollView.syncGroup = if (isLineWrap) null else rightSyncGroup
        if (!isLineWrap) {
            leftScrollView.scrollTo(leftSyncGroup.currentScrollX, 0)
            rightScrollView.scrollTo(rightSyncGroup.currentScrollX, 0)
            leftScrollView.applyContentMinWidth(leftSyncGroup.maxContentWidth)
            rightScrollView.applyContentMinWidth(rightSyncGroup.maxContentWidth)
        } else {
            leftScrollView.scrollTo(0, 0)
            rightScrollView.scrollTo(0, 0)
            leftScrollView.applyContentMinWidth(0)
            rightScrollView.applyContentMinWidth(0)
        }

        val gutterGravity = if (isLineWrap) Gravity.END or Gravity.TOP else Gravity.END or Gravity.CENTER_VERTICAL
        leftGutterText.gravity = gutterGravity
        rightGutterText.gravity = gutterGravity

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

        val leftGutterBg = when (row.type) {
            DiffRowType.DELETED -> colors.deletedBackground
            DiffRowType.MODIFIED -> colors.modifiedBackground
            else -> colors.lineNumberBackground
        }

        val rightGutterBg = when (row.type) {
            DiffRowType.INSERTED -> colors.addedBackground
            DiffRowType.MODIFIED -> colors.modifiedBackground
            else -> colors.lineNumberBackground
        }

        val leftSymbol = if (showDiffSymbols) {
            when (row.type) {
                DiffRowType.DELETED, DiffRowType.MODIFIED -> " -"
                else -> "  "
            }
        } else ""

        val rightSymbol = if (showDiffSymbols) {
            when (row.type) {
                DiffRowType.INSERTED, DiffRowType.MODIFIED -> " +"
                else -> "  "
            }
        } else ""

        bindSide(
            line = row.left,
            container = leftContainer,
            gutterText = leftGutterText,
            codeText = leftCodeText,
            bgColor = leftBg,
            gutterBgColor = leftGutterBg,
            highlightColor = leftHighlightBg,
            colors = colors,
            highlighter = highlighter,
            isDark = isDark,
            isLineWrap = isLineWrap,
            sideLabel = "Original",
            symbol = leftSymbol
        )

        bindSide(
            line = row.right,
            container = rightContainer,
            gutterText = rightGutterText,
            codeText = rightCodeText,
            bgColor = rightBg,
            gutterBgColor = rightGutterBg,
            highlightColor = rightHighlightBg,
            colors = colors,
            highlighter = highlighter,
            isDark = isDark,
            isLineWrap = isLineWrap,
            sideLabel = "Modified",
            symbol = rightSymbol
        )
    }

    private fun bindSide(
        line: DiffLine?,
        container: LinearLayout,
        gutterText: TextView,
        codeText: TextView,
        bgColor: Int,
        gutterBgColor: Int,
        highlightColor: Int,
        colors: DiffColors,
        highlighter: SyntaxHighlighter,
        isDark: Boolean,
        isLineWrap: Boolean,
        sideLabel: String,
        symbol: String = ""
    ) {
        if (line != null) {
            container.setBackgroundColor(bgColor)
            gutterText.setBackgroundColor(gutterBgColor)
            gutterText.setTextColor(colors.lineNumberTextColor)
            codeText.setTextColor(colors.codeTextColor)
            val lineNum = line.lineNumber?.toString() ?: ""
            gutterText.text = if (lineNum.isNotEmpty()) "$lineNum$symbol" else ""
            val highlighted = highlighter.highlight(
                spans = line.spans,
                defaultTextColor = colors.codeTextColor,
                highlightBgColor = highlightColor,
                isDark = isDark
            )
            codeText.text = if (isLineWrap) formatWrappedText(highlighted) else highlighted
            container.contentDescription = "$sideLabel line ${line.lineNumber}: ${line.content}"
        } else {
            container.setBackgroundColor(colors.noneTextBackground)
            gutterText.setBackgroundColor(colors.noneTextBackground)
            gutterText.setTextColor(colors.lineNumberTextColor)
            codeText.setTextColor(colors.codeTextColor)
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
                gravity = Gravity.TOP
                isBaselineAligned = false
            }

            val leftContainer = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }
            val leftGutterText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(gutterPx, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                typeface = Typeface.MONOSPACE
                setSingleLine(true)
                setPadding(0, padVerticalPx, padHorizontalPx, padVerticalPx)
            }
            val leftGutterDivider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dividerPx, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            val leftCodeText = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START
                )
                gravity = Gravity.TOP or Gravity.START
                typeface = Typeface.MONOSPACE
                setSingleLine(true)
                setPadding(padHorizontalPx, padVerticalPx, padHorizontalPx, padVerticalPx)
            }
            val leftScrollView = SyncHorizontalScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
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
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }
            val rightGutterText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(gutterPx, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                typeface = Typeface.MONOSPACE
                setSingleLine(true)
                setPadding(0, padVerticalPx, padHorizontalPx, padVerticalPx)
            }
            val rightGutterDivider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dividerPx, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            val rightCodeText = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START
                )
                gravity = Gravity.TOP or Gravity.START
                typeface = Typeface.MONOSPACE
                setSingleLine(true)
                setPadding(padHorizontalPx, padVerticalPx, padHorizontalPx, padVerticalPx)
            }
            val rightScrollView = SyncHorizontalScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
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
        isDark: Boolean,
        isLineWrap: Boolean = false,
        gutterWidthDp: Int = 48,
        isTextSelectable: Boolean = false
    ) {
        val density = oldGutterText.context.resources.displayMetrics.density
        val gutterPx = (gutterWidthDp * density).toInt()
        if (oldGutterText.layoutParams.width != gutterPx) {
            oldGutterText.layoutParams = oldGutterText.layoutParams.apply { width = gutterPx }
        }
        if (newGutterText.layoutParams.width != gutterPx) {
            newGutterText.layoutParams = newGutterText.layoutParams.apply { width = gutterPx }
        }

        codeText.setTextIsSelectable(isTextSelectable)
        scrollView.isLineWrap = isLineWrap
        codeText.isSingleLine = !isLineWrap
        codeText.gravity = Gravity.TOP or Gravity.START

        scrollView.syncGroup = if (isLineWrap) null else unifiedSyncGroup
        if (!isLineWrap) {
            scrollView.scrollTo(unifiedSyncGroup.currentScrollX, 0)
            scrollView.applyContentMinWidth(unifiedSyncGroup.maxContentWidth)
        } else {
            scrollView.scrollTo(0, 0)
            scrollView.applyContentMinWidth(0)
        }

        val gutterGravity = if (isLineWrap) Gravity.END or Gravity.TOP else Gravity.END or Gravity.CENTER_VERTICAL
        val prefixGravity = if (isLineWrap) Gravity.CENTER_HORIZONTAL or Gravity.TOP else Gravity.CENTER
        oldGutterText.gravity = gutterGravity
        newGutterText.gravity = gutterGravity
        prefixText.gravity = prefixGravity

        oldGutterText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        newGutterText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        prefixText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        codeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)

        gutterDivider.setBackgroundColor(colors.dividerColor)

        oldGutterText.text = item.oldLineNumber?.toString() ?: ""
        newGutterText.text = item.newLineNumber?.toString() ?: ""
        prefixText.text = item.prefix

        val bgColor = when (item.type) {
            DiffRowType.DELETED -> colors.deletedBackground
            DiffRowType.INSERTED -> colors.addedBackground
            DiffRowType.MODIFIED -> colors.modifiedBackground
            else -> colors.unchangedBackground
        }

        val gutterBg = when (item.type) {
            DiffRowType.DELETED -> colors.deletedBackground
            DiffRowType.INSERTED -> colors.addedBackground
            DiffRowType.MODIFIED -> colors.modifiedBackground
            else -> colors.lineNumberBackground
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
        oldGutterText.setBackgroundColor(gutterBg)
        oldGutterText.setTextColor(colors.lineNumberTextColor)
        newGutterText.setBackgroundColor(gutterBg)
        newGutterText.setTextColor(colors.lineNumberTextColor)
        prefixText.setTextColor(prefixColor)
        codeText.setTextColor(colors.codeTextColor)

        val highlighted = highlighter.highlight(
            spans = item.spans,
            defaultTextColor = colors.codeTextColor,
            highlightBgColor = highlightBg,
            isDark = isDark
        )
        codeText.text = if (isLineWrap) formatWrappedText(highlighted) else highlighted

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
                gravity = Gravity.TOP
                isBaselineAligned = false
            }

            val oldGutterText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(gutterPx, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                typeface = Typeface.MONOSPACE
                setSingleLine(true)
                setPadding(0, padVerticalPx, padHorizontalPx, padVerticalPx)
            }

            val newGutterText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(gutterPx, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                typeface = Typeface.MONOSPACE
                setSingleLine(true)
                setPadding(0, padVerticalPx, padHorizontalPx, padVerticalPx)
            }

            val gutterDivider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dividerPx, ViewGroup.LayoutParams.MATCH_PARENT)
            }

            val prefixText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(prefixPx, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = Gravity.CENTER
                typeface = Typeface.MONOSPACE
                setPadding(0, padVerticalPx, 0, padVerticalPx)
            }

            val codeText = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START
                )
                gravity = Gravity.TOP or Gravity.START
                typeface = Typeface.MONOSPACE
                setSingleLine(true)
                setPadding(padHorizontalPx, padVerticalPx, padHorizontalPx, padVerticalPx)
            }

            val scrollView = SyncHorizontalScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
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
    private val leftGutterText: TextView,
    private val gutterDivider: View,
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
        labels: DiffLabels,
        textSizeSp: Float,
        gutterWidthDp: Int = 48
    ) {
        val density = leftGutterText.context.resources.displayMetrics.density
        val gutterPx = (gutterWidthDp * density).toInt()
        if (leftGutterText.layoutParams.width != gutterPx) {
            leftGutterText.layoutParams = leftGutterText.layoutParams.apply { width = gutterPx }
        }

        currentItem = item
        itemView.setBackgroundColor(colors.foldedBannerBackground)
        leftGutterText.setBackgroundColor(colors.lineNumberBackground)
        leftGutterText.setTextColor(colors.lineNumberTextColor)
        leftGutterText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        leftGutterText.text = "↕️"
        gutterDivider.setBackgroundColor(colors.dividerColor)

        bannerText.setTextColor(colors.foldedBannerTextColor)
        bannerText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp - 1f)

        val rangeLeft = if (item.startLineLeft != null && item.endLineLeft != null) {
            "L${item.startLineLeft}~L${item.endLineLeft}"
        } else ""
        val rangeRight = if (item.startLineRight != null && item.endLineRight != null) {
            "R${item.startLineRight}~R${item.endLineRight}"
        } else ""

        bannerText.text = labels.foldedBannerFormatter(item.lineCount, rangeLeft, rangeRight)
    }

    companion object {
        fun create(context: Context, gutterWidthDp: Int, onToggleFold: (Long) -> Unit): FoldedHeaderViewHolder {
            val density = context.resources.displayMetrics.density
            val gutterPx = (gutterWidthDp * density).toInt()
            val dividerPx = (1 * density).toInt().coerceAtLeast(1)
            val padVerticalPx = (6 * density).toInt()
            val padHorizontalPx = (12 * density).toInt()

            val rootLayout = LinearLayout(context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
            }

            val leftGutterText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(gutterPx, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = Gravity.CENTER
                typeface = Typeface.MONOSPACE
                setSingleLine(true)
                setPadding(0, padVerticalPx, 0, padVerticalPx)
            }

            val gutterDivider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dividerPx, ViewGroup.LayoutParams.MATCH_PARENT)
            }

            val bannerText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setPadding(padHorizontalPx, padVerticalPx, padHorizontalPx, padVerticalPx)
                }
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
            }

            rootLayout.addView(leftGutterText)
            rootLayout.addView(gutterDivider)
            rootLayout.addView(bannerText)

            return FoldedHeaderViewHolder(rootLayout, leftGutterText, gutterDivider, bannerText, onToggleFold)
        }
    }
}

/**
 * 줄 바꿈(Line Wrap) 시 Android TextView가 들여쓰기 공백 뒤에서 줄을 바꿔 첫 행이 비어 보이는 현상을 방지하기 위해
 * 앞쪽의 연속된 공백 문자들을 Non-Breaking Space(\u00A0)로 치환합니다.
 */
internal fun formatWrappedText(text: CharSequence): CharSequence {
    var leadingSpaceCount = 0
    while (leadingSpaceCount < text.length && text[leadingSpaceCount] == ' ') {
        leadingSpaceCount++
    }
    if (leadingSpaceCount == 0) return text

    val nonBreakingLeading = "\u00A0".repeat(leadingSpaceCount)
    return when (text) {
        is Spannable -> {
            val ssb = SpannableStringBuilder(text)
            ssb.replace(0, leadingSpaceCount, nonBreakingLeading)
            ssb
        }

        is String -> {
            nonBreakingLeading + text.substring(leadingSpaceCount)
        }

        else -> {
            val ssb = SpannableStringBuilder(text)
            ssb.replace(0, leadingSpaceCount, nonBreakingLeading)
            ssb
        }
    }
}

