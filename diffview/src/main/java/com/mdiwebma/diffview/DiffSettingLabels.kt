package com.mdiwebma.diffview

import android.content.Context

/**
 * DiffView 설정(Setting) 다이얼로그의 텍스트 라벨 설정.
 */
data class DiffSettingLabels(
    val dialogTitle: String = "DiffView Settings",
    val diffModeTitle: String = "Diff Mode",
    val modeSideBySide: String = "Side-by-Side",
    val modeUnified: String = "Unified",
    val themeTitle: String = "Theme",
    val themeAuto: String = "Auto",
    val themeLight: String = "Light",
    val themeDark: String = "Dark",
    val textSizeTitle: String = "Text Size",
    val foldingTitle: String = "Code Folding",
    val foldingOn: String = "ON",
    val foldingOff: String = "OFF",
    val collapseAll: String = "Collapse All",
    val expandAll: String = "Expand All",
    val whitespaceTitle: String = "Ignore Whitespace",
    val whitespaceNone: String = "None (Strict)",
    val whitespaceTrim: String = "Trim Leading/Trailing",
    val whitespaceCollapse: String = "Collapse Multiple",
    val whitespaceIgnoreAll: String = "Ignore All",
    val granularityTitle: String = "Diff Granularity",
    val granularityWord: String = "Word",
    val granularityChar: String = "Character",
    val lineWrapTitle: String = "Line Wrap",
    val lineWrapOn: String = "Wrap ON",
    val lineWrapOff: String = "Wrap OFF",
    val symbolsTitle: String = "Diff Symbols (+/-)",
    val symbolsOn: String = "Symbols ON",
    val symbolsOff: String = "Symbols OFF",
    val longTabActionTitle: String = "Line Long-Press Action",
    val longTabNone: String = "None",
    val longTabTextSelectable: String = "Text Selectable",
    val longTabComment: String = "Comment",
    val syntaxTitle: String = "Syntax Highlighting",
    val syntaxKotlin: String = "Kotlin",
    val syntaxPlain: String = "Plain Text",
    val closeButton: String = "Close",
    val menuSaveVisibleImage: String = "Save Visible Viewport Image",
    val menuSaveFullImage: String = "Save Full Diff Image",
    val imageSavedTitle: String = "Image Saved",
    val imageSavedMessage: String = "Diff image has been saved to Pictures/DiffView.",
    val imageSaveFailed: String = "Failed to save diff image.",
    val actionView: String = "View",
    val actionShare: String = "Share",
    val expandAllSuccess: String = "All code blocks expanded.",
    val collapseAllSuccess: String = "All code blocks collapsed.",
    val menuCopyGitPatch: String = "Copy Git Patch",
    val copyGitPatchSuccess: String = "Git Patch copied to clipboard."
) {
    companion object {
        val Default = DiffSettingLabels()

        /**
         * Android Context의 strings.xml 리소스에서 [DiffSettingLabels]를 로드합니다.
         */
        fun fromContext(context: Context): DiffSettingLabels {
            return DiffSettingLabels(
                dialogTitle = context.getString(R.string.diffview_opt_dialog_title),
                diffModeTitle = context.getString(R.string.diffview_opt_diff_mode),
                modeSideBySide = context.getString(R.string.diffview_opt_mode_side_by_side),
                modeUnified = context.getString(R.string.diffview_opt_mode_unified),
                themeTitle = context.getString(R.string.diffview_opt_theme),
                themeAuto = context.getString(R.string.diffview_opt_theme_auto),
                themeLight = context.getString(R.string.diffview_opt_theme_light),
                themeDark = context.getString(R.string.diffview_opt_theme_dark),
                textSizeTitle = context.getString(R.string.diffview_opt_text_size),
                foldingTitle = context.getString(R.string.diffview_opt_folding),
                foldingOn = context.getString(R.string.diffview_opt_folding_on),
                foldingOff = context.getString(R.string.diffview_opt_folding_off),
                collapseAll = context.getString(R.string.diffview_opt_collapse_all),
                expandAll = context.getString(R.string.diffview_expand_all),
                whitespaceTitle = context.getString(R.string.diffview_opt_whitespace),
                whitespaceNone = context.getString(R.string.diffview_opt_ws_none),
                whitespaceTrim = context.getString(R.string.diffview_opt_ws_trim),
                whitespaceCollapse = context.getString(R.string.diffview_opt_ws_collapse),
                whitespaceIgnoreAll = context.getString(R.string.diffview_opt_ws_ignore_all),
                granularityTitle = context.getString(R.string.diffview_opt_granularity),
                granularityWord = context.getString(R.string.diffview_opt_granularity_word),
                granularityChar = context.getString(R.string.diffview_opt_granularity_char),
                lineWrapTitle = context.getString(R.string.diffview_opt_line_wrap),
                lineWrapOn = context.getString(R.string.diffview_opt_line_wrap_on),
                lineWrapOff = context.getString(R.string.diffview_opt_line_wrap_off),
                symbolsTitle = context.getString(R.string.diffview_opt_symbols),
                symbolsOn = context.getString(R.string.diffview_opt_symbols_on),
                symbolsOff = context.getString(R.string.diffview_opt_symbols_off),
                longTabActionTitle = context.getString(R.string.diffview_opt_long_tab),
                longTabNone = context.getString(R.string.diffview_opt_long_tab_none),
                longTabTextSelectable = context.getString(R.string.diffview_opt_long_tab_selectable),
                longTabComment = context.getString(R.string.diffview_opt_long_tab_comment),
                syntaxTitle = context.getString(R.string.diffview_opt_syntax),
                syntaxKotlin = context.getString(R.string.diffview_opt_syntax_kotlin),
                syntaxPlain = context.getString(R.string.diffview_opt_syntax_plain),
                closeButton = context.getString(R.string.diffview_opt_close),
                menuSaveVisibleImage = context.getString(R.string.diffview_more_save_visible),
                menuSaveFullImage = context.getString(R.string.diffview_more_save_full),
                imageSavedTitle = context.getString(R.string.diffview_image_saved_title),
                imageSavedMessage = context.getString(R.string.diffview_image_saved_msg),
                imageSaveFailed = context.getString(R.string.diffview_image_save_failed),
                actionView = context.getString(R.string.diffview_action_view),
                actionShare = context.getString(R.string.diffview_action_share),
                expandAllSuccess = context.getString(R.string.diffview_expand_all_success),
                collapseAllSuccess = context.getString(R.string.diffview_collapse_all_success),
                menuCopyGitPatch = context.getString(R.string.diffview_more_copy_git_patch),
                copyGitPatchSuccess = context.getString(R.string.diffview_msg_copy_git_patch_success)
            )
        }
    }
}
