package com.mdiwebma.diffview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mdiwebma.diffview.comment.CodeComment
import com.mdiwebma.diffview.comment.CodeCommentHelper
import com.mdiwebma.diffview.comment.CodeCommentManager
import com.mdiwebma.diffview.comment.DiffCommentLabels
import com.mdiwebma.diffview.comment.LineKey
import com.mdiwebma.diffview.engine.DiffEngine
import com.mdiwebma.diffview.engine.GitPatchParser
import com.mdiwebma.diffview.engine.KotlinDiffEngine
import com.mdiwebma.diffview.engine.ParsedGitPatch
import com.mdiwebma.diffview.model.DiffDisplayItem
import com.mdiwebma.diffview.model.DiffGranularity
import com.mdiwebma.diffview.model.DiffLongTabAction
import com.mdiwebma.diffview.model.DiffMode
import com.mdiwebma.diffview.model.DiffResult
import com.mdiwebma.diffview.model.WhitespaceIgnoreMode
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

    val commentManager: CodeCommentManager = CodeCommentManager(context)
    private var currentCommitHash: String? = null
    private var currentFilePath: String? = null
    private var isCommentsEnabled: Boolean = true

    var onLineLongClickListener: ((LineKey, CodeComment?) -> Unit)? = null
    var onCommentClickListener: ((LineKey, CodeComment) -> Unit)? = null

    private var diffEngine: DiffEngine = KotlinDiffEngine()
    private val viewScope = CoroutineScope(Dispatchers.Main + Job())

    private var diffMode: DiffMode = DiffMode.SIDE_BY_SIDE
    private var configuredDiffColors: DiffColors = DiffColors.Auto
    private var diffColors: DiffColors = DiffColors.defaultFor(context)
    private var diffLabels: DiffLabels = DiffLabels.fromContext(context)
    private var commentLabels: DiffCommentLabels = DiffCommentLabels.fromContext(context)
    private var settingLabels: DiffSettingLabels = DiffSettingLabels.fromContext(context)
    private var whitespaceIgnoreMode: WhitespaceIgnoreMode = WhitespaceIgnoreMode.NONE
    private var diffGranularity: DiffGranularity = DiffGranularity.WORD
    private var isLineWrap: Boolean = false
    private var showDiffSymbols: Boolean = true
    private var longTabAction: DiffLongTabAction = DiffLongTabAction.NONE
    private var gutterWidthDp: Int = 48
    private var isFoldingEnabled: Boolean = true
    private var contextLines: Int = 3
    private var foldingThreshold: Int = 8
    private var preferencesName: String = DiffViewPreferences.DEFAULT_PREFS_NAME
    private var preferencesKeyPrefix: String = ""
    private var isAutoSavePreferences: Boolean = false
    private val expandedFoldMap = mutableMapOf<Long, FoldExpansionState>()

    private var rawOriginalText: String = ""
    private var rawModifiedText: String = ""
    private var textNormalizer: TextNormalizer? = null
    private var normalizerChangedListener: ((newNormalizer: TextNormalizer?) -> Unit)? = null
    private var currentOriginalText: String = ""
    private var currentModifiedText: String = ""
    private var currentDiffResult: DiffResult? = null
    private var diffJob: Job? = null

    // UI Elements
    private val headerLayout: LinearLayout
    private val btnSettings: TextView
    private val btnMore: TextView
    private val leftHeaderBox: LinearLayout
    private val leftSpacer: View
    private val leftHeaderTitle: TextView
    private val rightHeaderBox: LinearLayout
    private val rightSpacer: View
    private val rightHeaderTitle: TextView
    private val unifiedHeaderBox: LinearLayout
    private val oldGutterHeaderTitle: TextView
    private val newGutterHeaderTitle: TextView
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
        val gutterPx = (gutterWidthDp * density).toInt()
        val dividerPx = (1 * density).toInt().coerceAtLeast(1)
        val padHorizontalPx = (8 * density).toInt()
        val padVerticalPx = (4 * density).toInt()
        val settingsWidthPx = (26 * density).toInt()

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
        leftSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(gutterPx, 1)
        }
        leftHeaderTitle = TextView(context).apply {
            text = diffLabels.originalHeader
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(padHorizontalPx, 0, 0, 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
        }
        leftHeaderBox.addView(leftSpacer)
        leftHeaderBox.addView(leftHeaderTitle)

        centerHeaderDivider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dividerPx, (14 * density).toInt())
        }

        // --- Side-by-Side Right Header ---
        rightHeaderBox = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        rightSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(gutterPx, 1)
        }
        rightHeaderTitle = TextView(context).apply {
            text = diffLabels.modifiedHeader
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(padHorizontalPx, 0, 0, 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
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
        oldGutterHeaderTitle = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(gutterPx, LayoutParams.WRAP_CONTENT)
            text = diffLabels.oldGutterHeader
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }
        newGutterHeaderTitle = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(gutterPx, LayoutParams.WRAP_CONTENT)
            text = diffLabels.newGutterHeader
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }
        unifiedHeaderTitle = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            text = diffLabels.unifiedHeader
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding((20 * density).toInt(), 0, 0, 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
        }
        unifiedHeaderBox.addView(oldGutterHeaderTitle)
        unifiedHeaderBox.addView(newGutterHeaderTitle)
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

        // Settings Button (Top-right ⚙️)
        btnSettings = TextView(context).apply {
            text = "⚙️"
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(settingsWidthPx, settingsWidthPx).apply {
                marginStart = (2 * density).toInt()
            }
            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
            if (outValue.resourceId != 0) {
                setBackgroundResource(outValue.resourceId)
            }
            isClickable = true
            isFocusable = true
            contentDescription = "DiffView Settings"
            setOnClickListener {
                showSettingsDialog()
            }
        }

        // More Options Button (Top-right ⋮)
        btnMore = TextView(context).apply {
            text = "⋮"
            textSize = 15f
            setTextColor(diffColors.headerTextColor)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(settingsWidthPx, settingsWidthPx).apply {
                marginStart = (1 * density).toInt()
            }
            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
            if (outValue.resourceId != 0) {
                setBackgroundResource(outValue.resourceId)
            }
            isClickable = true
            isFocusable = true
            contentDescription = "More Options"
            setOnClickListener {
                showMoreMenu(it)
            }
        }

        headerLayout.addView(leftHeaderBox)
        headerLayout.addView(centerHeaderDivider)
        headerLayout.addView(rightHeaderBox)
        headerLayout.addView(unifiedHeaderBox)
        headerLayout.addView(statsLayout)
        headerLayout.addView(btnSettings)
        headerLayout.addView(btnMore)

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
            onExpandUp = { foldId ->
                val state = expandedFoldMap.getOrPut(foldId) { FoldExpansionState() }
                expandedFoldMap[foldId] = state.copy(expandTopLines = state.expandTopLines + 6)
                updateDisplayItems()
            },
            onExpandDown = { foldId ->
                val state = expandedFoldMap.getOrPut(foldId) { FoldExpansionState() }
                expandedFoldMap[foldId] = state.copy(expandBottomLines = state.expandBottomLines + 6)
                updateDisplayItems()
            },
            onExpandAll = { foldId ->
                expandedFoldMap[foldId] = FoldExpansionState(isFullyExpanded = true)
                updateDisplayItems()
            }
        ).apply {
            this.diffLabels = this@DiffView.diffLabels
            this.diffMode = this@DiffView.diffMode
            this.onLineLongClick = { lineKey, currentComment ->
                if (onLineLongClickListener != null) {
                    onLineLongClickListener?.invoke(lineKey, currentComment)
                } else if (isCommentsEnabled) {
                    val commit = checkNotNull(currentCommitHash) {
                        "CommentContext is not initialized. Please call setCommentContext(commitHash, filePath) before adding comments."
                    }
                    val path = checkNotNull(currentFilePath) {
                        "CommentContext is not initialized. Please call setCommentContext(commitHash, filePath) before adding comments."
                    }
                    CodeCommentHelper.handleLineLongClick(
                        context = context,
                        lineKey = lineKey,
                        currentComment = currentComment,
                        labels = commentLabels,
                        onSave = { text ->
                            saveComment(commit, path, lineKey, text)
                        },
                        onDelete = {
                            deleteComment(commit, path, lineKey)
                        }
                    )
                }
            }
            this.onCommentClick = { lineKey, currentComment ->
                if (onCommentClickListener != null) {
                    onCommentClickListener?.invoke(lineKey, currentComment)
                } else if (isCommentsEnabled) {
                    val commit = checkNotNull(currentCommitHash) {
                        "CommentContext is not initialized. Please call setCommentContext(commitHash, filePath) before editing comments."
                    }
                    val path = checkNotNull(currentFilePath) {
                        "CommentContext is not initialized. Please call setCommentContext(commitHash, filePath) before editing comments."
                    }
                    CodeCommentHelper.showEditCommentDialog(
                        context = context,
                        currentText = currentComment.text,
                        labels = commentLabels,
                        onSave = { text ->
                            saveComment(commit, path, lineKey, text)
                        },
                        onDelete = {
                            deleteComment(commit, path, lineKey)
                        }
                    )
                }
            }
        }
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
            adapter.diffMode = mode
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
     * [ParsedGitPatch] 객체를 전달받아 원본 및 수정본 소스코드를 설정하고 Diff를 계산합니다.
     */
    fun setContentGitPatch(patch: ParsedGitPatch, autoUpdateHeaderTitles: Boolean = true) {
        if (autoUpdateHeaderTitles) {
            val origName = patch.originalFileName
            val modName = patch.modifiedFileName
            if (origName != null && modName != null) {
                setHeaderTitles(origName, modName)
            } else if (origName != null) {
                setHeaderTitles(origName, origName)
            } else if (modName != null) {
                setHeaderTitles(modName, modName)
            }
        }
        setContent(patch.originalText, patch.modifiedText)
    }

    /**
     * 표준 Git Patch 문자열을 파싱하여 지정된 인덱스([fileIndex])의 원본 및 수정본 소스코드를 설정하고 Diff를 계산합니다.
     *
     * @param gitPatch 표준 Git Patch 문자열 (e.g. `diff --git ...`, `@@ ... @@`)
     * @param fileIndex 다중 파일 패치 중 표시할 파일의 인덱스 (기본값: 0)
     * @param autoUpdateHeaderTitles true이면 diff 내에 포함된 파일명을 헤더 타이틀로 자동 설정합니다 (기본값: true).
     */
    fun setContentGitPatch(
        gitPatch: String,
        fileIndex: Int = 0,
        autoUpdateHeaderTitles: Boolean = true
    ) {
        val patches = GitPatchParser.parse(gitPatch)
        val targetPatch = patches.getOrNull(fileIndex) ?: return
        setContentGitPatch(targetPatch, autoUpdateHeaderTitles)
    }

    fun setOnTextNormalizerChanged(normalizerChangedListener: (newNormalizer: TextNormalizer?) -> Unit) {
        this.normalizerChangedListener = normalizerChangedListener
    }

    fun setTextNormalizer(textNormalizer: TextNormalizer?) {
        if (this.textNormalizer === textNormalizer) {
            return
        }
        this.textNormalizer = textNormalizer
        val original = textNormalizer?.normalize(rawOriginalText) ?: rawOriginalText
        val modified = textNormalizer?.normalize(rawModifiedText) ?: rawModifiedText
        setInnerContent(original, modified)
        normalizerChangedListener?.invoke(textNormalizer)
    }

    fun getTextNormalizer(): TextNormalizer? {
        return textNormalizer
    }

    fun setContent(rawOriginal: String, rawModified: String) {
        this.rawOriginalText = rawOriginal
        this.rawModifiedText = rawModified
        val original = textNormalizer?.normalize(rawOriginalText) ?: rawOriginalText
        val modified = textNormalizer?.normalize(rawModifiedText) ?: rawModifiedText
        setInnerContent(original, modified)
    }

    /**
     * 원본과 수정본 소스코드를 설정하고 비동기로 Diff를 계산합니다.
     */
    private fun setInnerContent(original: String, modified: String) {
        currentOriginalText = original
        currentModifiedText = modified
        diffJob?.cancel()
        expandedFoldMap.clear()
        adapter.resetScrollGroups()
        estimateAndPreloadContentWidths()
        recyclerView.scrollToPosition(0)
        progressBar.visibility = VISIBLE

        diffJob = viewScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    diffEngine.calculateDiff(
                        oldText = original,
                        newText = modified,
                        enableInlineDiff = true,
                        whitespaceMode = whitespaceIgnoreMode,
                        granularity = diffGranularity
                    )
                }
                currentDiffResult = result
                updateStats(result)
                estimateAndPreloadContentWidths()
                reloadComments()
                updateDisplayItems()
            } finally {
                progressBar.visibility = GONE
            }
        }
    }

    /**
     * 공백 무시 비교 모드 설정 ([WhitespaceIgnoreMode]).
     */
    fun setWhitespaceIgnoreMode(mode: WhitespaceIgnoreMode) {
        if (this.whitespaceIgnoreMode != mode) {
            this.whitespaceIgnoreMode = mode
            if (currentOriginalText.isNotEmpty() || currentModifiedText.isNotEmpty()) {
                setInnerContent(currentOriginalText, currentModifiedText)
            }
        }
    }

    fun getWhitespaceIgnoreMode(): WhitespaceIgnoreMode = whitespaceIgnoreMode

    /**
     * 인라인 Diff 비교 단위 설정 ([DiffGranularity.WORD] vs [DiffGranularity.CHARACTER]).
     */
    fun setDiffGranularity(granularity: DiffGranularity) {
        if (this.diffGranularity != granularity) {
            this.diffGranularity = granularity
            if (currentOriginalText.isNotEmpty() || currentModifiedText.isNotEmpty()) {
                setInnerContent(currentOriginalText, currentModifiedText)
            }
        }
    }

    fun getDiffGranularity(): DiffGranularity = diffGranularity

    /**
     * 긴 라인에 대한 자동 줄 바꿈(Line Wrap) 활성화 여부 설정.
     * - true: 가로 스크롤 대신 뷰 너비에 맞춰 자동 줄 바꿈
     * - false: 단일 행 유지 및 가로 동기 스크롤 (기본값)
     */
    fun setLineWrap(enabled: Boolean) {
        if (this.isLineWrap != enabled) {
            this.isLineWrap = enabled
            adapter.isLineWrap = enabled
            if (!enabled) {
                estimateAndPreloadContentWidths()
            } else {
                adapter.resetScrollGroups()
            }
        }
    }

    fun isLineWrap(): Boolean = isLineWrap

    /**
     * Side-by-Side 모드에서 줄 번호 옆에 변경 기호(- / +) 표시 여부 설정.
     * - true: 원본 줄 번호 뒤에 '-', 수정본 줄 번호 뒤에 '+' 표시 (기본값)
     * - false: 줄 번호만 표시
     */
    fun setShowDiffSymbols(enabled: Boolean) {
        if (this.showDiffSymbols != enabled) {
            this.showDiffSymbols = enabled
            adapter.showDiffSymbols = enabled
        }
    }

    fun isShowDiffSymbols(): Boolean = showDiffSymbols

    /**
     * 줄 번호(Gutter) 영역의 너비 설정 (DP 단위).
     * 기본값: 48dp
     */
    fun setGutterWidthDp(widthDp: Int) {
        if (this.gutterWidthDp != widthDp) {
            this.gutterWidthDp = widthDp
            adapter.gutterWidthDp = widthDp
            updateGutterWidths()
        }
    }

    fun getGutterWidthDp(): Int = gutterWidthDp

    /**
     * 코드 라인을 롱탭(Long-press)했을 때의 동작 옵션 설정.
     * - [DiffLongTabAction.NONE]: 롱탭 동작 없음 (기본값)
     * - [DiffLongTabAction.TEXT_SELECTABLE]: 텍스트 드래그 및 복사 모드
     * - [DiffLongTabAction.COMMENT]: 코드 라인 코멘트 추가/수정/삭제 모드
     */
    fun setLongTabAction(action: DiffLongTabAction) {
        if (this.longTabAction != action) {
            this.longTabAction = action
            adapter.longTabAction = action
        }
    }

    fun getLongTabAction(): DiffLongTabAction = longTabAction

    /**
     * DiffView 설정의 SharedPreferences 저장소 환경(이름, 키 접두사, 자동 저장 여부)을 한 번에 구성합니다.
     * @param prefsName SharedPreferences 파일 이름 (기본값: "diffview_preferences")
     * @param keyPrefix SharedPreferences 키 접두사 (기본값: "")
     * @param autoSave 설정 다이얼로그에서 변경 시 자동 저장 여부 (기본값: false)
     */
    fun configurePreferences(
        prefsName: String = DiffViewPreferences.DEFAULT_PREFS_NAME,
        keyPrefix: String = "",
        autoSave: Boolean = false
    ) {
        this.preferencesName = prefsName
        this.preferencesKeyPrefix = keyPrefix
        this.isAutoSavePreferences = autoSave
    }

    fun setPreferencesName(name: String) {
        this.preferencesName = name
    }

    fun getPreferencesName(): String = preferencesName

    fun setPreferencesKeyPrefix(prefix: String) {
        this.preferencesKeyPrefix = prefix
    }

    fun getPreferencesKeyPrefix(): String = preferencesKeyPrefix

    /**
     * 설정 변경 시 SharedPreferences에 자동 영구 저장할지 여부를 설정합니다 (기본값: false).
     */
    fun setAutoSavePreferences(enabled: Boolean) {
        this.isAutoSavePreferences = enabled
    }

    fun isAutoSavePreferences(): Boolean = isAutoSavePreferences

    /**
     * 설정 다이얼로그(Settings Dialog)를 화면에 표시합니다.
     * @param autoSave 설정 변경 시 자동으로 SharedPreferences에 영구 저장할지 여부 (기본값: [isAutoSavePreferences])
     * @param prefsName SharedPreferences 파일 이름 (기본값: [getPreferencesName])
     * @param keyPrefix SharedPreferences 키 접두사 (기본값: [getPreferencesKeyPrefix])
     */
    fun showSettingsDialog(
        autoSave: Boolean = this.isAutoSavePreferences,
        prefsName: String = this.preferencesName,
        keyPrefix: String = this.preferencesKeyPrefix
    ) {
        DiffSettingDialog.show(this, autoSave = autoSave, prefsName = prefsName, keyPrefix = keyPrefix)
    }

    /**
     * 상단 헤더의 설정(⚙️) 및 더보기(⋮) 버튼 노출 여부를 설정합니다.
     */
    fun setSettingsButtonVisible(visible: Boolean) {
        btnSettings.isVisible = visible
    }

    fun isSettingsButtonVisible(): Boolean = btnSettings.isVisible

    /**
     * 상단 헤더의 더보기(⋮) 버튼 노출 여부를 설정합니다.
     */
    fun setMoreButtonVisible(visible: Boolean) {
        btnMore.isVisible = visible
    }

    fun isMoreButtonVisible(): Boolean = btnMore.isVisible

    /**
     * 더보기(More) 팝업 메뉴를 화면에 표시합니다.
     */
    fun showMoreMenu(anchor: View = btnMore) {
        val popup = PopupMenu(context, anchor)
        val isFolding = isFoldingEnabled()

        popup.menu.add(0, 10, 0, "↕️ ${settingLabels.expandAll}").apply {
            isEnabled = isFolding
        }
        popup.menu.add(0, 11, 1, "➖ ${settingLabels.collapseAll}").apply {
            isEnabled = isFolding
        }
        popup.menu.add(0, 12, 2, "🎨 ${settingLabels.syntaxTitle}")
        popup.menu.add(0, 14, 3, "📄 ${settingLabels.normalizerTitle}")
        popup.menu.add(0, 13, 4, "📋 ${settingLabels.menuCopyGitPatch}")
        popup.menu.add(0, 1, 5, settingLabels.menuSaveVisibleImage)
        popup.menu.add(0, 2, 6, settingLabels.menuSaveFullImage)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                10 -> {
                    expandAll()
                    Toast.makeText(context, settingLabels.expandAllSuccess, Toast.LENGTH_SHORT).show()
                    true
                }

                11 -> {
                    collapseAll()
                    Toast.makeText(context, settingLabels.collapseAllSuccess, Toast.LENGTH_SHORT).show()
                    true
                }

                12 -> {
                    showSyntaxSelectionDialog()
                    true
                }

                14 -> {
                    showTextNormalizerSelectionDialog()
                    true
                }

                13 -> {
                    copyGitPatch()
                    Toast.makeText(context, settingLabels.copyGitPatchSuccess, Toast.LENGTH_SHORT).show()
                    true
                }

                1 -> {
                    executeImageCapture(isFull = false)
                    true
                }

                2 -> {
                    executeImageCapture(isFull = true)
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    /**
     * 지원되는 문법 하이라이트 언어를 AlertDialog 싱글 초이스로 선택하는 다이얼로그를 표시합니다.
     */
    fun showSyntaxSelectionDialog() {
        val languages = listOf(
            settingLabels.syntaxPlain to PlainTextSyntaxHighlighter,
            "Kotlin" to KotlinSyntaxHighlighter(),
            "Java" to JavaSyntaxHighlighter(),
            "JavaScript / TypeScript" to JavaScriptSyntaxHighlighter(),
            "Python" to PythonSyntaxHighlighter(),
            "C / C++" to CppSyntaxHighlighter(),
            "C#" to CSharpSyntaxHighlighter()
        )
        val currentHighlighter = getSyntaxHighlighter()
        val currentIndex = languages.indexOfFirst { (_, highlighter) ->
            highlighter::class == currentHighlighter::class
        }.let { if (it == -1) 0 else it }

        val items = languages.map { it.first }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle(settingLabels.syntaxTitle)
            .setSingleChoiceItems(items, currentIndex) { dialog, which ->
                val (_, selectedHighlighter) = languages[which]
                setSyntaxHighlighter(selectedHighlighter)
                dialog.dismiss()
            }
            .setNegativeButton(settingLabels.closeButton, null)
            .show()
    }

    /**
     * 파일 포맷 정규화(TextNormalizer) 포맷터를 AlertDialog 싱글 초이스로 선택하는 다이얼로그를 표시합니다.
     */
    fun showTextNormalizerSelectionDialog() {
        val normalizers = TextNormalizer.normalizerList
        val currentNormalizer = getTextNormalizer()
        val currentIndex = if (currentNormalizer == null) {
            normalizers.indexOfFirst { it.key == "PLAIN" }.let { if (it == -1) 0 else it }
        } else {
            normalizers.indexOfFirst { it.key == currentNormalizer.key }.let { if (it == -1) 0 else it }
        }

        val items = normalizers.map { it.name }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle(settingLabels.normalizerTitle)
            .setSingleChoiceItems(items, currentIndex) { dialog, which ->
                val selectedNormalizer = normalizers[which]
                val newNormalizer = if (selectedNormalizer.key == PlainTextNormalize.key) null else selectedNormalizer
                setTextNormalizer(newNormalizer)
                dialog.dismiss()
            }
            .setNegativeButton(settingLabels.closeButton, null)
            .show()
    }

    /**
     * 이미지 캡처 및 갤러리 저장 실행 후 공유/보기 다이얼로그 표시.
     */
    fun executeImageCapture(isFull: Boolean, onSaved: ((Uri?) -> Unit)? = null) {
        val bitmap = if (isFull) {
            captureFullBitmap()
        } else {
            captureVisibleBitmap()
        }

        if (bitmap == null) {
            Toast.makeText(context, settingLabels.imageSaveFailed, Toast.LENGTH_SHORT).show()
            onSaved?.invoke(null)
            return
        }

        progressBar.visibility = VISIBLE
        val suffix = if (isFull) "full" else "visible"
        val filename = "DiffView_${suffix}_${System.currentTimeMillis()}"

        viewScope.launch {
            val uri = try {
                saveBitmapToGallery(context, bitmap, filename)
            } finally {
                progressBar.visibility = GONE
            }

            if (uri != null) {
                showImageSavedDialog(uri)
            } else {
                Toast.makeText(context, settingLabels.imageSaveFailed, Toast.LENGTH_SHORT).show()
            }
            onSaved?.invoke(uri)
        }
    }

    /**
     * 이미지 저장 완료 알림 및 보기/공유 액션 다이얼로그를 표시합니다.
     */
    private fun showImageSavedDialog(uri: Uri) {
        AlertDialog.Builder(context)
            .setTitle(settingLabels.imageSavedTitle)
            .setMessage(settingLabels.imageSavedMessage)
            .setPositiveButton(settingLabels.actionView) { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "image/png")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, settingLabels.actionView))
                } catch (e: Exception) {
                    Toast.makeText(context, e.message ?: "Failed to open image", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton(settingLabels.actionShare) { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, settingLabels.actionShare))
                } catch (e: Exception) {
                    Toast.makeText(context, e.message ?: "Failed to share image", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(settingLabels.closeButton, null)
            .show()
    }

    /**
     * 코멘트 다이얼로그 라벨 설정 ([DiffCommentLabels]).
     */
    fun setCommentLabels(labels: DiffCommentLabels) {
        this.commentLabels = labels
    }

    fun getCommentLabels(): DiffCommentLabels = commentLabels

    /**
     * 코드 코멘트 본문(Content) 텍스트 크기를 설정합니다 (기본값: 12sp).
     */
    fun setCommentTextSize(sizeSp: Float) {
        adapter.commentTextSizeSp = sizeSp
    }

    /**
     * 코드 코멘트 본문(Content) 텍스트 크기를 반환합니다 (단위: SP).
     */
    fun getCommentTextSize(): Float = adapter.commentTextSizeSp

    /**
     * 코드 코멘트 날짜/시간(Date) 텍스트 크기를 설정합니다 (기본값: 11sp).
     */
    fun setCommentDateTextSize(sizeSp: Float) {
        adapter.commentDateTextSizeSp = sizeSp
    }

    /**
     * 코드 코멘트 날짜/시간(Date) 텍스트 크기를 반환합니다 (단위: SP).
     */
    fun getCommentDateTextSize(): Float = adapter.commentDateTextSizeSp

    /**
     * 코드 코멘트 본문 및 날짜 텍스트 크기를 한 번에 설정합니다 (단위: SP).
     */
    fun setCommentTextSizes(contentSizeSp: Float, dateSizeSp: Float) {
        adapter.commentTextSizeSp = contentSizeSp
        adapter.commentDateTextSizeSp = dateSizeSp
    }

    /**
     * 코드 코멘트에 날짜/시간 표시 여부를 설정합니다 (기본값: true).
     */
    fun setShowCommentDate(show: Boolean) {
        adapter.showCommentDate = show
    }

    /**
     * 코드 코멘트에 날짜/시간 표시 여부를 반환합니다.
     */
    fun isShowCommentDate(): Boolean = adapter.showCommentDate

    /**
     * 현재 원본 및 수정본 텍스트를 기반으로 표준 Git Patch 문자열을 생성합니다.
     */
    fun generateGitPatch(
        originalFileName: String = currentFilePath ?: diffLabels.originalHeader,
        modifiedFileName: String = currentFilePath ?: diffLabels.modifiedHeader,
        contextLines: Int = this.contextLines
    ): String {
        return diffEngine.generateGitPatch(
            originalFileName = originalFileName,
            modifiedFileName = modifiedFileName,
            oldText = currentOriginalText,
            newText = currentModifiedText,
            contextSize = contextLines
        )
    }

    /**
     * 현재 비교 중인 내용의 Git Patch 텍스트를 생성하여 시스템 클립보드에 복사합니다.
     */
    fun copyGitPatch(
        originalFileName: String = currentFilePath ?: diffLabels.originalHeader,
        modifiedFileName: String = currentFilePath ?: diffLabels.modifiedHeader,
        contextLines: Int = this.contextLines
    ): Boolean {
        val diffText = generateGitPatch(originalFileName, modifiedFileName, contextLines)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("Git Patch", diffText)
        clipboard?.setPrimaryClip(clip)
        return true
    }

    /**
     * 설정 다이얼로그 라벨 설정 ([DiffSettingLabels]).
     */
    fun setSettingLabels(labels: DiffSettingLabels) {
        this.settingLabels = labels
    }

    fun getSettingLabels(): DiffSettingLabels = settingLabels

    /**
     * Diff 테마/색상 팔레트 설정 ([DiffColors.Auto], [DiffColors.Light], [DiffColors.Dark], 또는 커스텀 [DiffColors]).
     * [DiffColors.Auto] 설정 시 시스템 설정에 따라 라이트/다크 테마가 자동 적용됩니다.
     */
    fun setDiffColors(colors: DiffColors) {
        this.configuredDiffColors = colors
        this.diffColors = if (colors == DiffColors.Auto) DiffColors.defaultFor(context) else colors
        applyColors()
    }

    /**
     * 현재 설정된 Diff 색상 팔레트를 반환합니다.
     */
    fun getDiffColors(): DiffColors = configuredDiffColors

    /**
     * 현재 DiffView가 다크 모드 테마로 렌더링 중인지 여부를 반환합니다.
     */
    fun isDark(): Boolean = (diffColors == DiffColors.Dark)

    fun getTextSize(): Float = adapter.textSizeSp

    fun isFoldingEnabled(): Boolean = isFoldingEnabled

    fun getContextLines(): Int = contextLines

    fun getFoldingThreshold(): Int = foldingThreshold

    fun getSyntaxHighlighter(): SyntaxHighlighter = adapter.syntaxHighlighter

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        if (configuredDiffColors == DiffColors.Auto) {
            this.diffColors = DiffColors.defaultFor(context)
            applyColors()
        }
    }

    /**
     * 현재 DiffView의 모든 설정값을 [DiffViewPreferences] 객체로 내보냅니다.
     */
    fun exportPreferences(): DiffViewPreferences {
        return DiffViewPreferences(
            diffMode = diffMode,
            theme = DiffViewPreferences.themeFromDiffColors(configuredDiffColors),
            textSizeSp = adapter.textSizeSp,
            isFoldingEnabled = isFoldingEnabled,
            contextLines = contextLines,
            foldingThreshold = foldingThreshold,
            whitespaceIgnoreMode = whitespaceIgnoreMode,
            diffGranularity = diffGranularity,
            isLineWrap = isLineWrap,
            showDiffSymbols = showDiffSymbols,
            longTabAction = longTabAction
        )
    }

    /**
     * [DiffViewPreferences] 객체의 설정값을 DiffView에 일괄 적용합니다.
     */
    fun applyPreferences(preferences: DiffViewPreferences) {
        setDiffMode(preferences.diffMode)
        setDiffColors(preferences.getDiffColors())
        setTextSize(preferences.textSizeSp)
        setFoldingEnabled(preferences.isFoldingEnabled, preferences.contextLines, preferences.foldingThreshold)
        setWhitespaceIgnoreMode(preferences.whitespaceIgnoreMode)
        setDiffGranularity(preferences.diffGranularity)
        setLineWrap(preferences.isLineWrap)
        setShowDiffSymbols(preferences.showDiffSymbols)
        setLongTabAction(preferences.longTabAction)
    }

    /**
     * 현재 설정을 지정된 [SharedPreferences]에 저장합니다.
     */
    fun savePreferences(
        sharedPreferences: SharedPreferences,
        keyPrefix: String = this.preferencesKeyPrefix
    ) {
        exportPreferences().saveTo(sharedPreferences, keyPrefix)
    }

    /**
     * 지정된 [SharedPreferences]에서 설정을 불러와 DiffView에 적용합니다.
     */
    fun loadPreferences(
        sharedPreferences: SharedPreferences,
        keyPrefix: String = this.preferencesKeyPrefix
    ) {
        val prefs = DiffViewPreferences.loadFrom(sharedPreferences, keyPrefix, default = exportPreferences())
        applyPreferences(prefs)
    }

    /**
     * 기본 SharedPreferences(또는 지정된 이름의 SharedPreferences)에 현재 설정을 저장합니다.
     */
    fun savePreferences(
        prefsName: String = this.preferencesName,
        keyPrefix: String = this.preferencesKeyPrefix
    ) {
        val sp = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        savePreferences(sp, keyPrefix)
    }

    /**
     * 기본 SharedPreferences(또는 지정된 이름의 SharedPreferences)에서 설정을 로드하여 적용합니다.
     */
    fun loadPreferences(
        prefsName: String = this.preferencesName,
        keyPrefix: String = this.preferencesKeyPrefix
    ) {
        val sp = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        loadPreferences(sp, keyPrefix)
    }

    private fun updateGutterWidths() {
        val density = context.resources.displayMetrics.density
        val gutterPx = (gutterWidthDp * density).toInt()
        leftSpacer.layoutParams = leftSpacer.layoutParams.apply { width = gutterPx }
        rightSpacer.layoutParams = rightSpacer.layoutParams.apply { width = gutterPx }
        oldGutterHeaderTitle.layoutParams = oldGutterHeaderTitle.layoutParams.apply { width = gutterPx }
        newGutterHeaderTitle.layoutParams = newGutterHeaderTitle.layoutParams.apply { width = gutterPx }
    }

    /**
     * Monospace 폰트를 기준으로 각 사이드의 최대 라인 너비를 즉시 계산하여
     * 첫 번째 라인부터 완벽하게 드래그 가로 스크롤이 작동하도록 사전 설정합니다.
     */
    private fun estimateAndPreloadContentWidths() {
        if (isLineWrap) return
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
        if (adapter.syntaxHighlighter === highlighter) {
            return
        }
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
     * 전체 UI 텍스트 및 포맷터 설정 ([DiffLabels]).
     */
    fun setDiffLabels(labels: DiffLabels) {
        this.diffLabels = labels
        applyLabels()
    }

    fun getDiffLabels(): DiffLabels = diffLabels

    /**
     * Side-by-Side 모드 헤더 타이틀 설정.
     */
    fun setHeaderTitles(original: String, modified: String) {
        this.diffLabels = diffLabels.copy(originalHeader = original, modifiedHeader = modified)
        leftHeaderTitle.text = original
        rightHeaderTitle.text = modified
    }

    /**
     * Unified 모드 헤더 타이틀 설정.
     */
    fun setUnifiedHeaderTitle(title: String) {
        this.diffLabels = diffLabels.copy(unifiedHeader = title)
        unifiedHeaderTitle.text = title
    }

    private fun applyLabels() {
        leftHeaderTitle.text = diffLabels.originalHeader
        rightHeaderTitle.text = diffLabels.modifiedHeader
        unifiedHeaderTitle.text = diffLabels.unifiedHeader
        oldGutterHeaderTitle.text = diffLabels.oldGutterHeader
        newGutterHeaderTitle.text = diffLabels.newGutterHeader
        adapter.diffLabels = diffLabels
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
            expandedFoldMap = emptyMap()
        ).filterIsInstance<DiffDisplayItem.FoldedHeader>()

        for (item in items) {
            expandedFoldMap[item.id] = FoldExpansionState(isFullyExpanded = true)
        }
        updateDisplayItems()
    }

    /**
     * 모든 접힌 블록 다시 접기.
     */
    fun collapseAll() {
        expandedFoldMap.clear()
        updateDisplayItems()
    }

    private fun updateDisplayItems() {
        val items = FoldingManager.createDisplayItems(
            diffResult = currentDiffResult,
            mode = diffMode,
            isFoldingEnabled = isFoldingEnabled,
            contextLines = contextLines,
            foldingThreshold = foldingThreshold,
            expandedFoldMap = expandedFoldMap
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
        oldGutterHeaderTitle.setTextColor(diffColors.headerTextColor)
        newGutterHeaderTitle.setTextColor(diffColors.headerTextColor)
        btnMore.setTextColor(diffColors.headerTextColor)
        headerDivider.setBackgroundColor(diffColors.dividerColor)
        centerHeaderDivider.setBackgroundColor(diffColors.dividerColor)

        adapter.diffColors = diffColors
    }

    /**
     * 커밋 해시와 파일 경로를 설정하여 해당 파일에 저장된 코멘트를 비동기로 로드하고 표시합니다.
     */
    fun setCommentContext(commitHash: String, filePath: String) {
        this.currentCommitHash = commitHash
        this.currentFilePath = filePath
        reloadComments()
    }

    /**
     * 현재 설정된 커밋과 파일의 코멘트를 디스크/메모리에서 다시 로드합니다.
     */
    fun reloadComments() {
        val commit = currentCommitHash ?: return
        val path = currentFilePath ?: return
        viewScope.launch {
            val loaded = commentManager.loadComments(commit, path)
            adapter.comments = loaded
        }
    }

    /**
     * 특정 라인에 코멘트를 저장하고 뷰를 갱신합니다.
     */
    fun saveComment(commitHash: String, filePath: String, key: LineKey, text: String) {
        viewScope.launch {
            commentManager.saveComment(commitHash, filePath, key, text)
            val updated = commentManager.loadComments(commitHash, filePath)
            adapter.comments = updated
        }
    }

    /**
     * 현재 설정된 커밋과 파일([setCommentContext])에 특정 라인 코멘트를 저장하고 뷰를 갱신합니다.
     */
    fun saveComment(key: LineKey, text: String) {
        val commit = checkNotNull(currentCommitHash) {
            "CommentContext is not initialized. Please call setCommentContext(commitHash, filePath) before saving comments."
        }
        val path = checkNotNull(currentFilePath) {
            "CommentContext is not initialized. Please call setCommentContext(commitHash, filePath) before saving comments."
        }
        saveComment(commit, path, key, text)
    }

    /**
     * 특정 라인의 코멘트를 삭제하고 뷰를 갱신합니다.
     */
    fun deleteComment(commitHash: String, filePath: String, key: LineKey) {
        viewScope.launch {
            commentManager.deleteComment(commitHash, filePath, key)
            val updated = commentManager.loadComments(commitHash, filePath)
            adapter.comments = updated
        }
    }

    /**
     * 현재 설정된 커밋과 파일([setCommentContext])에서 특정 라인의 코멘트를 삭제하고 뷰를 갱신합니다.
     */
    fun deleteComment(key: LineKey) {
        val commit = checkNotNull(currentCommitHash) {
            "CommentContext is not initialized. Please call setCommentContext(commitHash, filePath) before deleting comments."
        }
        val path = checkNotNull(currentFilePath) {
            "CommentContext is not initialized. Please call setCommentContext(commitHash, filePath) before deleting comments."
        }
        deleteComment(commit, path, key)
    }

    /**
     * 라인 롱클릭을 통한 코멘트 기능 활성화/비활성화 여부 설정.
     */
    fun setCommentsEnabled(enabled: Boolean) {
        this.isCommentsEnabled = enabled
    }

    fun isCommentsEnabled(): Boolean = isCommentsEnabled

    /**
     * 현재 화면에 표시되고 있는 영역(Viewport)을 [Bitmap]으로 캡처합니다.
     * View의 기본 [draw] 메서드를 사용하여 화면에 렌더링된 상태 그대로 캡처합니다.
     */
    fun captureVisibleBitmap(): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            return null
        }
        val canvas = Canvas(bitmap)
        draw(canvas)
        return bitmap
    }

    /**
     * DiffView의 전체 내용(헤더 및 스크롤 끝까지의 모든 행)을 [Bitmap]으로 캡처합니다.
     * UI 스레드에서 호출되어야 하며, 전체 내용이 렌더링된 비트맵을 반환합니다.
     */
    fun captureFullBitmap(): Bitmap? {
        val w = if (width > 0) width else resources.displayMetrics.widthPixels
        if (w <= 0) return null

        val widthSpec = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
        val unspecifiedSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)

        // 1. Measure Header
        headerLayout.measure(widthSpec, unspecifiedSpec)
        val headerHeight = headerLayout.measuredHeight
        headerLayout.measure(widthSpec, MeasureSpec.makeMeasureSpec(headerHeight, MeasureSpec.EXACTLY))
        headerLayout.layout(0, 0, w, headerHeight)

        headerDivider.measure(widthSpec, unspecifiedSpec)
        val dividerHeight = headerDivider.measuredHeight
        headerDivider.measure(widthSpec, MeasureSpec.makeMeasureSpec(dividerHeight, MeasureSpec.EXACTLY))
        headerDivider.layout(0, 0, w, dividerHeight)

        // 2. Measure all RecyclerView items
        val itemCount = adapter.itemCount
        val viewHolders = ArrayList<RecyclerView.ViewHolder>(itemCount)
        val itemHeights = IntArray(itemCount)
        var itemsTotalHeight = 0

        for (i in 0 until itemCount) {
            val viewType = adapter.getItemViewType(i)
            val holder = adapter.createViewHolder(recyclerView, viewType)
            adapter.onBindViewHolder(holder, i)

            // Pass 1: compute wrap_content height
            holder.itemView.measure(widthSpec, unspecifiedSpec)
            val h = holder.itemView.measuredHeight.coerceAtLeast(1)
            itemHeights[i] = h
            itemsTotalHeight += h

            // Pass 2: layout with exact height so match_parent children have valid bounds
            holder.itemView.measure(widthSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
            holder.itemView.layout(0, 0, w, h)

            viewHolders.add(holder)
        }

        val totalHeight = (headerHeight + dividerHeight + itemsTotalHeight).coerceAtLeast(1)

        val bitmap = try {
            Bitmap.createBitmap(w, totalHeight, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            return null
        }

        val canvas = Canvas(bitmap)
        canvas.drawColor(diffColors.unchangedBackground)

        // 3. Draw Header
        headerLayout.draw(canvas)
        canvas.translate(0f, headerHeight.toFloat())

        headerDivider.draw(canvas)
        canvas.translate(0f, dividerHeight.toFloat())

        // 4. Draw all items
        for (i in 0 until itemCount) {
            val holder = viewHolders[i]
            val h = itemHeights[i]
            holder.itemView.draw(canvas)
            canvas.translate(0f, h.toFloat())
        }

        return bitmap
    }

    /**
     * 캡처한 비트맵을 기기의 사진 갤러리(Pictures/DiffView)에 PNG 파일로 비동기 저장합니다.
     */
    suspend fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        filename: String = "Diff_${System.currentTimeMillis()}"
    ): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$filename.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DiffView")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val itemUri = resolver.insert(collection, contentValues) ?: return@withContext null

        try {
            resolver.openOutputStream(itemUri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
            }
            itemUri
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                resolver.delete(itemUri, null, null)
            } catch (_: Exception) {
            }
            null
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        diffJob?.cancel()
        viewScope.cancel()
    }
}
