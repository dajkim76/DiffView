package com.mdiwebma.diffviewer.dialog

import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.mdiwebma.diffviewer.R

object ConfirmDeleteDialog {

    fun show(
        context: Context,
        @StringRes messageRes: Int,
        onConfirm: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(R.string.menu_delete)
            .setMessage(messageRes)
            .setPositiveButton(R.string.btn_delete) { dialog, _ ->
                onConfirm()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }
}
