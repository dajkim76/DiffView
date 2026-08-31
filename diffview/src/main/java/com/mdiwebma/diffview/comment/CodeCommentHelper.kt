package com.mdiwebma.diffview.comment

import android.content.Context
import android.text.InputType
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.mdiwebma.diffview.R
import android.graphics.drawable.GradientDrawable
import com.mdiwebma.diffview.DiffColors
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.util.TypedValue
import java.util.Date

/**
 * UI helper for handling long-click comment interaction, dialogs, and rendering with GitHub-style card views.
 */
object CodeCommentHelper {

    private val DEFAULT_EMOJIS = listOf("👍", "🚀", "❤️", "💡", "⚠️", "❓", "🎉", "🔥")

    /**
     * Shows the comment input/action flow when a code line is long clicked.
     */
    fun handleLineLongClick(
        context: Context,
        lineKey: LineKey,
        currentComment: CodeComment?,
        onSave: (String) -> Unit,
        onDelete: () -> Unit
    ) {
        if (currentComment == null) {
            showAddCommentDialog(context, lineKey, onSave)
        } else {
            showEditCommentDialog(context, currentComment.text, onSave, onDelete)
        }
    }

    /**
     * Shows a dialog to add a new comment.
     */
    fun showAddCommentDialog(
        context: Context,
        lineKey: LineKey,
        onSave: (String) -> Unit
    ) {
        showCommentInputDialog(
            context = context,
            titleRes = R.string.comment_add_title,
            initialText = "",
            onSave = onSave,
            onDelete = null
        )
    }

    /**
     * Shows a dialog to edit an existing comment with a Neutral Delete button.
     */
    fun showEditCommentDialog(
        context: Context,
        currentText: String,
        onSave: (String) -> Unit,
        onDelete: (() -> Unit)? = null
    ) {
        showCommentInputDialog(
            context = context,
            titleRes = R.string.comment_edit_title,
            initialText = currentText,
            onSave = onSave,
            onDelete = onDelete
        )
    }

    private fun showCommentInputDialog(
        context: Context,
        titleRes: Int,
        initialText: String = "",
        hintRes: Int = R.string.comment_hint,
        onSave: (String) -> Unit,
        onDelete: (() -> Unit)? = null
    ) {
        val density = context.resources.displayMetrics.density
        val padHorizontalPx = (20 * density).toInt()
        val padVerticalPx = (12 * density).toInt()
        val pad4Px = (4 * density).toInt()
        val pad8Px = (8 * density).toInt()

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padHorizontalPx, padVerticalPx, padHorizontalPx, (4 * density).toInt())
        }

        val editText = EditText(context).apply {
            if (initialText.isNotEmpty()) {
                setText(initialText)
                setSelection(initialText.length)
            }
            hint = context.getString(hintRes)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            isVerticalScrollBarEnabled = true
        }

        val emojiScrollView = HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = pad4Px
                bottomMargin = pad4Px
            }
            isHorizontalScrollBarEnabled = false
        }

        val emojiLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        DEFAULT_EMOJIS.forEach { emoji ->
            val emojiButton = TextView(context).apply {
                text = emoji
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setPadding(pad8Px, pad4Px, pad8Px, pad4Px)
                val outValue = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
                if (outValue.resourceId != 0) {
                    setBackgroundResource(outValue.resourceId)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val start = editText.selectionStart.coerceAtLeast(0)
                    val end = editText.selectionEnd.coerceAtLeast(0)
                    val minPos = minOf(start, end)
                    val maxPos = maxOf(start, end)
                    editText.text?.replace(minPos, maxPos, emoji)
                    val newCursor = minPos + emoji.length
                    editText.setSelection(newCursor.coerceIn(0, editText.text?.length ?: 0))
                }
            }
            emojiLayout.addView(emojiButton)
        }
        emojiScrollView.addView(emojiLayout)

        rootLayout.addView(
            editText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        rootLayout.addView(emojiScrollView)

        val builder = AlertDialog.Builder(context)
            .setTitle(titleRes)
            .setView(rootLayout)
            .setPositiveButton(R.string.comment_action_save) { _, _ ->
                val text = editText.text.toString().trim()
                if (text.isNotEmpty()) {
                    onSave(text)
                }
            }
            .setNegativeButton(R.string.comment_action_cancel, null)

        if (onDelete != null) {
            builder.setNeutralButton(R.string.comment_action_delete) { _, _ ->
                showDeleteConfirmDialog(context, onDelete)
            }
        }

        val dialog = builder.create()
        if (onDelete != null) {
            dialog.setOnShowListener {
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)?.setTextColor(android.graphics.Color.parseColor("#E53935"))
            }
        }
        dialog.show()
    }

    /**
     * Shows a confirmation dialog before deleting a comment.
     */
    fun showDeleteConfirmDialog(
        context: Context,
        onDelete: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(R.string.comment_delete_title)
            .setMessage(R.string.comment_delete_confirm)
            .setPositiveButton(R.string.comment_action_delete) { _, _ ->
                onDelete()
            }
            .setNegativeButton(R.string.comment_action_cancel, null)
            .show()
    }

    /**
     * Binds a comment to the GitHub-style comment view container.
     */
    fun bindCommentView(
        commentView: View,
        tvContent: TextView,
        tvTime: TextView?,
        comment: CodeComment?,
        colors: DiffColors? = null,
        onCommentClick: (() -> Unit)? = null,
        onCommentLongClick: (() -> Unit)? = null
    ) {
        if (comment == null) {
            commentView.visibility = View.GONE
            return
        }

        commentView.visibility = View.VISIBLE
        tvContent.text = comment.text

        colors?.let { c ->
            val bg = GradientDrawable().apply {
                cornerRadius = commentView.context.resources.displayMetrics.density * 6
                setColor(c.commentBackground)
                setStroke((1 * commentView.context.resources.displayMetrics.density).toInt().coerceAtLeast(1), c.commentStroke)
            }
            commentView.background = bg
            tvContent.setTextColor(c.commentTextColor)
            tvTime?.setTextColor(c.commentTimeColor)
        }

        tvTime?.let {
            val dateFormatted = DateFormat.format("yyyy-MM-dd HH:mm", Date(comment.updatedAt))
            it.text = dateFormatted
        }

        if (onCommentClick != null) {
            commentView.setOnClickListener { onCommentClick() }
        } else {
            commentView.setOnClickListener(null)
        }

        if (onCommentLongClick != null) {
            commentView.setOnLongClickListener {
                onCommentLongClick()
                true
            }
        } else {
            commentView.setOnLongClickListener(null)
        }
    }
}
