package com.mdiwebma.diffview

import android.content.Context

/**
 * DiffView UI에 표시되는 텍스트 및 포맷터 설정.
 */
data class DiffLabels(
    val originalHeader: String = "Original",
    val modifiedHeader: String = "Modified",
    val unifiedHeader: String = "Unified Changes (+ / -)",
    val oldGutterHeader: String = "Old",
    val newGutterHeader: String = "New",
    val foldedBannerFormatter: (lineCount: Int, rangeLeft: String, rangeRight: String) -> String = { count, left, right ->
        val rangeInfo = if (left.isNotEmpty() || right.isNotEmpty()) " ($left / $right - Click to expand)" else ""
        "⋯ $count unchanged lines$rangeInfo ⋯"
    }
) {
    companion object {
        val Default = DiffLabels()

        /**
         * Android Context의 strings.xml 리소스에서 [DiffLabels]를 로드합니다.
         */
        fun fromContext(context: Context): DiffLabels {
            return DiffLabels(
                originalHeader = context.getString(R.string.diffview_header_original),
                modifiedHeader = context.getString(R.string.diffview_header_modified),
                unifiedHeader = context.getString(R.string.diffview_header_unified),
                oldGutterHeader = context.getString(R.string.diffview_header_old_gutter),
                newGutterHeader = context.getString(R.string.diffview_header_new_gutter),
                foldedBannerFormatter = { count, left, right ->
                    context.getString(R.string.diffview_folded_banner, count, left, right)
                }
            )
        }
    }
}
