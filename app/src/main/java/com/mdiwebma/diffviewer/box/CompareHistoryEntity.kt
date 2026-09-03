package com.mdiwebma.diffviewer.box

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

/**
 * Entity to store and manage git commit compare history in the Demo App.
 */
@Entity
data class CompareHistoryEntity(
    @Id var id: Long = 0,
    @Index var commitUrl: String = "",
    var owner: String = "",
    var repo: String = "",
    @Index var commitSha: String = "",
    var parentSha: String? = null,
    var commitMessage: String = "",
    var author: String = "",
    var selectedFileName: String? = null,
    var isFavorite: Boolean = false,
    var createdAt: Long = System.currentTimeMillis(),
    @Index var updatedAt: Long = System.currentTimeMillis()
)
