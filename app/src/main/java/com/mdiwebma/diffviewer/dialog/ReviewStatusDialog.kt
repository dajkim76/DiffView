package com.mdiwebma.diffviewer.dialog

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.mdiwebma.diffviewer.R
import com.mdiwebma.diffviewer.model.ReviewStatus

object ReviewStatusDialog {
    fun show(
        context: Context,
        currentStatus: ReviewStatus,
        onStatusSelected: (ReviewStatus) -> Unit
    ) {
        val statuses = ReviewStatus.entries.toTypedArray()
        val items = statuses.map { it.getDisplayName(context) }.toTypedArray()
        val currentIndex = statuses.indexOf(currentStatus).takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(context)
            .setTitle(R.string.review_status_dialog_title)
            .setSingleChoiceItems(items, currentIndex) { dialog, which ->
                val selected = statuses[which]
                onStatusSelected(selected)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }
}
