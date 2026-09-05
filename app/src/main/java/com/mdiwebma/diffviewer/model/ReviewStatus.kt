package com.mdiwebma.diffviewer.model

import android.content.Context
import androidx.annotation.StringRes
import com.mdiwebma.diffviewer.R

enum class ReviewStatus(
    val dbValue: Int,
    val emoji: String,
    @StringRes val labelRes: Int
) {
    OPEN(0, "🟢", R.string.review_status_open),
    REVIEWING(1, "👀", R.string.review_status_reviewing),
    PENDING(2, "⏳", R.string.review_status_pending),
    COMMENTED(3, "💬", R.string.review_status_commented),
    APPROVED(4, "✅", R.string.review_status_approved),
    CLOSED(5, "❌", R.string.review_status_closed),
    MERGED(6, "🟣", R.string.review_status_merged);

    fun getDisplayName(context: Context): String {
        return "$emoji  ${context.getString(labelRes)}"
    }

    companion object {
        fun fromDbValue(value: Int): ReviewStatus {
            return entries.find { it.dbValue == value } ?: OPEN
        }
    }
}
