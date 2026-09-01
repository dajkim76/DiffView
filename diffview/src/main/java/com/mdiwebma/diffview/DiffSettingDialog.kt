package com.mdiwebma.diffview

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.mdiwebma.diffview.model.DiffGranularity
import com.mdiwebma.diffview.model.DiffLongTabAction
import com.mdiwebma.diffview.model.DiffMode
import com.mdiwebma.diffview.model.WhitespaceIgnoreMode

/**
 * DiffView의 모든 표시 및 동작 설정을 실시간으로 변경할 수 있는 모달 다이얼로그.
 */
object DiffSettingDialog {

    fun show(diffView: DiffView) {
        val context = diffView.context
        val labels = diffView.getSettingLabels()
        val density = context.resources.displayMetrics.density

        val padHorizontalPx = (18 * density).toInt()
        val padVerticalPx = (12 * density).toInt()
        val pad8Px = (8 * density).toInt()
        val pad4Px = (4 * density).toInt()

        val scrollView = ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isFillViewport = true
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padHorizontalPx, padVerticalPx, padHorizontalPx, padVerticalPx)
        }
        scrollView.addView(container)

        val isAppDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

        fun createSectionTitle(title: String): TextView {
            return TextView(context).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(if (isAppDark) Color.parseColor("#E0E0E0") else Color.parseColor("#444444"))
                paint.isFakeBoldText = true
                setPadding(0, pad8Px, 0, pad4Px)
            }
        }

        fun createButtonRow(): LinearLayout {
            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = pad4Px
                }
            }
        }

        fun styleOptionButton(btn: Button, isSelected: Boolean) {
            val bg = GradientDrawable().apply {
                cornerRadius = 6 * density
                if (isSelected) {
                    setColor(if (isAppDark) Color.parseColor("#3574F0") else Color.parseColor("#1976D2"))
                } else {
                    setColor(if (isAppDark) Color.parseColor("#2D3033") else Color.parseColor("#EAEAEA"))
                }
            }
            btn.background = bg
            btn.setTextColor(if (isSelected) Color.WHITE else if (isAppDark) Color.parseColor("#CCCCCC") else Color.parseColor("#333333"))
        }

        fun createOptionButton(text: String, isSelected: Boolean, onClick: () -> Unit): Button {
            return Button(context).apply {
                this.text = text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                isAllCaps = false
                gravity = Gravity.CENTER
                minHeight = (36 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = (4 * density).toInt()
                }
                setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
                styleOptionButton(this, isSelected)
                setOnClickListener { onClick() }
            }
        }

        var refreshUI: (() -> Unit)? = null

        fun buildContent() {
            container.removeAllViews()

            // 1. Diff Mode
            container.addView(createSectionTitle(labels.diffModeTitle))
            val modeRow = createButtonRow()
            val isSideBySide = diffView.getDiffMode() == DiffMode.SIDE_BY_SIDE
            modeRow.addView(createOptionButton(labels.modeSideBySide, isSideBySide) {
                diffView.setDiffMode(DiffMode.SIDE_BY_SIDE)
                refreshUI?.invoke()
            })
            modeRow.addView(createOptionButton(labels.modeUnified, !isSideBySide) {
                diffView.setDiffMode(DiffMode.UNIFIED)
                refreshUI?.invoke()
            })
            container.addView(modeRow)

            // 2. Theme (Light / Dark)
            container.addView(createSectionTitle(labels.themeTitle))
            val themeRow = createButtonRow()
            val isDark = diffView.isDark()
            themeRow.addView(createOptionButton(labels.themeLight, !isDark) {
                diffView.setDiffColors(DiffColors.Light, isDark = false)
                refreshUI?.invoke()
            })
            themeRow.addView(createOptionButton(labels.themeDark, isDark) {
                diffView.setDiffColors(DiffColors.Dark, isDark = true)
                refreshUI?.invoke()
            })
            container.addView(themeRow)

            // 3. Text Size
            container.addView(createSectionTitle(labels.textSizeTitle))
            val sizeRow = createButtonRow()
            val currentSize = diffView.getTextSize()
            val btnMinus = createOptionButton("－ (A-)", false) {
                val newSize = (diffView.getTextSize() - 1f).coerceAtLeast(8f)
                diffView.setTextSize(newSize)
                refreshUI?.invoke()
            }
            val tvSize = TextView(context).apply {
                text = "${"%.1f".format(currentSize)} sp"
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(if (isAppDark) Color.parseColor("#EEEEEE") else Color.parseColor("#222222"))
                paint.isFakeBoldText = true
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val btnPlus = createOptionButton("＋ (A+)", false) {
                val newSize = (diffView.getTextSize() + 1f).coerceAtMost(32f)
                diffView.setTextSize(newSize)
                refreshUI?.invoke()
            }
            sizeRow.addView(btnMinus)
            sizeRow.addView(tvSize)
            sizeRow.addView(btnPlus)
            container.addView(sizeRow)

            // 4. Code Folding
            container.addView(createSectionTitle(labels.foldingTitle))
            val foldRow = createButtonRow()
            val isFolding = diffView.isFoldingEnabled()
            foldRow.addView(createOptionButton(if (isFolding) labels.foldingOn else labels.foldingOff, isFolding) {
                diffView.setFoldingEnabled(!isFolding)
                refreshUI?.invoke()
            })
            foldRow.addView(createOptionButton(labels.expandAll, false) {
                diffView.expandAll()
            })
            foldRow.addView(createOptionButton(labels.collapseAll, false) {
                diffView.collapseAll()
            })
            container.addView(foldRow)

            // 5. Whitespace Ignore Mode
            container.addView(createSectionTitle(labels.whitespaceTitle))
            val wsRow1 = createButtonRow()
            val wsMode = diffView.getWhitespaceIgnoreMode()
            wsRow1.addView(createOptionButton(labels.whitespaceNone, wsMode == WhitespaceIgnoreMode.NONE) {
                diffView.setWhitespaceIgnoreMode(WhitespaceIgnoreMode.NONE)
                refreshUI?.invoke()
            })
            wsRow1.addView(createOptionButton(labels.whitespaceTrim, wsMode == WhitespaceIgnoreMode.TRIM_LEADING_TRAILING) {
                diffView.setWhitespaceIgnoreMode(WhitespaceIgnoreMode.TRIM_LEADING_TRAILING)
                refreshUI?.invoke()
            })
            container.addView(wsRow1)

            val wsRow2 = createButtonRow()
            wsRow2.addView(createOptionButton(labels.whitespaceCollapse, wsMode == WhitespaceIgnoreMode.COLLAPSE_WHITESPACE) {
                diffView.setWhitespaceIgnoreMode(WhitespaceIgnoreMode.COLLAPSE_WHITESPACE)
                refreshUI?.invoke()
            })
            wsRow2.addView(createOptionButton(labels.whitespaceIgnoreAll, wsMode == WhitespaceIgnoreMode.IGNORE_ALL) {
                diffView.setWhitespaceIgnoreMode(WhitespaceIgnoreMode.IGNORE_ALL)
                refreshUI?.invoke()
            })
            container.addView(wsRow2)

            // 6. Diff Granularity (Word / Char)
            container.addView(createSectionTitle(labels.granularityTitle))
            val granRow = createButtonRow()
            val gran = diffView.getDiffGranularity()
            granRow.addView(createOptionButton(labels.granularityWord, gran == DiffGranularity.WORD) {
                diffView.setDiffGranularity(DiffGranularity.WORD)
                refreshUI?.invoke()
            })
            granRow.addView(createOptionButton(labels.granularityChar, gran == DiffGranularity.CHARACTER) {
                diffView.setDiffGranularity(DiffGranularity.CHARACTER)
                refreshUI?.invoke()
            })
            container.addView(granRow)

            // 7. Line Wrap & Symbols
            container.addView(createSectionTitle("${labels.lineWrapTitle} / ${labels.symbolsTitle}"))
            val wrapSymbolRow = createButtonRow()
            val isWrap = diffView.isLineWrap()
            wrapSymbolRow.addView(createOptionButton(if (isWrap) labels.lineWrapOn else labels.lineWrapOff, isWrap) {
                diffView.setLineWrap(!isWrap)
                refreshUI?.invoke()
            })
            val isSymbols = diffView.isShowDiffSymbols()
            wrapSymbolRow.addView(createOptionButton(if (isSymbols) labels.symbolsOn else labels.symbolsOff, isSymbols) {
                diffView.setShowDiffSymbols(!isSymbols)
                refreshUI?.invoke()
            })
            container.addView(wrapSymbolRow)

            // 8. Long Press Action (None / Selectable / Comment)
            container.addView(createSectionTitle(labels.longTabActionTitle))
            val longTabRow = createButtonRow()
            val longTab = diffView.getLongTabAction()
            longTabRow.addView(createOptionButton(labels.longTabNone, longTab == DiffLongTabAction.NONE) {
                diffView.setLongTabAction(DiffLongTabAction.NONE)
                refreshUI?.invoke()
            })
            longTabRow.addView(createOptionButton(labels.longTabTextSelectable, longTab == DiffLongTabAction.TEXT_SELECTABLE) {
                diffView.setLongTabAction(DiffLongTabAction.TEXT_SELECTABLE)
                refreshUI?.invoke()
            })
            longTabRow.addView(createOptionButton(labels.longTabComment, longTab == DiffLongTabAction.COMMENT) {
                diffView.setLongTabAction(DiffLongTabAction.COMMENT)
                refreshUI?.invoke()
            })
            container.addView(longTabRow)

            // 9. Syntax Highlighting
            container.addView(createSectionTitle(labels.syntaxTitle))
            val syntaxRow = createButtonRow()
            val currentHighlighter = diffView.getSyntaxHighlighter()
            val currentHighlighterName = when (currentHighlighter) {
                is KotlinSyntaxHighlighter -> labels.syntaxKotlin
                is JavaSyntaxHighlighter -> "Java"
                is JavaScriptSyntaxHighlighter -> "JavaScript / TypeScript"
                is PythonSyntaxHighlighter -> "Python"
                is CppSyntaxHighlighter -> "C / C++"
                is CSharpSyntaxHighlighter -> "C#"
                else -> labels.syntaxPlain
            }

            val syntaxLanguages = listOf(
                labels.syntaxPlain to { PlainTextSyntaxHighlighter },
                labels.syntaxKotlin to { KotlinSyntaxHighlighter() },
                "Java" to { JavaSyntaxHighlighter() },
                "JavaScript / TypeScript" to { JavaScriptSyntaxHighlighter() },
                "Python" to { PythonSyntaxHighlighter() },
                "C / C++" to { CppSyntaxHighlighter() },
                "C#" to { CSharpSyntaxHighlighter() }
            )

            syntaxRow.addView(createOptionButton("$currentHighlighterName  ▾", true) {
                val names = syntaxLanguages.map { it.first }.toTypedArray()
                val currentSelectedIndex = syntaxLanguages.indexOfFirst {
                    when (currentHighlighter) {
                        is KotlinSyntaxHighlighter -> it.first == labels.syntaxKotlin
                        is JavaSyntaxHighlighter -> it.first == "Java"
                        is JavaScriptSyntaxHighlighter -> it.first == "JavaScript / TypeScript"
                        is PythonSyntaxHighlighter -> it.first == "Python"
                        is CppSyntaxHighlighter -> it.first == "C / C++"
                        is CSharpSyntaxHighlighter -> it.first == "C#"
                        else -> it.first == labels.syntaxPlain
                    }
                }.coerceAtLeast(0)

                AlertDialog.Builder(context)
                    .setTitle(labels.syntaxTitle)
                    .setSingleChoiceItems(names, currentSelectedIndex) { dialog, which ->
                        val selectedCreator = syntaxLanguages[which].second
                        diffView.setSyntaxHighlighter(selectedCreator())
                        refreshUI?.invoke()
                        dialog.dismiss()
                    }
                    .setNegativeButton(labels.closeButton, null)
                    .show()
            })
            container.addView(syntaxRow)
        }

        refreshUI = {
            buildContent()
        }

        buildContent()

        AlertDialog.Builder(context)
            .setTitle(labels.dialogTitle)
            .setView(scrollView)
            .setPositiveButton(labels.closeButton, null)
            .show()
    }
}
