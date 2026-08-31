package com.mdiwebma.diffview

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
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
import android.view.LayoutInflater
import com.mdiwebma.diffview.comment.CodeComment
import com.mdiwebma.diffview.comment.CodeCommentHelper
import com.mdiwebma.diffview.comment.LineKey
import com.mdiwebma.diffview.model.DiffDisplayItem
import com.mdiwebma.diffview.model.DiffLine
import com.mdiwebma.diffview.model.DiffMode
import com.mdiwebma.diffview.model.DiffRow
import com.mdiwebma.diffview.model.DiffRowType
import com.mdiwebma.diffview.model.FoldPosition

/**
 * Side-by-Side 및 Unified 모드를 모두 지원하며,
 * 뷰/컬럼 단위로 가로 스크롤이 동기화되는 Diff 어댑터.
 */
class DiffViewAdapter(
    val leftSyncGroup: HorizontalScrollSyncGroup = HorizontalScrollSyncGroup(),
    val rightSyncGroup: HorizontalScrollSyncGroup = HorizontalScrollSyncGroup(),
    val unifiedSyncGroup: HorizontalScrollSyncGroup = HorizontalScrollSyncGroup(),
    private val onExpandUp: (Long) -> Unit = {},
    private val onExpandDown: (Long) -> Unit = {},
    private val onExpandAll: (Long) -> Unit = {}
) : ListAdapter<DiffDisplayItem, RecyclerView.ViewHolder>(DiffItemCallback) {

    var comments: Map<LineKey, CodeComment> = emptyMap()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var onLineLongClick: ((LineKey, CodeComment?) -> Unit)? = null
    var onCommentClick: ((LineKey, CodeComment) -> Unit)? = null

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

    var gutterWidthDp: Int = 42
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    var diffMode: DiffMode = DiffMode.SIDE_BY_SIDE
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
            TYPE_FOLDED_HEADER -> FoldedHeaderViewHolder.create(parent.context, gutterWidthDp, onExpandUp, onExpandDown, onExpandAll)
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
                    isTextSelectable = isTextSelectable,
                    comments = comments,
                    onLineLongClick = onLineLongClick,
                    onCommentClick = onCommentClick
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
                    isTextSelectable = isTextSelectable,
                    comments = comments,
                    onLineLongClick = onLineLongClick,
                    onCommentClick = onCommentClick
                )
            }

            is DiffDisplayItem.FoldedHeader -> {
                (holder as FoldedHeaderViewHolder).bind(
                    item = item,
                    colors = diffColors,
                    labels = diffLabels,
                    textSizeSp = textSizeSp,
                    gutterWidthDp = gutterWidthDp,
                    diffMode = diffMode
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
    private val leftColumn: LinearLayout,
    private val leftContainer: LinearLayout,
    private val leftGutterText: TextView,
    private val leftGutterDivider: View,
    private val leftPrefixText: TextView,
    private val leftScrollView: SyncHorizontalScrollView,
    private val leftCodeText: TextView,
    private val leftCommentView: View,
    private val leftTvCommentTime: TextView,
    private val leftTvCommentContent: TextView,
    private val centerDivider: View,
    private val rightColumn: LinearLayout,
    private val rightContainer: LinearLayout,
    private val rightGutterText: TextView,
    private val rightGutterDivider: View,
    private val rightPrefixText: TextView,
    private val rightScrollView: SyncHorizontalScrollView,
    private val rightCodeText: TextView,
    private val rightCommentView: View,
    private val rightTvCommentTime: TextView,
    private val rightTvCommentContent: TextView,
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
        isTextSelectable: Boolean = false,
        comments: Map<LineKey, CodeComment> = emptyMap(),
        onLineLongClick: ((LineKey, CodeComment?) -> Unit)? = null,
        onCommentClick: ((LineKey, CodeComment) -> Unit)? = null
    ) {
        val density = leftGutterText.context.resources.displayMetrics.density
        val gutterPx = (gutterWidthDp * density).toInt()
        val dividerPx = (1 * density).toInt().coerceAtLeast(1)
        val prefixPx = (16 * density).toInt()
        val sideGutterTotalPx = gutterPx + dividerPx + (if (showDiffSymbols) prefixPx else 0)
        val commentMarginStart = sideGutterTotalPx + (4 * density).toInt()

        if (leftGutterText.layoutParams.width != gutterPx) {
            leftGutterText.layoutParams = leftGutterText.layoutParams.apply { width = gutterPx }
        }
        if (rightGutterText.layoutParams.width != gutterPx) {
            rightGutterText.layoutParams = rightGutterText.layoutParams.apply { width = gutterPx }
        }

        (leftCommentView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            if (lp.marginStart != commentMarginStart) {
                lp.marginStart = commentMarginStart
                leftCommentView.layoutParams = lp
            }
        }
        (rightCommentView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            if (lp.marginStart != commentMarginStart) {
                lp.marginStart = commentMarginStart
                rightCommentView.layoutParams = lp
            }
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
        val prefixGravity = if (isLineWrap) Gravity.CENTER_HORIZONTAL or Gravity.TOP else Gravity.CENTER
        leftGutterText.gravity = gutterGravity
        rightGutterText.gravity = gutterGravity
        leftPrefixText.gravity = prefixGravity
        rightPrefixText.gravity = prefixGravity

        leftGutterText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        rightGutterText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        leftPrefixText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        rightPrefixText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        leftCodeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        rightCodeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)

        leftGutterDivider.setBackgroundColor(colors.dividerColor)
        centerDivider.setBackgroundColor(colors.dividerColor)
        rightGutterDivider.setBackgroundColor(colors.dividerColor)

        val leftBg = when (row.type) {
            DiffRowType.DELETED -> colors.deletedBackground
            DiffRowType.MODIFIED -> colors.modifiedBackground
            else -> colors.unchangedBackground
        }
        val leftGutterBg = when (row.type) {
            DiffRowType.DELETED -> colors.deletedBackground
            DiffRowType.MODIFIED -> colors.modifiedBackground
            else -> colors.lineNumberBackground
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
        val rightGutterBg = when (row.type) {
            DiffRowType.INSERTED -> colors.addedBackground
            DiffRowType.MODIFIED -> colors.modifiedBackground
            else -> colors.lineNumberBackground
        }
        val rightHighlightBg = when (row.type) {
            DiffRowType.INSERTED -> colors.addedHighlight
            DiffRowType.MODIFIED -> colors.modifiedHighlight
            else -> Color.TRANSPARENT
        }

        val leftSymbol = if (row.left != null) {
            when (row.type) {
                DiffRowType.DELETED, DiffRowType.MODIFIED -> "-"
                else -> " "
            }
        } else ""

        val rightSymbol = if (row.right != null) {
            when (row.type) {
                DiffRowType.INSERTED, DiffRowType.MODIFIED -> "+"
                else -> " "
            }
        } else ""

        val leftKey = row.left?.lineNumber?.let { LineKey(leftLine = it, rightLine = null) }
        val leftComment = leftKey?.let { comments[it] }

        val rightKey = row.right?.lineNumber?.let { LineKey(leftLine = null, rightLine = it) }
        val rightComment = rightKey?.let { comments[it] }

        bindSide(
            line = row.left,
            lineKey = leftKey,
            comment = leftComment,
            container = leftContainer,
            gutterText = leftGutterText,
            prefixText = leftPrefixText,
            codeText = leftCodeText,
            commentView = leftCommentView,
            tvCommentTime = leftTvCommentTime,
            tvCommentContent = leftTvCommentContent,
            bgColor = leftBg,
            gutterBgColor = leftGutterBg,
            highlightColor = leftHighlightBg,
            colors = colors,
            highlighter = highlighter,
            isDark = isDark,
            isLineWrap = isLineWrap,
            showDiffSymbols = showDiffSymbols,
            sideLabel = "Original",
            symbol = leftSymbol,
            onLineLongClick = onLineLongClick,
            onCommentClick = onCommentClick
        )

        bindSide(
            line = row.right,
            lineKey = rightKey,
            comment = rightComment,
            container = rightContainer,
            gutterText = rightGutterText,
            prefixText = rightPrefixText,
            codeText = rightCodeText,
            commentView = rightCommentView,
            tvCommentTime = rightTvCommentTime,
            tvCommentContent = rightTvCommentContent,
            bgColor = rightBg,
            gutterBgColor = rightGutterBg,
            highlightColor = rightHighlightBg,
            colors = colors,
            highlighter = highlighter,
            isDark = isDark,
            isLineWrap = isLineWrap,
            showDiffSymbols = showDiffSymbols,
            sideLabel = "Modified",
            symbol = rightSymbol,
            onLineLongClick = onLineLongClick,
            onCommentClick = onCommentClick
        )
    }

    private fun bindSide(
        line: DiffLine?,
        lineKey: LineKey?,
        comment: CodeComment?,
        container: LinearLayout,
        gutterText: TextView,
        prefixText: TextView,
        codeText: TextView,
        commentView: View,
        tvCommentTime: TextView,
        tvCommentContent: TextView,
        bgColor: Int,
        gutterBgColor: Int,
        highlightColor: Int,
        colors: DiffColors,
        highlighter: SyntaxHighlighter,
        isDark: Boolean,
        isLineWrap: Boolean,
        showDiffSymbols: Boolean,
        sideLabel: String,
        symbol: String = "",
        onLineLongClick: ((LineKey, CodeComment?) -> Unit)?,
        onCommentClick: ((LineKey, CodeComment) -> Unit)?
    ) {
        val density = container.context.resources.displayMetrics.density
        val padHorizontalPx = (6 * density).toInt()
        val padVerticalPx = (3 * density).toInt()
        val codePadLeft = if (showDiffSymbols) (2 * density).toInt() else padHorizontalPx
        codeText.setPadding(codePadLeft, padVerticalPx, padHorizontalPx, padVerticalPx)

        if (showDiffSymbols) {
            prefixText.visibility = View.VISIBLE
            prefixText.text = symbol
            prefixText.setTextColor(
                when (symbol) {
                    "-" -> Color.parseColor("#E53935")
                    "+" -> Color.parseColor("#4CAF50")
                    else -> colors.lineNumberTextColor
                }
            )
        } else {
            prefixText.visibility = View.GONE
            prefixText.text = ""
        }

        if (line != null && lineKey != null) {
            container.setBackgroundColor(bgColor)
            gutterText.setBackgroundColor(gutterBgColor)
            prefixText.setBackgroundColor(bgColor)
            gutterText.setTextColor(colors.lineNumberTextColor)
            codeText.setTextColor(colors.codeTextColor)
            gutterText.text = line.lineNumber?.toString() ?: ""
            val highlighted = highlighter.highlight(
                spans = line.spans,
                defaultTextColor = colors.codeTextColor,
                highlightBgColor = highlightColor,
                isDark = isDark
            )
            codeText.text = if (isLineWrap) formatWrappedText(highlighted) else highlighted
            container.contentDescription = "$sideLabel line ${line.lineNumber}: ${line.content}"

            CodeCommentHelper.bindCommentView(
                commentView = commentView,
                tvContent = tvCommentContent,
                tvTime = tvCommentTime,
                comment = comment,
                colors = colors,
                onCommentClick = {
                    if (comment != null) {
                        onCommentClick?.invoke(lineKey, comment) ?: onLineLongClick?.invoke(lineKey, comment)
                    }
                },
                onCommentLongClick = {
                    onLineLongClick?.invoke(lineKey, comment)
                }
            )

            val longClickListener = View.OnLongClickListener {
                onLineLongClick?.invoke(lineKey, comment)
                true
            }
            container.setOnLongClickListener(longClickListener)
            codeText.setOnLongClickListener(longClickListener)
        } else {
            container.setBackgroundColor(colors.noneTextBackground)
            gutterText.setBackgroundColor(colors.noneTextBackground)
            prefixText.setBackgroundColor(colors.noneTextBackground)
            gutterText.setTextColor(colors.lineNumberTextColor)
            codeText.setTextColor(colors.codeTextColor)
            gutterText.text = ""
            codeText.text = ""
            container.contentDescription = "$sideLabel empty line"
            commentView.visibility = View.GONE
            container.setOnLongClickListener(null)
            codeText.setOnLongClickListener(null)
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

            // Left Column
            val leftColumn = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                orientation = LinearLayout.VERTICAL
            }
            val leftContainer = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }
            val leftGutterText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(gutterPx, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                typeface = Typeface.MONOSPACE
                maxLines = 1
                setPadding(0, padVerticalPx, padHorizontalPx, padVerticalPx)
            }
            val leftGutterDivider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dividerPx, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            val leftPrefixText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(prefixPx, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = Gravity.CENTER
                typeface = Typeface.MONOSPACE
                setPadding(0, padVerticalPx, 0, padVerticalPx)
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
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                isFillViewport = true
                scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                addView(leftCodeText)
            }
            leftContainer.addView(leftGutterText)
            leftContainer.addView(leftGutterDivider)
            leftContainer.addView(leftPrefixText)
            leftContainer.addView(leftScrollView)

            val leftCommentView = LayoutInflater.from(context).inflate(R.layout.view_code_comment, leftColumn, false)
            val leftTvCommentTime = leftCommentView.findViewById<TextView>(R.id.tvCommentTime)
            val leftTvCommentContent = leftCommentView.findViewById<TextView>(R.id.tvCommentContent)
            leftCommentView.visibility = View.GONE

            leftColumn.addView(leftContainer)
            leftColumn.addView(leftCommentView)

            val centerDivider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dividerPx, ViewGroup.LayoutParams.MATCH_PARENT)
            }

            // Right Column
            val rightColumn = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                orientation = LinearLayout.VERTICAL
            }
            val rightContainer = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }
            val rightGutterText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(gutterPx, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                typeface = Typeface.MONOSPACE
                maxLines = 1
                setPadding(0, padVerticalPx, padHorizontalPx, padVerticalPx)
            }
            val rightGutterDivider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dividerPx, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            val rightPrefixText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(prefixPx, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = Gravity.CENTER
                typeface = Typeface.MONOSPACE
                setPadding(0, padVerticalPx, 0, padVerticalPx)
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
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                isFillViewport = true
                scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                addView(rightCodeText)
            }
            rightContainer.addView(rightGutterText)
            rightContainer.addView(rightGutterDivider)
            rightContainer.addView(rightPrefixText)
            rightContainer.addView(rightScrollView)

            val rightCommentView = LayoutInflater.from(context).inflate(R.layout.view_code_comment, rightColumn, false)
            val rightTvCommentTime = rightCommentView.findViewById<TextView>(R.id.tvCommentTime)
            val rightTvCommentContent = rightCommentView.findViewById<TextView>(R.id.tvCommentContent)
            rightCommentView.visibility = View.GONE

            rightColumn.addView(rightContainer)
            rightColumn.addView(rightCommentView)

            rootLayout.addView(leftColumn)
            rootLayout.addView(centerDivider)
            rootLayout.addView(rightColumn)

            return DiffRowViewHolder(
                itemView = rootLayout,
                leftColumn = leftColumn,
                leftContainer = leftContainer,
                leftGutterText = leftGutterText,
                leftGutterDivider = leftGutterDivider,
                leftPrefixText = leftPrefixText,
                leftScrollView = leftScrollView,
                leftCodeText = leftCodeText,
                leftCommentView = leftCommentView,
                leftTvCommentTime = leftTvCommentTime,
                leftTvCommentContent = leftTvCommentContent,
                centerDivider = centerDivider,
                rightColumn = rightColumn,
                rightContainer = rightContainer,
                rightGutterText = rightGutterText,
                rightGutterDivider = rightGutterDivider,
                rightPrefixText = rightPrefixText,
                rightScrollView = rightScrollView,
                rightCodeText = rightCodeText,
                rightCommentView = rightCommentView,
                rightTvCommentTime = rightTvCommentTime,
                rightTvCommentContent = rightTvCommentContent,
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
    private val lineContainer: LinearLayout,
    private val oldGutterText: TextView,
    private val newGutterText: TextView,
    private val gutterDivider: View,
    private val prefixText: TextView,
    private val scrollView: SyncHorizontalScrollView,
    private val codeText: TextView,
    private val commentView: View,
    private val tvCommentTime: TextView,
    private val tvCommentContent: TextView,
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
        isTextSelectable: Boolean = false,
        comments: Map<LineKey, CodeComment> = emptyMap(),
        onLineLongClick: ((LineKey, CodeComment?) -> Unit)? = null,
        onCommentClick: ((LineKey, CodeComment) -> Unit)? = null
    ) {
        val density = oldGutterText.context.resources.displayMetrics.density
        val gutterPx = (gutterWidthDp * density).toInt()
        val dividerPx = (1 * density).toInt().coerceAtLeast(1)
        val prefixPx = (16 * density).toInt()
        val unifiedGutterTotalPx = (gutterPx * 2) + dividerPx + prefixPx
        val commentMarginStart = unifiedGutterTotalPx + (4 * density).toInt()

        if (oldGutterText.layoutParams.width != gutterPx) {
            oldGutterText.layoutParams = oldGutterText.layoutParams.apply { width = gutterPx }
        }
        if (newGutterText.layoutParams.width != gutterPx) {
            newGutterText.layoutParams = newGutterText.layoutParams.apply { width = gutterPx }
        }

        (commentView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            if (lp.marginStart != commentMarginStart) {
                lp.marginStart = commentMarginStart
                commentView.layoutParams = lp
            }
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

        val lineKey = LineKey(leftLine = item.oldLineNumber, rightLine = item.newLineNumber)
        val comment = comments[lineKey]

        CodeCommentHelper.bindCommentView(
            commentView = commentView,
            tvContent = tvCommentContent,
            tvTime = tvCommentTime,
            comment = comment,
            colors = colors,
            onCommentClick = {
                if (comment != null) {
                    onCommentClick?.invoke(lineKey, comment) ?: onLineLongClick?.invoke(lineKey, comment)
                }
            },
            onCommentLongClick = {
                onLineLongClick?.invoke(lineKey, comment)
            }
        )

        val longClickListener = View.OnLongClickListener {
            onLineLongClick?.invoke(lineKey, comment)
            true
        }
        lineContainer.setOnLongClickListener(longClickListener)
        codeText.setOnLongClickListener(longClickListener)
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
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.TOP
            }

            val lineContainer = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
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
                maxLines = 1
                setPadding(0, padVerticalPx, padHorizontalPx, padVerticalPx)
            }

            val newGutterText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(gutterPx, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                typeface = Typeface.MONOSPACE
                maxLines = 1
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
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                isFillViewport = true
                scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                addView(codeText)
            }

            lineContainer.addView(oldGutterText)
            lineContainer.addView(newGutterText)
            lineContainer.addView(gutterDivider)
            lineContainer.addView(prefixText)
            lineContainer.addView(scrollView)

            val commentView = LayoutInflater.from(context).inflate(R.layout.view_code_comment, rootLayout, false)
            val tvCommentTime = commentView.findViewById<TextView>(R.id.tvCommentTime)
            val tvCommentContent = commentView.findViewById<TextView>(R.id.tvCommentContent)
            commentView.visibility = View.GONE

            rootLayout.addView(lineContainer)
            rootLayout.addView(commentView)

            return UnifiedRowViewHolder(
                itemView = rootLayout,
                rootContainer = rootLayout,
                lineContainer = lineContainer,
                oldGutterText = oldGutterText,
                newGutterText = newGutterText,
                gutterDivider = gutterDivider,
                prefixText = prefixText,
                scrollView = scrollView,
                codeText = codeText,
                commentView = commentView,
                tvCommentTime = tvCommentTime,
                tvCommentContent = tvCommentContent,
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
    private val btnExpandUp: TextView,
    private val bannerText: TextView,
    private val btnExpandDown: TextView,
    private val btnExpandAll: TextView,
    private val onExpandUp: (Long) -> Unit,
    private val onExpandDown: (Long) -> Unit,
    private val onExpandAll: (Long) -> Unit
) : RecyclerView.ViewHolder(itemView) {

    private var currentItem: DiffDisplayItem.FoldedHeader? = null

    init {
        btnExpandUp.setOnClickListener {
            currentItem?.let { onExpandUp(it.id) }
        }
        btnExpandDown.setOnClickListener {
            currentItem?.let { onExpandDown(it.id) }
        }
        btnExpandAll.setOnClickListener {
            currentItem?.let { onExpandAll(it.id) }
        }
        bannerText.setOnClickListener {
            currentItem?.let { item ->
                when (item.position) {
                    FoldPosition.START_OF_FILE -> onExpandUp(item.id)
                    FoldPosition.END_OF_FILE -> onExpandDown(item.id)
                    FoldPosition.MIDDLE -> {
                        onExpandUp(item.id)
                        onExpandDown(item.id)
                    }
                }
            }
        }
        leftGutterText.setOnClickListener {
            currentItem?.let { onExpandAll(it.id) }
        }
    }

    fun bind(
        item: DiffDisplayItem.FoldedHeader,
        colors: DiffColors,
        labels: DiffLabels,
        textSizeSp: Float,
        gutterWidthDp: Int = 48,
        diffMode: DiffMode = DiffMode.SIDE_BY_SIDE
    ) {
        val density = leftGutterText.context.resources.displayMetrics.density
        val singleGutterPx = (gutterWidthDp * density).toInt()
        val gutterPx = if (diffMode == DiffMode.UNIFIED) singleGutterPx * 2 else singleGutterPx
        if (leftGutterText.layoutParams.width != gutterPx) {
            leftGutterText.layoutParams = leftGutterText.layoutParams.apply { width = gutterPx }
        }
        leftGutterText.gravity = Gravity.CENTER

        currentItem = item
        itemView.setBackgroundColor(colors.foldedBannerBackground)
        leftGutterText.setBackgroundColor(colors.lineNumberBackground)
        leftGutterText.setTextColor(colors.lineNumberTextColor)
        leftGutterText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        leftGutterText.text = "↕️"
        gutterDivider.setBackgroundColor(colors.dividerColor)

        val btnBg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 4 * density
            setColor(colors.lineNumberBackground)
            setStroke((1 * density).toInt().coerceAtLeast(1), colors.dividerColor)
        }
        val btnBg2 = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 4 * density
            setColor(colors.lineNumberBackground)
            setStroke((1 * density).toInt().coerceAtLeast(1), colors.dividerColor)
        }
        val btnBg3 = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 4 * density
            setColor(colors.lineNumberBackground)
            setStroke((1 * density).toInt().coerceAtLeast(1), colors.dividerColor)
        }

        val btnTextSize = (textSizeSp - 1.5f).coerceAtLeast(9f)

        btnExpandUp.background = btnBg
        btnExpandUp.setTextColor(colors.codeTextColor)
        btnExpandUp.setTextSize(TypedValue.COMPLEX_UNIT_SP, btnTextSize)
        btnExpandUp.text = labels.expandUpLabel

        btnExpandDown.background = btnBg2
        btnExpandDown.setTextColor(colors.codeTextColor)
        btnExpandDown.setTextSize(TypedValue.COMPLEX_UNIT_SP, btnTextSize)
        btnExpandDown.text = labels.expandDownLabel

        btnExpandAll.background = btnBg3
        btnExpandAll.setTextColor(colors.codeTextColor)
        btnExpandAll.setTextSize(TypedValue.COMPLEX_UNIT_SP, btnTextSize)
        btnExpandAll.text = labels.expandAllLabel

        when (item.position) {
            FoldPosition.START_OF_FILE -> {
                btnExpandUp.visibility = View.VISIBLE
                btnExpandDown.visibility = View.GONE
            }

            FoldPosition.END_OF_FILE -> {
                btnExpandUp.visibility = View.GONE
                btnExpandDown.visibility = View.VISIBLE
            }

            FoldPosition.MIDDLE -> {
                btnExpandUp.visibility = View.VISIBLE
                btnExpandDown.visibility = View.VISIBLE
            }
        }

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
        fun create(
            context: Context,
            gutterWidthDp: Int,
            onExpandUp: (Long) -> Unit,
            onExpandDown: (Long) -> Unit,
            onExpandAll: (Long) -> Unit
        ): FoldedHeaderViewHolder {
            val density = context.resources.displayMetrics.density
            val gutterPx = (gutterWidthDp * density).toInt()
            val dividerPx = (1 * density).toInt().coerceAtLeast(1)
            val padVerticalPx = (4 * density).toInt()
            val padHorizontalPx = (8 * density).toInt()
            val leftGutterHorizontalPx = (6 * density).toInt()
            val btnPadH = (8 * density).toInt()
            val btnPadV = (3 * density).toInt()
            val btnMarginH = (4 * density).toInt()

            val rootLayout = LinearLayout(context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setPadding(0, 0, (6 * density).toInt(), 0)
            }

            val leftGutterText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(gutterPx, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                typeface = Typeface.MONOSPACE
                maxLines = 1
                setPadding(0, padVerticalPx, leftGutterHorizontalPx, padVerticalPx)
            }

            val gutterDivider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dividerPx, ViewGroup.LayoutParams.MATCH_PARENT)
            }

            val btnExpandUp = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(btnMarginH, 0, btnMarginH, 0)
                }
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setPadding(btnPadH, btnPadV, btnPadH, btnPadV)
                isClickable = true
                isFocusable = true
            }

            val bannerText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setPadding(padHorizontalPx, padVerticalPx, padHorizontalPx, padVerticalPx)
                }
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MIDDLE
                isClickable = true
                isFocusable = true
            }

            val btnExpandDown = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(btnMarginH, 0, btnMarginH, 0)
                }
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setPadding(btnPadH, btnPadV, btnPadH, btnPadV)
                isClickable = true
                isFocusable = true
            }

            val btnExpandAll = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(btnMarginH, 0, (2 * density).toInt(), 0)
                }
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setPadding(btnPadH, btnPadV, btnPadH, btnPadV)
                isClickable = true
                isFocusable = true
            }

            rootLayout.addView(leftGutterText)
            rootLayout.addView(gutterDivider)
            rootLayout.addView(btnExpandUp)
            rootLayout.addView(bannerText)
            rootLayout.addView(btnExpandDown)
            rootLayout.addView(btnExpandAll)

            return FoldedHeaderViewHolder(
                itemView = rootLayout,
                leftGutterText = leftGutterText,
                gutterDivider = gutterDivider,
                btnExpandUp = btnExpandUp,
                bannerText = bannerText,
                btnExpandDown = btnExpandDown,
                btnExpandAll = btnExpandAll,
                onExpandUp = onExpandUp,
                onExpandDown = onExpandDown,
                onExpandAll = onExpandAll
            )
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

