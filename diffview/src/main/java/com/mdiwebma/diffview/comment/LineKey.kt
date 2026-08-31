package com.mdiwebma.diffview.comment

import com.google.gson.annotations.SerializedName

/**
 * Line identification key based on left/right diff line numbers.
 */
data class LineKey(
    @SerializedName("leftLine")
    val leftLine: Int? = null,
    @SerializedName("rightLine")
    val rightLine: Int? = null
) {
    fun toKeyString(): String = "${leftLine ?: -1}_${rightLine ?: -1}"

    companion object {
        fun fromKeyString(keyStr: String): LineKey {
            val parts = keyStr.split("_")
            val left = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it >= 0 }
            val right = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it >= 0 }
            return LineKey(left, right)
        }
    }
}
