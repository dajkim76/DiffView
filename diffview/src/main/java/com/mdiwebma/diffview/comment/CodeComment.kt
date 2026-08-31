package com.mdiwebma.diffview.comment

import com.google.gson.annotations.SerializedName

/**
 * Single line comment data model.
 */
data class CodeComment(
    @SerializedName("text")
    var text: String,
    @SerializedName("updatedAt")
    var updatedAt: Long = System.currentTimeMillis()
)

/**
 * Payload data structure for JSON persistence.
 */
data class FileCommentsPayload(
    @SerializedName("commitHash")
    val commitHash: String,
    @SerializedName("filePath")
    val filePath: String,
    @SerializedName("comments")
    val comments: MutableMap<String, CodeComment> = mutableMapOf()
)
