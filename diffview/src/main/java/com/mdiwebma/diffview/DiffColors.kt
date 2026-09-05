package com.mdiwebma.diffview

import android.content.Context
import android.content.res.Configuration
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
    @get:ColorInt val noneTextBackground: Int,
    @get:ColorInt val lineNumberBackground: Int,
    @get:ColorInt val lineNumberTextColor: Int,
    @get:ColorInt val dividerColor: Int,
    @get:ColorInt val headerBackground: Int,
    @get:ColorInt val headerTextColor: Int,
    @get:ColorInt val foldedBannerBackground: Int,
    @get:ColorInt val foldedBannerTextColor: Int,
    @get:ColorInt val codeTextColor: Int,
    @get:ColorInt val commentBackground: Int = 0xFFF6F8FA.toInt(),
    @get:ColorInt val commentStroke: Int = 0xFFD0D7DE.toInt(),
    @get:ColorInt val commentTextColor: Int = 0xFF24292F.toInt(),
    @get:ColorInt val commentTimeColor: Int = 0xFF57606A.toInt()
) {
    companion object {
        /**
         * 기본 제공 Light 테마의 원본 불변 팔레트.
         */
        val DefaultLight = DiffColors(
            addedBackground = 0xFFE6F5E6.toInt(),
            addedHighlight = 0xFFBCE6BC.toInt(),
            deletedBackground = 0xFFFFECEC.toInt(),
            deletedHighlight = 0xFFF7BFBE.toInt(),
            modifiedBackground = 0xFFE9F2FD.toInt(),
            modifiedHighlight = 0xFFC7DEF9.toInt(),
            unchangedBackground = 0xFFFFFFFF.toInt(),
            noneTextBackground = 0xFFEAEAEA.toInt(),
            lineNumberBackground = 0xFFF5F5F7.toInt(),
            lineNumberTextColor = 0xFF6E7781.toInt(),
            dividerColor = 0xFFD4D4D8.toInt(),
            headerBackground = 0xFFF1F3F5.toInt(),
            headerTextColor = 0xFF333333.toInt(),
            foldedBannerBackground = 0xFFEEF2F6.toInt(),
            foldedBannerTextColor = 0xFF5C6B73.toInt(),
            codeTextColor = 0xFF1F2328.toInt()
        )

        /**
         * 기본 제공 Dark 테마의 원본 불변 팔레트.
         */
        val DefaultDark = DiffColors(
            addedBackground = 0xFF233827.toInt(),
            addedHighlight = 0xFF2E5434.toInt(),
            deletedBackground = 0xFF3E2728.toInt(),
            deletedHighlight = 0xFF5E3334.toInt(),
            modifiedBackground = 0xFF253549.toInt(),
            modifiedHighlight = 0xFF324D6F.toInt(),
            unchangedBackground = 0xFF1E1F22.toInt(),
            noneTextBackground = 0xFF26282E.toInt(),
            lineNumberBackground = 0xFF25262A.toInt(),
            lineNumberTextColor = 0xFF9DA0A8.toInt(),
            dividerColor = 0xFF393B40.toInt(),
            headerBackground = 0xFF2B2D30.toInt(),
            headerTextColor = 0xFFBCBEC4.toInt(),
            foldedBannerBackground = 0xFF2B2D30.toInt(),
            foldedBannerTextColor = 0xFF868A91.toInt(),
            codeTextColor = 0xFFBCBEC4.toInt(),
            commentBackground = 0xFF262C36.toInt(),
            commentStroke = 0xFF444C56.toInt(),
            commentTextColor = 0xFFE6EDF3.toInt(),
            commentTimeColor = 0xFF9DA0A8.toInt()
        )

        /**
         * 앱 전역에서 기본으로 사용되는 Light 테마 인스턴스 (인스턴스 교체 가능).
         */
        var Light: DiffColors = DefaultLight

        /**
         * 앱 전역에서 기본으로 사용되는 Dark 테마 인스턴스 (인스턴스 교체 가능).
         */
        var Dark: DiffColors = DefaultDark

        /**
         * 시스템 다크 모드 설정에 따라 라이트/다크 테마를 자동 적용하는 센티널 테마 객체.
         */
        val Auto = DiffColors(
            addedBackground = 0,
            addedHighlight = 0,
            deletedBackground = 0,
            deletedHighlight = 0,
            modifiedBackground = 0,
            modifiedHighlight = 0,
            unchangedBackground = 0,
            noneTextBackground = 0,
            lineNumberBackground = 0,
            lineNumberTextColor = 0,
            dividerColor = 0,
            headerBackground = 0,
            headerTextColor = 0,
            foldedBannerBackground = 0,
            foldedBannerTextColor = 0,
            codeTextColor = 0,
            commentBackground = 0,
            commentStroke = 0,
            commentTextColor = 0,
            commentTimeColor = 0
        )

        fun defaultFor(context: Context): DiffColors {
            val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) Dark else Light
        }
    }
}
