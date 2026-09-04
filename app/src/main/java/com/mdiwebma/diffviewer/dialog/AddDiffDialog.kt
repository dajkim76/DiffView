package com.mdiwebma.diffviewer.dialog

import android.content.Context
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.mdiwebma.diffviewer.R
import com.mdiwebma.diffviewer.databinding.DialogAddDiffBinding

class AddDiffDialog(
    private val context: Context,
    private val onPickTwoFiles: (binding: DialogAddDiffBinding) -> Unit,
    private val onPickBefore: (binding: DialogAddDiffBinding) -> Unit,
    private val onPickAfter: (binding: DialogAddDiffBinding) -> Unit,
    private val onSave: (title: String, before: String, after: String) -> Unit,
    private val onDismiss: () -> Unit = {}
) {

    private var binding: DialogAddDiffBinding? = null
    private var dialog: AlertDialog? = null

    fun show() {
        val dialogBinding = DialogAddDiffBinding.inflate(LayoutInflater.from(context))
        binding = dialogBinding

        dialogBinding.btnPickTwoFilesDiff.setOnClickListener {
            onPickTwoFiles(dialogBinding)
        }
        dialogBinding.btnPickBeforeDiff.setOnClickListener {
            onPickBefore(dialogBinding)
        }
        dialogBinding.btnPickAfterDiff.setOnClickListener {
            onPickAfter(dialogBinding)
        }

        val alertDialog = AlertDialog.Builder(context)
            .setTitle(R.string.title_add_diff)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.btn_save, null)
            .setNegativeButton(R.string.btn_cancel, null)
            .setOnDismissListener {
                binding = null
                onDismiss()
            }
            .create()

        alertDialog.setOnShowListener {
            val saveBtn = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveBtn.setOnClickListener {
                val title = dialogBinding.etDiffTitle.text.toString().trim()
                val before = dialogBinding.etDiffBefore.text.toString()
                val after = dialogBinding.etDiffAfter.text.toString()

                if (before.isEmpty() && after.isEmpty()) {
                    Toast.makeText(context, R.string.msg_input_required, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                onSave(title, before, after)
                alertDialog.dismiss()
            }
        }

        dialog = alertDialog
        alertDialog.show()
    }
}
