package com.mdiwebma.diffview.comment

import android.content.Context
import com.mdiwebma.diffview.R

/**
 * 코드 코멘트 입력 및 편집 다이얼로그의 텍스트 라벨 설정.
 */
data class DiffCommentLabels(
    val addTitle: String = "Add Comment",
    val editTitle: String = "Edit Comment",
    val deleteTitle: String = "Delete Comment",
    val deleteConfirm: String = "Are you sure you want to delete this comment?",
    val hint: String = "Leave a comment...",
    val menuEdit: String = "Edit",
    val menuDelete: String = "Delete",
    val actionSave: String = "Save",
    val actionCancel: String = "Cancel",
    val actionDelete: String = "Delete"
) {
    companion object {
        val Default = DiffCommentLabels()

        /**
         * Android Context의 strings.xml 리소스에서 [DiffCommentLabels]를 로드합니다.
         */
        fun fromContext(context: Context): DiffCommentLabels {
            return DiffCommentLabels(
                addTitle = context.getString(R.string.diffview_comment_add_title),
                editTitle = context.getString(R.string.diffview_comment_edit_title),
                deleteTitle = context.getString(R.string.diffview_comment_delete_title),
                deleteConfirm = context.getString(R.string.diffview_comment_delete_confirm),
                hint = context.getString(R.string.diffview_comment_hint),
                menuEdit = context.getString(R.string.diffview_comment_menu_edit),
                menuDelete = context.getString(R.string.diffview_comment_menu_delete),
                actionSave = context.getString(R.string.diffview_comment_action_save),
                actionCancel = context.getString(R.string.diffview_comment_action_cancel),
                actionDelete = context.getString(R.string.diffview_comment_action_delete)
            )
        }
    }
}
