package com.mdiwebma.diffview

import android.content.Context
import android.content.SharedPreferences
import com.mdiwebma.diffview.model.DiffGranularity
import com.mdiwebma.diffview.model.DiffLongTabAction
import com.mdiwebma.diffview.model.DiffMode
import com.mdiwebma.diffview.model.WhitespaceIgnoreMode

/**
 * DiffView의 전체 설정값을 담는 불변 데이터 모델 및 SharedPreferences 저장/로드 헬퍼.
 */
data class DiffViewPreferences(
    val diffMode: DiffMode = DiffMode.SIDE_BY_SIDE,
    val theme: String = THEME_AUTO,
    val textSizeSp: Float = 12f,
    val isFoldingEnabled: Boolean = true,
    val contextLines: Int = 3,
    val foldingThreshold: Int = 8,
    val whitespaceIgnoreMode: WhitespaceIgnoreMode = WhitespaceIgnoreMode.NONE,
    val diffGranularity: DiffGranularity = DiffGranularity.WORD,
    val isLineWrap: Boolean = false,
    val showDiffSymbols: Boolean = true,
    val longTabAction: DiffLongTabAction = DiffLongTabAction.COMMENT
) {
    /**
     * 테마 설정값에 대응하는 [DiffColors]를 반환합니다.
     */
    fun getDiffColors(): DiffColors = when (theme.uppercase()) {
        THEME_LIGHT -> DiffColors.Light
        THEME_DARK -> DiffColors.Dark
        else -> DiffColors.Auto
    }

    /**
     * [SharedPreferences]에 설정값들을 저장합니다.
     * @param sharedPreferences 저장 대상 SharedPreferences 인스턴스
     * @param keyPrefix SharedPreferences 키 접두사 (다중 DiffView 인스턴스 구분용)
     */
    fun saveTo(sharedPreferences: SharedPreferences, keyPrefix: String = "") {
        sharedPreferences.edit()
            .putString("${keyPrefix}diff_mode", diffMode.name)
            .putString("${keyPrefix}theme", theme)
            .putFloat("${keyPrefix}text_size_sp", textSizeSp)
            .putBoolean("${keyPrefix}is_folding_enabled", isFoldingEnabled)
            .putInt("${keyPrefix}context_lines", contextLines)
            .putInt("${keyPrefix}folding_threshold", foldingThreshold)
            .putString("${keyPrefix}whitespace_ignore_mode", whitespaceIgnoreMode.name)
            .putString("${keyPrefix}diff_granularity", diffGranularity.name)
            .putBoolean("${keyPrefix}is_line_wrap", isLineWrap)
            .putBoolean("${keyPrefix}show_diff_symbols", showDiffSymbols)
            .putString("${keyPrefix}long_tab_action", longTabAction.name)
            .apply()
    }

    companion object {
        const val DEFAULT_PREFS_NAME = "diffview_preferences"
        const val THEME_AUTO = "AUTO"
        const val THEME_LIGHT = "LIGHT"
        const val THEME_DARK = "DARK"

        fun themeFromDiffColors(colors: DiffColors): String = when (colors) {
            DiffColors.Light -> THEME_LIGHT
            DiffColors.Dark -> THEME_DARK
            else -> THEME_AUTO
        }

        /**
         * [SharedPreferences]에서 설정값들을 로드합니다.
         * @param sharedPreferences 로드 대상 SharedPreferences 인스턴스
         * @param keyPrefix SharedPreferences 키 접두사
         * @param default 기본값 (설정값이 없을 때 사용할 값)
         */
        fun loadFrom(
            sharedPreferences: SharedPreferences,
            keyPrefix: String = "",
            default: DiffViewPreferences = DiffViewPreferences()
        ): DiffViewPreferences {
            val modeStr = sharedPreferences.getString("${keyPrefix}diff_mode", default.diffMode.name)
            val mode = runCatching { DiffMode.valueOf(modeStr ?: "") }.getOrDefault(default.diffMode)

            val themeStr = sharedPreferences.getString("${keyPrefix}theme", default.theme)
                ?: sharedPreferences.getString("${keyPrefix}theme_mode", default.theme)
                ?: default.theme

            val textSize = sharedPreferences.getFloat("${keyPrefix}text_size_sp", default.textSizeSp)
            val isFolding = sharedPreferences.getBoolean("${keyPrefix}is_folding_enabled", default.isFoldingEnabled)
            val contextLines = sharedPreferences.getInt("${keyPrefix}context_lines", default.contextLines)
            val threshold = sharedPreferences.getInt("${keyPrefix}folding_threshold", default.foldingThreshold)

            val wsStr = sharedPreferences.getString("${keyPrefix}whitespace_ignore_mode", default.whitespaceIgnoreMode.name)
            val ws = runCatching { WhitespaceIgnoreMode.valueOf(wsStr ?: "") }.getOrDefault(default.whitespaceIgnoreMode)

            val granStr = sharedPreferences.getString("${keyPrefix}diff_granularity", default.diffGranularity.name)
            val gran = runCatching { DiffGranularity.valueOf(granStr ?: "") }.getOrDefault(default.diffGranularity)

            val isLineWrap = sharedPreferences.getBoolean("${keyPrefix}is_line_wrap", default.isLineWrap)
            val showDiffSymbols = sharedPreferences.getBoolean("${keyPrefix}show_diff_symbols", default.showDiffSymbols)

            val tabStr = sharedPreferences.getString("${keyPrefix}long_tab_action", default.longTabAction.name)
            val longTab = runCatching { DiffLongTabAction.valueOf(tabStr ?: "") }.getOrDefault(default.longTabAction)

            return DiffViewPreferences(
                diffMode = mode,
                theme = themeStr,
                textSizeSp = textSize,
                isFoldingEnabled = isFolding,
                contextLines = contextLines,
                foldingThreshold = threshold,
                whitespaceIgnoreMode = ws,
                diffGranularity = gran,
                isLineWrap = isLineWrap,
                showDiffSymbols = showDiffSymbols,
                longTabAction = longTab
            )
        }

        /**
         * [Context]의 기본 SharedPreferences에서 [DiffViewPreferences]를 로드합니다.
         */
        fun loadFromContext(
            context: Context,
            prefsName: String = DEFAULT_PREFS_NAME,
            keyPrefix: String = "",
            default: DiffViewPreferences = DiffViewPreferences()
        ): DiffViewPreferences {
            val sp = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            return loadFrom(sp, keyPrefix, default)
        }
    }
}
