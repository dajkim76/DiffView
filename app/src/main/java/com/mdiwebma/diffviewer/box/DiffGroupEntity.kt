package com.mdiwebma.diffviewer.box

import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToMany

/**
 * Entity to store and manage git commit compare history in the Demo App.
 */
@Entity
data class DiffGroupEntity(
    @Id var id: Long = 0,
    var type: Int,              //0: Text, 1:Git commit URL, 2: Git Patch,
    var title: String,
    var commitUrl: String? = null,
    var data: String? = null,   // git commit url's json or  git patch
    var diffCount: Int = 0,     // diff의 갯수
    var diffId: Long = 0L,      //미자막 보던 diffId
    var favoriteTime: Long = 0L,
    var createdTime: Long = System.currentTimeMillis(),
    var updatedTime: Long = System.currentTimeMillis(),
) {
    @Backlink(to = "history")
    lateinit var diffs: ToMany<DiffEntity>

    companion object {
        const val TYPE_TEXT = 0
        const val TYPE_COMMIT_URL = 1
        const val TYPE_GIT_PATCH = 2
    }
}
