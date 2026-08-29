package com.mdiwebma.diffview

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.annotation.ColorInt

/**
 * Android Studio 스타일의 Diff 색상 팔레트 (Android View용 @ColorInt).
 */
data class DiffColors(
    @get:ColorInt val addedBackground: Int,
    @get:ColorInt val addedHighlight: Int,
    @get:ColorInt val deletedBackground: Int,
    @get:ColorInt val deletedHighlight: Int,
    @get:ColorInt val modifiedBackground: Int,
    @get:ColorInt val modifiedHighlight: Int,
    @get:ColorInt val unchangedBackground: Int,
    @get:ColorInt val lineNumberBackground: Int,
    @get:ColorInt val lineNumberTextColor: Int,
    @get:ColorInt val dividerColor: Int,
    @get:ColorInt val headerBackground: Int,
    @get:ColorInt val headerTextColor: Int,
    @get:ColorInt val foldedBannerBackground: Int,
    @get:ColorInt val foldedBannerTextColor: Int,
    @get:ColorInt val codeTextColor: Int
) {
    companion object {
        val Light = DiffColors(
            addedBackground = Color.parseColor("#E6F5E6"),
            addedHighlight = Color.parseColor("#BCE6BC"),
            deletedBackground = Color.parseColor("#FFECEC"),
            deletedHighlight = Color.parseColor("#F7BFBE"),
            modifiedBackground = Color.parseColor("#E9F2FD"),
            modifiedHighlight = Color.parseColor("#C7DEF9"),
            unchangedBackground = Color.parseColor("#FFFFFF"),
            lineNumberBackground = Color.parseColor("#F5F5F7"),
            lineNumberTextColor = Color.parseColor("#9E9E9E"),
            dividerColor = Color.parseColor("#D4D4D8"),
            headerBackground = Color.parseColor("#F1F3F5"),
            headerTextColor = Color.parseColor("#333333"),
            foldedBannerBackground = Color.parseColor("#EEF2F6"),
            foldedBannerTextColor = Color.parseColor("#5C6B73"),
            codeTextColor = Color.parseColor("#1F2328")
        )

        val Dark = DiffColors(
            addedBackground = Color.parseColor("#233827"),
            addedHighlight = Color.parseColor("#2E5434"),
            deletedBackground = Color.parseColor("#3E2728"),
            deletedHighlight = Color.parseColor("#5E3334"),
            modifiedBackground = Color.parseColor("#253549"),
            modifiedHighlight = Color.parseColor("#324D6F"),
            unchangedBackground = Color.parseColor("#1E1F22"),
            lineNumberBackground = Color.parseColor("#25262A"),
            lineNumberTextColor = Color.parseColor("#6B6E77"),
            dividerColor = Color.parseColor("#393B40"),
            headerBackground = Color.parseColor("#2B2D30"),
            headerTextColor = Color.parseColor("#BCBEC4"),
            foldedBannerBackground = Color.parseColor("#2B2D30"),
            foldedBannerTextColor = Color.parseColor("#868A91"),
            codeTextColor = Color.parseColor("#BCBEC4")
        )

        fun defaultFor(context: Context): DiffColors {
            val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) Dark else Light
        }
    }
}
