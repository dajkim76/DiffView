package com.mdiwebma.diffviewer.dialog

import android.content.Context
import android.widget.EditText
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.mdiwebma.diffviewer.R

object RenameDialog {

    fun show(
        context: Context,
        @StringRes titleRes: Int,
        initialText: String,
        onConfirm: (newName: String) -> Unit
    ) {
        val editText = EditText(context).apply {
            setText(initialText)
            setSelection(initialText.length)
        }
        AlertDialog.Builder(context)
            .setTitle(titleRes)
            .setView(editText)
            .setPositiveButton(R.string.btn_save) { dialog, _ ->
                val newTitle = editText.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    onConfirm(newTitle)
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }
}
