package com.mdiwebma.diffviewer.dialog

import android.content.Context
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
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
        val density = context.resources.displayMetrics.density
        val padding = (10f * density).toInt()
        val linearLayout = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(padding, padding, padding, padding)
        }
        val editText = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setText(initialText)
            setSelection(initialText.length)
            setPadding(padding, padding / 2, padding, padding / 2)
            setBackgroundResource(R.drawable.bg_edit_box)
            linearLayout.addView(this)
        }
        AlertDialog.Builder(context)
            .setTitle(titleRes)
            .setView(linearLayout)
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
